package com.astrastore.storagenode.controller;

import com.astrastore.storagenode.config.StorageConfig;
import com.astrastore.storagenode.config.StorageNodeProperties;
import com.astrastore.storagenode.service.NodeUsageTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heartbeat endpoint for a single storage node.
 *
 * <h2>What changed and why</h2>
 * <p>This endpoint used to report {@code FileStore#getTotalSpace} as the
 * node's capacity. Every node in a compose stack is a container sharing one
 * host disk, so all three reported the same physical drive, placement summed
 * them, and the dashboard showed a cluster three times larger than any disk
 * that exists — with a "used" figure that was really the operator's laptop
 * filling up, not AstraStore storing anything.
 *
 * <p>The node now reports its configured quota ({@code capacityBytes}) and
 * the bytes it has genuinely written ({@code usedBytes}). Those are additive
 * across nodes. The host filesystem is still useful for spotting a volume
 * about to fill, so it is kept as {@code hostDiskFreeBytes} — named so it
 * can never be mistaken for cluster capacity again.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final StorageConfig storageConfig;
    private final StorageNodeProperties properties;
    private final NodeUsageTracker usageTracker;

    /**
     * Returns this node's identity, quota, real usage and — separately, as
     * advisory information only — the underlying filesystem's free space.
     */
    @GetMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat() {
        Path storageRoot = storageConfig.getStorageRoot();

        long capacityBytes = properties.getCapacityBytes();
        long usedBytes = usageTracker.getUsedBytes();
        long availableBytes = Math.max(0L, capacityBytes - usedBytes);
        long chunkCount = usageTracker.getChunkCount();
        Double usedRatio = capacityBytes > 0
                ? (double) usedBytes / (double) capacityBytes
                : null;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("status", "UP");
        metrics.put("nodeId", properties.getId());
        metrics.put("storageRoot", storageRoot.toString());
        metrics.put("reportedAt", Instant.now().toString());

        // --- The node's own accounting: additive across the cluster -----
        metrics.put("capacityBytes", capacityBytes);
        metrics.put("usedBytes", usedBytes);
        metrics.put("availableBytes", availableBytes);
        metrics.put("chunkCount", chunkCount);
        metrics.put("usedRatio", usedRatio);

        // Null until the first full walk finishes, so a consumer can tell a
        // measured zero from a not-yet-measured one.
        Instant reconciledAt = usageTracker.getLastReconciledAt();
        metrics.put("usageReconciledAt", reconciledAt != null ? reconciledAt.toString() : null);
        metrics.put("usageMeasured", reconciledAt != null);

        // --- Advisory only: the shared host filesystem ------------------
        // NOT cluster capacity. Three nodes on one laptop all report the
        // same drive here; summing this field is what produced the old lie.
        Long hostFree = null;
        Long hostTotal = null;
        try {
            FileStore store = Files.getFileStore(storageRoot);
            hostFree = store.getUsableSpace();
            hostTotal = store.getTotalSpace();
        } catch (IOException e) {
            log.warn("Could not read host filesystem for {} — reporting null", storageRoot, e);
        }
        metrics.put("hostDiskFreeBytes", hostFree);
        metrics.put("hostDiskTotalBytes", hostTotal);

        // --- Deprecated aliases ----------------------------------------
        // Same keys as before so nothing 404s or NPEs mid-rollout, but now
        // sourced from the quota accounting instead of the host disk.
        metrics.put("diskTotal", capacityBytes);
        metrics.put("diskFree", availableBytes);
        metrics.put("diskUsed", usedBytes);
        metrics.put("diskPercentUsed", usedRatio == null ? null : usedRatio * 100.0);

        log.debug("Heartbeat — nodeId={}, used={}B/{}B, chunks={}, hostFree={}B",
                properties.getId(), usedBytes, capacityBytes, chunkCount, hostFree);

        return ResponseEntity.ok(metrics);
    }
}
