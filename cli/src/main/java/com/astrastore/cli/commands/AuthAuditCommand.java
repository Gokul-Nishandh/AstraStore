/**
 * Audit command displays the authenticated user's security event log.
 * Calls GET /api/auth/audit (paginated) and renders results in a table.
 * Shows timestamp, action, success/failure, and IP address.
 * Supports JSON output for scripting.
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
        name = "audit",
        mixinStandardHelpOptions = true,
        description = "Show your security audit log."
)
public class AuthAuditCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--page"}, description = "Page number (0-based, default: 0)")
    private int page = 0;

    @CommandLine.Option(names = {"--size"}, description = "Number of entries per page (default: 20, max: 100)")
    private int size = 20;

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
            String path = "/api/auth/audit?page=" + page + "&size=" + Math.min(size, 100);
            List<AuditEntry> entries = client.get(path, new TypeReference<List<AuditEntry>>() {});

            if (entries == null || entries.isEmpty()) {
                if ("json".equalsIgnoreCase(output)) {
                    System.out.println("[]");
                } else {
                    System.out.println("No audit log entries found.");
                }
                return 0;
            }

            if ("json".equalsIgnoreCase(output)) {
                System.out.println(client.getMapper().writeValueAsString(entries));
                return 0;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

            System.out.println("Audit Log (page " + page + ", " + entries.size() + " entries)");
            System.out.println("─────────────────────────");
            for (AuditEntry entry : entries) {
                String ts = entry.getTimestamp() != null
                        ? fmt.format(Instant.parse(entry.getTimestamp().toString()))
                        : "(unknown)";
                String status = entry.isSuccess() ? "✓" : "✗";
                System.out.println("  " + status + "  " + ts + "  " + entry.getAction());
                if (entry.getIpAddress() != null) {
                    System.out.println("       IP: " + entry.getIpAddress());
                }
                if (!entry.isSuccess() && entry.getFailureReason() != null) {
                    System.out.println("       Reason: " + entry.getFailureReason());
                }
            }
            System.out.println();
            System.out.println("  Use --page and --size to paginate. Run 'astra auth audit --page 1' for more.");
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
    public static class AuditEntry {
        private Long id;
        private Long userId;
        private String action;
        private String ipAddress;
        private String userAgent;
        private boolean success;
        private String failureReason;
        private Object timestamp;
    }
}
