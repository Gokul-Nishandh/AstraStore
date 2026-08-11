/**
 * Delete-object command (rm) removes an object from a bucket.
 * Calls DELETE /api/v1/buckets/{bucketId}/objects/{*key} endpoint.
 * Requires --yes flag to skip confirmation prompt for scripted use.
 * Returns success message with deleted object ID.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import picocli.CommandLine;

import java.util.UUID;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "rm",
        mixinStandardHelpOptions = true,
        description = "Delete an object from a bucket."
)
public class DeleteObjectCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Object ID (UUID) to delete")
    private String objectId;

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
            UUID.fromString(objectId);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: objectId must be a valid UUID");
            return 1;
        }

        if (!skipConfirm) {
            System.out.print("Delete object " + objectId + "? This cannot be undone. [y/N] ");
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
            client.delete("/api/v1/objects/" + objectId);
            System.out.println("✓ Object " + objectId + " deleted.");
            return 0;
        } catch (Exception e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }
}
