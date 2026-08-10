/**
 * Download command streams an object from AstraStore to a local file.
 * Calls /api/v1/objects/{objectId} endpoint or /api/v1/buckets/{bucketId}/objects/{*key}.
 * Uses OkHttp streaming for efficient large file downloads.
 * Shows progress bar with bytes transferred.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
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

    @CommandLine.Parameters(index = "0", description = "Object ID (global hash) or bucket/key path")
    private String objectId;

    @CommandLine.Option(names = {"-b", "--bucket"}, description = "Bucket name (for key-based download)")
    private String bucket;

    @CommandLine.Option(names = {"-k", "--key"}, description = "Object key (for key-based download)")
    private String key;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file (defaults to objectId or filename)")
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
        String downloadUrl = buildDownloadUrl(config.getGatewayUrl());

        File outFile = (output != null) ? output : deriveOutputFilename();
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
                System.err.println("Download failed: HTTP " + response.code() + " " + body);
                return 1;
            }

            long contentLength = response.body().contentLength();
            Source source = response.body().source();
            Sink fileSink = Okio.sink(outFile);
            BufferedSink sink = Okio.buffer(fileSink);

            if (!noProgress && System.console() != null && contentLength > 0) {
                ProgressTracker tracker = new ProgressTracker(contentLength);
                Source counted = new ForwardingSource(source) {
                    @Override
                    public long read(okio.Buffer sinkBuf, long byteCount) throws IOException {
                        long read = super.read(sinkBuf, byteCount);
                        tracker.update(read);
                        return read;
                    }
                };
                sink.writeAll(counted);
            } else {
                sink.writeAll(source);
            }
            sink.close();

            System.out.println();
            System.out.println("✓ Downloaded " + formatSize(outFile.length()) + " to " + outFile.getAbsolutePath());
            return 0;
        } catch (IOException e) {
            System.err.println("Download failed: " + e.getMessage());
            return 1;
        }
    }

    private String buildDownloadUrl(String gatewayUrl) {
        if (bucket != null && key != null) {
            return gatewayUrl + "/api/v1/buckets/" + bucket + "/objects/" + key;
        }
        return gatewayUrl + "/api/v1/objects/" + objectId;
    }

    private File deriveOutputFilename() {
        if (bucket != null && key != null) {
            String safeName = key.replaceAll("[/\\\\]", "_");
            return new File(System.getProperty("user.dir"), safeName);
        }
        return new File(System.getProperty("user.dir"), objectId);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static class ProgressTracker {
        private final long total;
        private long downloaded = 0;
        private long lastPrint = 0;
        private static final long PRINT_INTERVAL = 524288L;

        ProgressTracker(long total) {
            this.total = total;
            System.out.print("\r  Progress: 0%");
        }

        void update(long bytesRead) {
            if (bytesRead > 0) {
                downloaded += bytesRead;
                if (downloaded - lastPrint >= PRINT_INTERVAL || downloaded == total) {
                    int percent = total > 0 ? (int) ((downloaded * 100) / total) : 0;
                    System.out.print("\r  Progress: " + percent + "%");
                    lastPrint = downloaded;
                }
            }
        }
    }
}
