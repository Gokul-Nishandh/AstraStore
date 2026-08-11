/**
 * List-keys command displays all active API keys for the authenticated user.
 * Calls /api/auth/keys endpoint and renders results in a formatted table.
 * Shows key prefix (never full key), name, expiry, and last-used timestamp.
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
        name = "list-keys",
        aliases = {"keys"},
        mixinStandardHelpOptions = true,
        description = "List your active API keys."
)
public class AuthListKeysCommand implements Callable<Integer> {

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
            AstraHttpClient client = new AstraHttpClient(config.getAuthUrl());
            List<KeyResponse> keys = client.get("/api/auth/keys", new TypeReference<List<KeyResponse>>() {});

            if (keys == null || keys.isEmpty()) {
                if ("json".equalsIgnoreCase(output)) {
                    System.out.println("[]");
                } else {
                    System.out.println("No active API keys. Run 'astra auth create-key' to create one.");
                }
                return 0;
            }

            if ("json".equalsIgnoreCase(output)) {
                System.out.println(client.getMapper().writeValueAsString(keys));
                return 0;
            }

            System.out.println("Active API Keys");
            System.out.println("──────────────");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());

            for (KeyResponse key : keys) {
                System.out.println("  ID:          " + key.getId());
                System.out.println("  Name:        " + key.getName());
                System.out.println("  Prefix:      " + key.getKeyPrefix());
                System.out.println("  Created:     " + fmt.format(Instant.parse(key.getCreatedAt())));
                if (key.getLastUsedAt() != null) {
                    System.out.println("  Last used:   " + fmt.format(Instant.parse(key.getLastUsedAt())));
                } else {
                    System.out.println("  Last used:   never");
                }
                if (key.getExpiresAt() != null) {
                    Instant expiry = Instant.parse(key.getExpiresAt());
                    if (expiry.isBefore(Instant.now())) {
                        System.out.println("  Expires:     " + fmt.format(expiry) + " (EXPIRED)");
                    } else {
                        System.out.println("  Expires:     " + fmt.format(expiry));
                    }
                } else {
                    System.out.println("  Expires:     never");
                }
                System.out.println("  Revoke with: astra auth revoke-key " + key.getId());
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
    public static class KeyResponse {
        private Long id;
        private String name;
        private String keyPrefix;
        private String expiresAt;
        private String createdAt;
        private String lastUsedAt;
    }
}
