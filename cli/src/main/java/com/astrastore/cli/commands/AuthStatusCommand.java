/**
 * Status command shows the current authenticated user and token expiry.
 * Loads credentials from the encrypted store and displays summary.
 * Does not make any API calls - pure local state inspection.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import picocli.CommandLine;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "status",
        mixinStandardHelpOptions = true,
        description = "Show current authentication status."
)
public class AuthStatusCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        CredentialStore store = CredentialStore.getInstance();
        CredentialStore.Credentials creds;
        try {
            creds = store.load();
        } catch (Exception e) {
            System.err.println("Error loading credentials: " + e.getMessage());
            return 1;
        }

        if (creds == null) {
            System.out.println("Not logged in. Run 'astra auth login' to authenticate.");
            return 0;
        }

        System.out.println("Authentication Status");
        System.out.println("─────────────────────");
        if (creds.getApiKey() != null && !creds.getApiKey().isBlank()) {
            System.out.println("  Mode: API key");
            System.out.println("  Key prefix: " + extractKeyPrefix(creds.getApiKey()));
        } else {
            System.out.println("  Mode: Username/password");
            System.out.println("  Username: " + (creds.getUsername() != null ? creds.getUsername() : "(unknown)"));
            System.out.println("  Email: " + (creds.getEmail() != null ? creds.getEmail() : "(unknown)"));
            if (creds.getAccessToken() != null) {
                System.out.println("  Access token: present");
            }
            if (creds.getRefreshToken() != null) {
                System.out.println("  Refresh token: present");
            }
        }

        if (creds.getExpiresAtEpoch() > 0) {
            Instant expiry = Instant.ofEpochMilli(creds.getExpiresAtEpoch());
            Instant now = Instant.now();
            if (expiry.isAfter(now)) {
                Duration remaining = Duration.between(now, expiry);
                System.out.println("  Token expires in: " + formatDuration(remaining));
            } else {
                System.out.println("  Token: EXPIRED (run 'astra auth login' to refresh)");
            }
        }
        return 0;
    }

    private String extractKeyPrefix(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) return "(invalid)";
        return apiKey.substring(0, 12) + "...";
    }

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + d.toSecondsPart() + "s";
    }
}
