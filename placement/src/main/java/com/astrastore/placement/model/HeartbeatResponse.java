package com.astrastore.placement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO that maps to the JSON body returned by a storage node's
 * {@code GET /api/v1/health/heartbeat} endpoint.
 *
 * <p>Example response from the storage node:</p>
 * <pre>
 * {
 *   "status": "UP",
 *   "nodeId": "storage-node-1",
 *   "storageRoot": "/data/storage",
 *   "capacityBytes": 21474836480,
 *   "usedBytes": 5242880,
 *   "availableBytes": 21469593600,
 *   "chunkCount": 12,
 *   "usedRatio": 0.000244,
 *   "hostDiskFreeBytes": 402653184000,
 *   "usageMeasured": true
 * }
 * </pre>
 *
 * <h2>Why the legacy fields are not trusted</h2>
 * <p>Older nodes returned {@code diskTotal}/{@code diskFree} straight from
 * {@code FileStore}, i.e. the shared host drive. Those keys are still parsed
 * for diagnostics, but they are never used as capacity: a node that has not
 * reported {@code capacityBytes} is treated as <em>unknown</em>, which the
 * cluster view surfaces honestly, rather than silently re-introducing the
 * triple-counted host disk.</p>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures the DTO
 * is resilient to future additions in the storage node response.</p>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartbeatResponse {

    /** Node self-reported status — typically "UP". */
    private String status;

    /** The node's own identity, e.g. "storage-node-1". */
    private String nodeId;

    /** Absolute path of the storage root on the node's filesystem. */
    private String storageRoot;

    // --- Quota accounting: the figures that may be summed ---------------

    /** Bytes the node is configured to hold. */
    private Long capacityBytes;

    /** Bytes the node has actually stored. */
    private Long usedBytes;

    /** Remaining quota, floored at zero by the node. */
    private Long availableBytes;

    /** Number of chunk files held. */
    private Long chunkCount;

    /** usedBytes / capacityBytes, as a number. */
    private Double usedRatio;

    /** True once the node has completed a full walk of its storage root. */
    private Boolean usageMeasured;

    // --- Advisory: the shared host filesystem, never aggregated ---------

    /** Free space on the node's underlying filesystem. */
    private Long hostDiskFreeBytes;

    /** Total size of the node's underlying filesystem. */
    private Long hostDiskTotalBytes;

    // --- Deprecated aliases, parsed for diagnostics only ----------------

    @Deprecated
    @JsonProperty("diskTotal")
    private Long legacyDiskTotal;

    @Deprecated
    @JsonProperty("diskFree")
    private Long legacyDiskFree;

    @Deprecated
    @JsonProperty("diskUsed")
    private Long legacyDiskUsed;

    /**
     * Convenience predicate: returns {@code true} when the node reports itself UP.
     */
    public boolean isUp() {
        return "UP".equalsIgnoreCase(status);
    }

    /**
     * Whether this payload carries quota-based capacity. A node that answers
     * without it is reachable but its capacity is unknown, and the cluster
     * totals say so rather than guessing.
     */
    public boolean hasCapacityData() {
        return capacityBytes != null && usedBytes != null;
    }
}
