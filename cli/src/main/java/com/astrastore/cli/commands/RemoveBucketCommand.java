/**
 * Remove-bucket command (rb) deletes a bucket by ID.
 * Calls DELETE /api/v1/buckets/{bucketId} endpoint.
 * Requires confirmation unless --yes flag is provided.
 * Cannot delete bucket that still contains objects (returns 409).
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import picocli.CommandLine;

import java.util.UUID;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "rb",
        mixinStandardHelpOptions = true,
        description = "Remove a bucket."
)
public class RemoveBucketCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Bucket ID (UUID) to remove")
    private String bucketId;

    @CommandLine.Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
    private boolean skipConfirm;

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
            UUID.fromString(bucketId);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: bucketId must be a valid UUID");
            return 1;
        }

        if (!skipConfirm) {
            System.out.print("Remove bucket " + bucketId + "? This cannot be undone. [y/N] ");
            String response = System.console() != null
                    ? System.console().readLine()
                    : new java.util.Scanner(System.in).nextLine();
            if (response == null || !response.trim().equalsIgnoreCase("y")) {
                System.out.println("Cancelled.");
                return 0;
            }
        }

        try {
            AstraConfig config = AstraConfig.load();
            AstraHttpClient client = new AstraHttpClient(config.getGatewayUrl());
            client.delete("/api/v1/buckets/" + bucketId);
            System.out.println("✓ Bucket " + bucketId + " removed.");
            return 0;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                System.err.println("Error: bucket is not empty. Delete all objects first.");
            } else {
                System.err.println("Failed to remove bucket: " + e.getMessage());
            }
            return 1;
        }
    }
}
