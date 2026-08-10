/**
 * Cluster health command displays an overview of cluster status.
 * Calls Placement service /api/v1/cluster/status endpoint.
 * Shows total/healthy/degraded/down nodes, disk usage, and individual node details.
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "health",
        mixinStandardHelpOptions = true,
        description = "Show cluster health summary."
)
public class ClusterHealthCommand implements Callable<Integer> {

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
            AstraHttpClient client = new AstraHttpClient(config.getPlacementUrl());
            ClusterStatus status = client.get("/api/v1/cluster/status", new TypeReference<>() {});

            if ("json".equalsIgnoreCase(output)) {
                System.out.println(client.getMapper().writeValueAsString(status));
                return 0;
            }

            long totalGb = status.totalDiskBytes / 1024 / 1024 / 1024;
            long freeGb = status.totalFreeBytes / 1024 / 1024 / 1024;
            double freePct = totalGb > 0 ? (freeGb * 100.0 / totalGb) : 0.0;

            System.out.println("AstraStore Cluster Health");
            System.out.println("─────────────────────────");
            System.out.println("  Total Nodes:    " + status.totalNodes);
            System.out.println("  ├ Healthy:      " + status.healthyNodes + " 🟢");
            System.out.println("  ├ Degraded:     " + status.degradedNodes + " 🟡");
            System.out.println("  ├ Down:         " + status.downNodes + " 🔴");
            System.out.println("  └ Recovering:   " + status.recoveringNodes);
            System.out.println();
            System.out.println("  Eligible:       " + status.eligibleNodes + "/" + status.totalNodes);
            System.out.println("  Disk:           " + freeGb + " GB free / " + totalGb + " GB total (" + String.format("%.1f%%", freePct) + " free)");

            if (status.nodes != null && !status.nodes.isEmpty()) {
                System.out.println();
                System.out.println("Nodes");
                System.out.println("─────");
                for (NodeStatus node : status.nodes) {
                    String emoji = switch (node.state) {
                        case "HEALTHY" -> "🟢";
                        case "DEGRADED" -> "🟡";
                        case "DOWN" -> "🔴";
                        case "RECOVERING" -> "🔄";
                        default -> "⚪";
                    };
                    System.out.println("  " + emoji + " " + node.nodeId + " (" + node.state + ")");
                    System.out.println("      URL:    " + node.baseUrl);
                    System.out.println("      Disk:   " + formatDisk(node.diskUsedBytes, node.diskTotalBytes));
                    System.out.println("      Failures: " + node.consecutiveFailures);
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to fetch cluster health: " + e.getMessage());
            return 1;
        }
    }

    private String formatDisk(long used, long total) {
        double usedGb = used / 1024.0 / 1024.0 / 1024.0;
        double totalGb = total / 1024.0 / 1024.0 / 1024.0;
        double pct = total > 0 ? (used * 100.0 / total) : 0.0;
        return String.format("%.1f GB / %.1f GB (%.1f%% used)", usedGb, totalGb, pct);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClusterStatus {
        private int totalNodes;
        private long healthyNodes;
        private long degradedNodes;
        private long downNodes;
        private long recoveringNodes;
        private long eligibleNodes;
        private long totalDiskBytes;
        private long totalFreeBytes;
        private String clusterFreeRatio;
        private String checkedAt;
        private List<NodeStatus> nodes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeStatus {
        private String nodeId;
        private String baseUrl;
        private String state;
        private String lastSeen;
        private String lastChecked;
        private int consecutiveFailures;
        private long diskTotalBytes;
        private long diskFreeBytes;
        private long diskUsedBytes;
        private String diskFreeRatio;
        private boolean eligibleForPlacement;
    }
}
