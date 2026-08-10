/**
 * Login command supporting username/password and API key authentication.
 * Prompts for credentials interactively if not provided via flags.
 * Stores tokens in encrypted credential store after successful auth.
 * Supports --api-key flag for programmatic authentication without password.
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

import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "login",
        mixinStandardHelpOptions = true,
        description = "Authenticate with username/password or API key."
)
public class AuthLoginCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-u", "--username"}, description = "Username")
    private String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "Password", interactive = true)
    private String password;

    @CommandLine.Option(names = {"--api-key"}, description = "Login using an API key instead of password")
    private String apiKey;

    @Override
    public Integer call() throws Exception {
        AstraConfig config = AstraConfig.load();
        String authUrl = config.getAuthUrl();

        CredentialStore.Credentials credentials;
        if (apiKey != null && !apiKey.isBlank()) {
            credentials = loginWithApiKey(authUrl, apiKey);
        } else {
            if (username == null || username.isBlank()) {
                System.err.println("Error: --username is required (or use --api-key)");
                return 1;
            }
            if (password == null || password.isBlank()) {
                System.err.println("Error: --password is required (or use --api-key)");
                return 1;
            }
            credentials = loginWithPassword(authUrl, username, password);
        }

        CredentialStore.getInstance().save(credentials);
        System.out.println("✓ Logged in as " + credentials.getUsername() + " (" + credentials.getEmail() + ")");
        return 0;
    }

    private CredentialStore.Credentials loginWithPassword(String authUrl, String username, String password) throws Exception {
        AstraHttpClient client = new AstraHttpClient(authUrl);
        String email = username.contains("@") ? username : username + "@local";
        LoginRequest request = new LoginRequest(username, password, email);
        try {
            LoginResponse response = client.post("/api/auth/login", request, new TypeReference<>() {});
            return new CredentialStore.Credentials(
                    response.getUsername(),
                    response.getEmail(),
                    response.getToken(),
                    response.getRefreshToken(),
                    null,
                    System.currentTimeMillis() + 24 * 3600 * 1000L
            );
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage(), e);
        }
    }

    private CredentialStore.Credentials loginWithApiKey(String authUrl, String apiKey) throws Exception {
        AstraHttpClient client = new AstraHttpClient(authUrl);
        Map<String, Object> user = client.get("/api/auth/audit?page=0&size=1",
                new TypeReference<Map<String, Object>>() {});
        String username = apiKey.startsWith("astra_sk_") ? "api-key-user" : "unknown";
        return new CredentialStore.Credentials(
                username, null, null, null, apiKey,
                System.currentTimeMillis() + 365 * 24 * 3600 * 1000L
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoginRequest {
        private String username;
        private String password;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoginResponse {
        private String token;
        private String type;
        private String refreshToken;
        private Long userId;
        private String username;
        private String email;
        private java.util.Set<String> roles;
    }
}
