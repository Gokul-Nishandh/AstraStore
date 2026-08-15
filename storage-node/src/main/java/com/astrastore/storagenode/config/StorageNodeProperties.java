package com.astrastore.storagenode.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Identity and quota for this storage node.
 *
 * <p>Bound from {@code astrastore.node.*}, which in turn reads the
 * environment variables docker-compose already sets per container
 * ({@code STORAGE_NODE_ID}, {@code STORAGE_CAPACITY_BYTES}).
 *
 * <p><strong>Why a configured quota rather than the filesystem?</strong>
 * Every node in a local stack is a container on one host disk. Asking the
 * filesystem how big it is returns the same physical drive three times, so
 * summing it across the cluster invents capacity that does not exist. The
 * quota below is the only number that describes what <em>this</em> node is
 * allowed to hold, and it is the only one safe to add up.
 */
@Data
@Component
@ConfigurationProperties(prefix = "astrastore.node")
public class StorageNodeProperties {

    /** Node identity, e.g. {@code storage-node-1}. */
    private String id = "storage-node";

    /** How many bytes this node may hold. Default 20 GiB. */
    private long capacityBytes = 21_474_836_480L;

    /** Directory holding the hash-fanned chunk files. */
    private String storageRoot = "/data/storage";

    /**
     * How often the running usage counter is re-derived from a full directory
     * walk. The counter is maintained incrementally on every write and delete;
     * this only repairs drift (out-of-band file changes, a crash mid-write).
     */
    private long usageReconcileIntervalMs = 300_000L;
}
