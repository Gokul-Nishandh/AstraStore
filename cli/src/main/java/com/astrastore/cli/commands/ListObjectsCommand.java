/**
 * List-objects command (ls) displays objects in a bucket.
 * Calls /api/v1/buckets/{bucketId}/objects endpoint with optional prefix filter.
 * Supports JSON output via --output flag for scripting.
 * Shows key, size, and creation timestamp in table format.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import picocli.CommandLine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "ls",
        mixinStandardHelpOptions = true,
        description = "List objects in a bucket."
)
public class ListObjectsCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Bucket ID (UUID)")
    private String bucketId;

    @CommandLine.Option(names = {"--prefix"}, description = "Filter by key prefix")
    private String prefix;

    @CommandLine.Option(names = {"--output"}, description = "Output format: table (default) or json")
    private String output = "table";

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

        try {
            AstraConfig config = AstraConfig.load();
            AstraHttpClient client = new AstraHttpClient(config.getGatewayUrl());
            String url = "/api/v1/buckets/" + bucketId + "/objects";
            if (prefix != null && !prefix.isBlank()) {
                url += "?prefix=" + java.net.URLEncoder.encode(prefix, java.nio.charset.StandardCharsets.UTF_8);
            }

            ObjectsPage page = client.get(url, new TypeReference<ObjectsPage>() {});

            if ("json".equalsIgnoreCase(output)) {
                System.out.println(client.getMapper().writeValueAsString(page.content));
                return 0;
            }

            if (page.content == null || page.content.isEmpty()) {
                System.out.println("No objects in bucket " + bucketId);
                return 0;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());

            System.out.println("Objects in bucket " + bucketId);
            System.out.println("─────────────────────");
            for (ObjectEntry obj : page.content) {
                System.out.println("  Key:        " + obj.key);
                System.out.println("  ID:         " + obj.id);
                System.out.println("  Size:       " + formatSize(obj.sizeBytes));
                System.out.println("  Status:     " + (obj.status != null ? obj.status : "(unknown)"));
                System.out.println("  Chunks:     " + obj.chunksReplicated + "/" + obj.chunksTotal + " replicated");
                if (obj.createdAt != null) {
                    System.out.println("  Created:    " + fmt.format(Instant.parse(obj.createdAt)));
                }
                System.out.println();
            }
            return 0;
        } catch (Exception e) {
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ObjectsPage {
        private List<ObjectEntry> content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ObjectEntry {
        private String id;
        private String key;
        private long sizeBytes;
        private String checksum;
        private String contentType;
        private String status;
        private String createdAt;
        private long chunksReplicated;
        private long chunksTotal;
    }
}
