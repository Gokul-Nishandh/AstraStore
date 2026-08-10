/**
 * Create-key command generates a new API key for the authenticated user.
 * Calls /api/auth/keys endpoint with optional name and expiry.
 * Displays the raw key ONCE with a warning to save it immediately.
 * Requires existing login session (uses stored credentials for auth).
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
import java.time.format.DateTimeParseException;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "create-key",
        mixinStandardHelpOptions = true,
        description = "Create a new API key."
)
public class AuthCreateKeyCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-n", "--name"}, description = "Friendly name for the key", required = true)
    private String name;

    @CommandLine.Option(names = {"-e", "--expires-at"},
            description = "Expiry timestamp (ISO-8601, e.g. 2027-12-31T23:59:59Z). Max 1 year.")
    private String expiresAt;

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

        CreateKeyRequest request = new CreateKeyRequest();
        request.setName(name);
        if (expiresAt != null) {
            try {
                request.setExpiresAt(Instant.parse(expiresAt).toString());
            } catch (DateTimeParseException e) {
                System.err.println("Invalid expiry format. Use ISO-8601 (e.g. 2027-12-31T23:59:59Z)");
                return 1;
            }
        }

        try {
            AstraConfig config = AstraConfig.load();
            AstraHttpClient client = new AstraHttpClient(config.getAuthUrl());
            CreatedKeyResponse response = client.post("/api/auth/keys", request, new TypeReference<>() {});

            System.out.println("✓ API key created (ID: " + response.getId() + ")");
            System.out.println();
            System.out.println("⚠️  SAVE THIS KEY NOW — IT WILL NOT BE SHOWN AGAIN:");
            System.out.println("    " + response.getKey());
            System.out.println();
            System.out.println("  Name:       " + response.getName());
            System.out.println("  Prefix:      " + response.getKeyPrefix());
            System.out.println("  Created:     " + response.getCreatedAt());
            if (response.getExpiresAt() != null) {
                System.out.println("  Expires:     " + response.getExpiresAt());
            } else {
                System.out.println("  Expires:     never");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to create API key: " + e.getMessage());
            return 1;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateKeyRequest {
        private String name;
        private String expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatedKeyResponse {
        private Long id;
        private String name;
        private String key;
        private String keyPrefix;
        private String expiresAt;
        private String createdAt;
    }
}
