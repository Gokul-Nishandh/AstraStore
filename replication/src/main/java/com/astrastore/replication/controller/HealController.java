/**
 * Admin API to manually trigger the self-healing scan.
 * Bypasses the 60-second polling interval for immediate testing.
 * Triggers UnderReplicationScanner to run healing immediately.
 */
package com.astrastore.replication.controller;

import com.astrastore.replication.db.ReplicaRecord;
import com.astrastore.replication.healing.UnderReplicationScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/heal")
@RequiredArgsConstructor
@Slf4j
public class HealController {

    private final UnderReplicationScanner underReplicationScanner;

    @PostMapping("/run")
    public ResponseEntity<?> triggerHealingNow() {
        log.info("Manual heal triggered via admin endpoint");

        int underReplicatedBefore = underReplicationScanner.getUnderReplicatedCount();

        underReplicationScanner.scanForDegradedChunks();

        return ResponseEntity.ok(Map.of(
                "message", "Healing scan triggered",
                "underReplicatedChunksFound", underReplicatedBefore
        ));
    }
}
