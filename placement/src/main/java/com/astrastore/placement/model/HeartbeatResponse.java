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
 *   "storageRoot": "/data/storage",
 *   "diskTotal": 107374182400,
 *   "diskFree":   96636764160,
 *   "diskUsed":   10737418240,
 *   "diskPercentUsed": "10.00%"
 * }
 * </pre>
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

    /** Absolute path of the storage root on the node's filesystem. */
    private String storageRoot;

    /** Total disk capacity in bytes. */
    @JsonProperty("diskTotal")
    private Long diskTotalBytes;

    /** Usable (free) disk space in bytes. */
    @JsonProperty("diskFree")
    private Long diskFreeBytes;

    /** Used disk space in bytes. */
    @JsonProperty("diskUsed")
    private Long diskUsedBytes;

    /** Human-readable percentage string, e.g. "10.00%". */
    private String diskPercentUsed;

    /**
     * Convenience predicate: returns {@code true} when the node reports itself UP.
     */
    public boolean isUp() {
        return "UP".equalsIgnoreCase(status);
    }
}
