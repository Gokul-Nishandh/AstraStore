/**
 * Revoke-key command permanently deletes an API key by its ID.
 * If no keyId is provided, presents an interactive picker of active keys.
 * Calls DELETE /api/auth/keys/{keyId} endpoint.
 * Requires confirmation unless --yes flag is provided.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import com.astrastore.cli.ui.ConsolePrompter;
import com.astrastore.cli.ui.ErrorParser;
import com.fasterxml.jackson.core.type.TypeReference;
import picocli.CommandLine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "revoke-key",
        mixinStandardHelpOptions = true,
        description = "Revoke an API key."
)
public class AuthRevokeKeyCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..1", description = "ID of the API key to revoke (omit for picker)")
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

        AstraConfig config = AstraConfig.load();
        AstraHttpClient client = new AstraHttpClient(config.getAuthUrl());

        if (keyId == null) {
            if (!ConsolePrompter.isInteractive()) {
                System.err.println("Error: --keyId required (or provide key ID as argument).");
                return 1;
            }
            try {
                List<Map<String, Object>> keys = client.get("/api/auth/keys",
                        new TypeReference<List<Map<String, Object>>>() {});
                if (keys == null || keys.isEmpty()) {
                    System.err.println("No active API keys to revoke.");
                    return 1;
                }
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(ZoneId.systemDefault());
                List<String> labels = keys.stream()
                        .map(k -> String.format("ID %d  %-30s  %s  %s",
                                k.get("id"),
                                k.get("name"),
                                k.get("keyPrefix"),
                                k.get("createdAt") != null
                                        ? fmt.format(Instant.parse((String) k.get("createdAt")))
                                        : "n/a"))
                        .collect(Collectors.toList());
                int idx = ConsolePrompter.selectSingle("Select an API key to revoke (Use arrow keys):", labels);
                if (idx < 0) {
                    System.out.println("Cancelled.");
                    return 0;
                }
                keyId = ((Number) keys.get(idx).get("id")).longValue();
            } catch (Exception e) {
                ErrorHandler.printError(e);
                return 1;
            }
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
            client.delete("/api/auth/keys/" + keyId);
            System.out.println("✓ API key " + keyId + " revoked successfully.");
            return 0;
        } catch (Exception e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }
}
