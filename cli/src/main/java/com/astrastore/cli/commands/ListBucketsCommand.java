/**
 * List-buckets command displays all buckets in the cluster.
 * Calls GET /api/v1/buckets endpoint with optional owner filter.
 * Shows bucket name, ID, owner, and creation timestamp.
 * Supports JSON output for scripting.
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

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "ls-buckets",
        mixinStandardHelpOptions = true,
        description = "List all buckets."
)
public class ListBucketsCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--owner"}, description = "Filter by owner UUID")
    private String ownerId;

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
            String path = "/api/v1/buckets";
            if (ownerId != null && !ownerId.isBlank()) {
                path += "?ownerId=" + java.net.URLEncoder.encode(ownerId, java.nio.charset.StandardCharsets.UTF_8);
            }

            BucketsPage page = client.get(path, new TypeReference<>() {});

            if ("json".equalsIgnoreCase(output)) {
                System.out.println(client.getMapper().writeValueAsString(page.content));
                return 0;
            }

            if (page.content == null || page.content.isEmpty()) {
                System.out.println("No buckets found.");
                return 0;
            }

            System.out.println("Buckets (" + page.totalElements + " total)");
            System.out.println("─────────────────────");
            for (BucketInfo bucket : page.content) {
                System.out.println("  • " + bucket.name);
                System.out.println("    ID:      " + bucket.id);
                System.out.println("    Owner:   " + (bucket.ownerId != null ? bucket.ownerId : "(unknown)"));
                if (bucket.createdAt != null) {
                    System.out.println("    Created: " + bucket.createdAt);
                }
                System.out.println();
            }
            return 0;
        } catch (Exception e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BucketsPage {
        private List<BucketInfo> content;
        private int totalElements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BucketInfo {
        private String id;
        private String name;
        private String ownerId;
        private String createdAt;
    }
}
