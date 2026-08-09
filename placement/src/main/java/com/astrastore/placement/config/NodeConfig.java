package com.astrastore.placement.config;

import lombok.Data;

/**
 * Binds a single entry from the {@code astrastore.cluster.nodes[]} list
 * in {@code application.yaml}.
 *
 * <p>Example YAML fragment:</p>
 * <pre>
 * astrastore:
 *   cluster:
 *     nodes:
 *       - id: storage-node-1
 *         url: http://storage-node-1:8088
 * </pre>
 */
@Data
public class NodeConfig {

    /** Human-readable identifier matching the Docker service name. */
    private String id;

    /** Full base URL used by the HeartbeatService (no trailing slash). */
    private String url;
}
