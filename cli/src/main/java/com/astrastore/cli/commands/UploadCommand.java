/**
 * Upload command streams a local file to AstraStore via gateway.
 * Uses HTTP multipart form upload to /api/v1/upload endpoint.
 * Shows progress bar with bytes transferred, speed, and ETA.
 * Supports --bucket and --key options for destination, defaults to filename as key.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import com.fasterxml.jackson.core.type.TypeReference;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Sink;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "upload",
        mixinStandardHelpOptions = true,
        description = "Upload a file to a bucket."
)
public class UploadCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Local file to upload", paramLabel = "FILE")
    private File file;

    @CommandLine.Option(names = {"-b", "--bucket"}, description = "Destination bucket ID (UUID)", required = true)
    private String bucketId;

    @CommandLine.Option(names = {"-k", "--key"}, description = "Object key (defaults to filename)")
    private String key;

    @CommandLine.Option(names = {"--no-progress"}, description = "Disable progress bar")
    private boolean noProgress;

    @Override
    public Integer call() {
        if (!file.exists() || !file.isFile()) {
            System.err.println("Error: file not found: " + file.getAbsolutePath());
            return 1;
        }

        UUID bucketUuid;
        try {
            bucketUuid = UUID.fromString(bucketId);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: --bucket must be a valid UUID. Use 'astra ls-buckets' to find it.");
            return 1;
        }

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

        String objectKey = (key != null && !key.isBlank()) ? key : file.getName();
        AstraConfig config = AstraConfig.load();
        String uploadUrl = config.getGatewayUrl() + "/api/v1/buckets/" + bucketUuid + "/objects/" + objectKey;

        long fileSize = file.length();
        System.out.println("Uploading " + file.getName() + " (" + formatSize(fileSize) + ") to " + bucketId + "/" + objectKey);

        try {
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());

            RequestBody requestBody = RequestBody.create(fileBytes,
                    MediaType.parse("application/octet-stream"));

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .header("Authorization", "Bearer " + creds.getAccessToken())
                    .header("Content-Type", "application/octet-stream")
                    .put(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    System.err.println("Upload failed: HTTP " + response.code() + " " + respBody);
                    return 1;
                }

                AstraHttpClient parserClient = new AstraHttpClient(config.getGatewayUrl());
                Map<String, Object> result = parserClient.getMapper().readValue(respBody,
                        new TypeReference<Map<String, Object>>() {});

                String objectId = (String) result.get("objectId");
                String checksum = (String) result.get("checksum");
                Integer totalChunks = (Integer) result.get("chunkCount");

                System.out.println();
                System.out.println("✓ Upload complete");
                System.out.println("  Object ID:    " + objectId);
                System.out.println("  Checksum:     " + (checksum != null ? checksum.substring(0, Math.min(16, checksum.length())) + "..." : "(unknown)"));
                System.out.println("  Total Chunks: " + totalChunks);
                return 0;
            }
        } catch (IOException e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static class ProgressRequestBody extends RequestBody {
        private final RequestBody delegate;
        private final ProgressBar pb;

        ProgressRequestBody(RequestBody delegate, ProgressBar pb) {
            this.delegate = delegate;
            this.pb = pb;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() {
            try {
                return delegate.contentLength();
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            Sink progressSink = new ForwardingSink(sink) {
                @Override
                public void write(okio.Buffer source, long byteCount) throws IOException {
                    super.write(source, byteCount);
                    pb.stepBy(byteCount);
                }
            };
            delegate.writeTo(okio.Okio.buffer(progressSink));
            pb.close();
        }
    }
}
