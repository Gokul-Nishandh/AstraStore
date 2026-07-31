package com.astrastore.storagenode.controller;

import com.astrastore.storagenode.config.StorageConfig;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for storage node.
 * Exposes disk space metrics used by placement and monitoring services.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final StorageConfig storageConfig;

    /**
     * Returns the node's current hardware metrics including available disk space.
     * Used by placement and monitoring services for cluster load balancing.
     */
    @GetMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat() throws IOException {
        Path storageRoot = storageConfig.getStorageRoot();
        FileStore store = Files.getFileStore(storageRoot);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("status", "UP");
        metrics.put("storageRoot", storageRoot.toString());
        metrics.put("diskTotal", store.getTotalSpace());
        metrics.put("diskFree", store.getUsableSpace());
        metrics.put("diskUsed", store.getTotalSpace() - store.getUsableSpace());
        metrics.put("diskPercentUsed",
                String.format("%.2f%%",
                        100.0 * (store.getTotalSpace() - store.getUsableSpace()) / store.getTotalSpace()));

        log.debug("Heartbeat metrics — diskFree={}, diskTotal={}",
                store.getUsableSpace(), store.getTotalSpace());

        return ResponseEntity.ok(metrics);
    }
}
