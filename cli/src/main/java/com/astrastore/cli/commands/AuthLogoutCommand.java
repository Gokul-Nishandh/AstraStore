/**
 * Logout command revokes the current refresh token and clears local credentials.
 * Calls /api/auth/logout to invalidate server-side token before local cleanup.
 * Always clears local credentials even if server call fails (best-effort cleanup).
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
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "logout",
        mixinStandardHelpOptions = true,
        description = "Revoke refresh token and clear local credentials."
)
@Slf4j
public class AuthLogoutCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        CredentialStore store = CredentialStore.getInstance();
        CredentialStore.Credentials creds;
        try {
            creds = store.load();
        } catch (Exception e) {
            log.warn("Failed to load credentials: {}", e.getMessage());
            creds = null;
        }

        if (creds == null) {
            System.out.println("Not currently logged in.");
            return 0;
        }

        if (creds.getRefreshToken() != null && !creds.getRefreshToken().isBlank()) {
            try {
                AstraConfig config = AstraConfig.load();
                AstraHttpClient client = new AstraHttpClient(config.getAuthUrl());
                LogoutRequest request = new LogoutRequest(creds.getRefreshToken());
                client.post("/api/auth/logout", request, new TypeReference<Map<String, Object>>() {});
                System.out.println("✓ Server-side session revoked.");
            } catch (Exception e) {
                log.warn("Server-side logout failed (will still clear local credentials): {}", e.getMessage());
            }
        }

        try {
            store.clear();
            System.out.println("✓ Local credentials cleared.");
        } catch (Exception e) {
            System.err.println("Error clearing local credentials: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LogoutRequest {
        private String refreshToken;
    }
}
