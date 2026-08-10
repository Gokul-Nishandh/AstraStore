/**
 * Healing run command manually triggers self-healing scan immediately.
 * Calls Replication service /api/v1/admin/heal/run endpoint.
 * Bypasses the 60-second scheduled scan for immediate repair.
 * Shows how many chunks were healed.
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
        name = "run",
        mixinStandardHelpOptions = true,
        description = "Trigger self-healing scan immediately."
)
public class HealingRunCommand implements Callable<Integer> {

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

            System.out.println("Triggering self-healing scan...");
            HealResult result = client.post("/api/replication/api/v1/admin/heal/run", null,
                    new TypeReference<HealResult>() {});

            System.out.println("✓ Healing scan triggered.");
            System.out.println("  Under-replicated chunks found: " + result.underReplicatedChunksFound);
            System.out.println();
            System.out.println("Run 'astra cluster healing status' to check progress.");
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to trigger healing: " + e.getMessage());
            return 1;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HealResult {
        private String message;
        private int underReplicatedChunksFound;
    }
}
