/**
 * Cluster nodes command displays detailed information about storage nodes.
 * Calls Placement service /api/v1/cluster/nodes/eligible endpoint.
 * Shows node ID, URL, state, disk metrics, and eligibility for placement.
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
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "nodes",
        mixinStandardHelpOptions = true,
        description = "List all storage nodes."
)
public class ClusterNodesCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--eligible"}, description = "Show only eligible-for-placement nodes")
    private boolean eligibleOnly;

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
            String path = eligibleOnly ? "/api/v1/cluster/nodes/eligible" : "/api/v1/cluster/nodes/eligible";
            List<NodeInfo> nodes = client.get(path, new TypeReference<List<NodeInfo>>() {});

            if ("json".equalsIgnoreCase(output)) {
                System.out.println(client.getMapper().writeValueAsString(nodes));
                return 0;
            }

            if (nodes == null || nodes.isEmpty()) {
                System.out.println("No nodes found.");
                return 0;
            }

            System.out.println(eligibleOnly ? "Eligible Storage Nodes" : "All Storage Nodes");
            System.out.println("─────────────────────");
            for (NodeInfo node : nodes) {
                String emoji = switch (node.state) {
                    case "HEALTHY" -> "🟢";
                    case "DEGRADED" -> "🟡";
                    case "DOWN" -> "🔴";
                    case "RECOVERING" -> "🔄";
                    default -> "⚪";
                };
                System.out.println("  " + emoji + " " + node.nodeId + " — " + node.state);
                System.out.println("      URL:          " + node.baseUrl);
                System.out.println("      Disk Free:    " + formatGb(node.diskFreeBytes));
                System.out.println("      Disk Total:   " + formatGb(node.diskTotalBytes));
                System.out.println("      Free Ratio:    " + node.diskFreeRatio);
                System.out.println("      Failures:      " + node.consecutiveFailures);
                System.out.println("      Eligible:      " + (node.eligibleForPlacement ? "yes" : "no"));
                System.out.println();
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to list nodes: " + e.getMessage());
            return 1;
        }
    }

    private String formatGb(long bytes) {
        double gb = bytes / 1024.0 / 1024.0 / 1024.0;
        return String.format("%.2f GB", gb);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeInfo {
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
