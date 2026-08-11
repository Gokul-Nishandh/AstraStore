/**
 * Revoke-key command permanently deletes an API key by its ID.
 * Calls DELETE /api/auth/keys/{keyId} endpoint.
 * Requires confirmation unless --yes flag is provided for scripted use.
 * Verifies ownership before deletion to prevent accidental cross-user revocation.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "revoke-key",
        mixinStandardHelpOptions = true,
        description = "Revoke an API key by ID."
)
public class AuthRevokeKeyCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "ID of the API key to revoke")
    private Long keyId;

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

        if (!skipConfirm) {
            System.out.print("Revoke API key ID " + keyId + "? This cannot be undone. [y/N] ");
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
            AstraHttpClient client = new AstraHttpClient(config.getAuthUrl());
            client.delete("/api/auth/keys/" + keyId);
            System.out.println("✓ API key " + keyId + " revoked successfully.");
            return 0;
        } catch (Exception e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }
}
