/**
 * Download command streams an object from AstraStore to a local file.
 * Supports path-based references (bucket-name/key, s3://name/key, or UUID).
 * If only bucket+key is given (no UUID), resolves via ResourceResolver.
 * Calls /api/v1/objects/{id} for UUIDs, or /api/v1/buckets/{uuid}/objects/{key} for paths.
 * Uses OkHttp streaming for efficient large file downloads.
 * Shows progress bar with bytes transferred.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.exception.ApiException;
import com.astrastore.cli.http.AstraHttpClient;
import com.astrastore.cli.ui.ColorSupport;
import com.astrastore.cli.ui.ErrorParser;
import com.astrastore.cli.ui.ResourceResolver;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.ForwardingSource;
import okio.Okio;
import okio.Sink;
import okio.Source;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "download",
        mixinStandardHelpOptions = true,
        description = "Download an object from AstraStore."
)
public class DownloadCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..1",
            description = "Object ID, bucket-name/key, or s3://name/key (omit for picker)")
    private String objectRef;

    @CommandLine.Option(names = {"-b", "--bucket"},
            description = "Bucket name or UUID (alternative to positional arg)")
    private String bucket;

    @CommandLine.Option(names = {"-k", "--key"}, description = "Object key (with --bucket)")
    private String key;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file")
    private File output;

    @CommandLine.Option(names = {"--no-progress"}, description = "Disable progress bar")
    private boolean noProgress;

    @Override
    public Integer call() {
        CredentialStore.Credentials creds;
        try {
            creds = CredentialStore.getInstance().load();
        } catch (Exception e) {
            System.err.println("Error loading credentials: " + e.getMessage());
            return 1;
        }
        if (creds == null || creds.getAccessToken() == null) {
            System.err.println("Not logged in. Run 'astra auth login' first.");
            return 1;
        }

        AstraConfig config = AstraConfig.load();
        AstraHttpClient client = new AstraHttpClient(config.getGatewayUrl());

        String downloadUrl;
        String objectIdForFilename = null;

        if (bucket != null && key != null) {
            ResourceResolver.ResolvedBucket resolvedBucket = ResourceResolver.resolveBucket(bucket, client);
            if (resolvedBucket == null) {
                System.err.println(ErrorParser.friendlyMessage(
                        new ApiException(404, "/api/v1/buckets/" + bucket,
                                "{\"code\":\"NOT_FOUND\",\"message\":\"Bucket not found: " + bucket + "\"}")));
                return 1;
            }
            downloadUrl = config.getGatewayUrl() + "/api/v1/buckets/" + resolvedBucket.uuid() + "/objects/" + key;
            objectIdForFilename = key;
        } else if (objectRef != null && !objectRef.isBlank()) {
            if (objectRef.startsWith("s3://") || objectRef.contains("/")) {
                ResourceResolver.ResolvedObject resolved = ResourceResolver.resolveObject(objectRef, client);
                if (resolved == null) {
                    System.err.println(ErrorParser.friendlyMessage(
                            new ApiException(404, objectRef,
                                    "{\"code\":\"NOT_FOUND\",\"message\":\"Object not found: " + objectRef + "\"}")));
                    return 1;
                }
                downloadUrl = config.getGatewayUrl() + "/api/v1/buckets/" + resolved.bucketUuid() + "/objects/" + resolved.key();
                objectIdForFilename = resolved.key();
            } else {
                downloadUrl = config.getGatewayUrl() + "/api/v1/objects/" + objectRef;
                objectIdForFilename = objectRef;
            }
        } else {
            System.err.println(ColorSupport.error("Object reference required (UUID, bucket-name/key, or s3://name/key)."));
            System.err.println(ColorSupport.info("Run 'astra ls <bucket-name>' to see available objects."));
            return 1;
        }

        File outFile = (output != null) ? output : deriveOutputFilename(objectIdForFilename);
        System.out.println("Downloading to " + outFile.getAbsolutePath());

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer " + creds.getAccessToken())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                ErrorHandler.printError(new ApiException(response.code(), request.url().encodedPath(), body));
                return 1;
            }

            long contentLength = response.body().contentLength();
            Source source = response.body().source();
            Sink fileSink = Okio.sink(outFile);
            BufferedSink sink = Okio.buffer(fileSink);

            if (!noProgress && System.console() != null && contentLength > 0) {
                com.astrastore.cli.ui.ProgressRenderer renderer =
                        new com.astrastore.cli.ui.ProgressRenderer(outFile.getName(), contentLength);
                Source counted = new ForwardingSource(source) {
                    @Override
                    public long read(okio.Buffer sinkBuf, long byteCount) throws IOException {
                        long read = super.read(sinkBuf, byteCount);
                        renderer.update(read);
                        return read;
                    }
                };
                sink.writeAll(counted);
                renderer.finish();
            } else {
                sink.writeAll(source);
            }
            sink.close();

            System.out.println();
            System.out.println("✓ Downloaded " + formatSize(outFile.length()) + " to " + outFile.getAbsolutePath());
            return 0;
        } catch (IOException e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }

    private File deriveOutputFilename(String key) {
        if (key != null && !key.isEmpty()) {
            String safeName = key.replaceAll("[/\\\\]", "_");
            return new File(System.getProperty("user.dir"), safeName);
        }
        return new File(System.getProperty("user.dir"), "download.bin");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
