/**
 * Healing status command shows how many chunks are under-replicated.
 * Calls Replication service /api/v1/admin/metadata/status endpoint.
 * Displays total tracked chunks vs under-replicated count.
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

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "status",
        mixinStandardHelpOptions = true,
        description = "Show healing status (under-replicated chunks)."
)
public class HealingStatusCommand implements Callable<Integer> {

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
            HealingStatus status = client.get("/api/replication/api/v1/admin/metadata/status",
                    new TypeReference<HealingStatus>() {});

            System.out.println("Self-Healing Status");
            System.out.println("────────────────────");
            System.out.println("  Tracked chunks:       " + status.totalTrackedChunks);
            System.out.println("  Under-replicated:     " + status.underReplicatedChunks);

            if (status.underReplicatedChunks == 0) {
                System.out.println();
                System.out.println("  ✓ All chunks have target replicas.");
            } else {
                System.out.println();
                System.out.println("  ⚠ " + status.underReplicatedChunks + " chunk(s) need healing.");
                System.out.println("    Run 'astra cluster healing run' to trigger repair.");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to fetch healing status: " + e.getMessage());
            return 1;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HealingStatus {
        private int totalTrackedChunks;
        private int underReplicatedChunks;
    }
}
