/**
 * Background scanner that detects under-replicated chunks and triggers self-healing.
 * Runs every 60 seconds, queries MockChunkDatabase for chunks with < 3 replicas,
 * then publishes recovery events to Kafka to trigger the replication pipeline.
 * TODO: Replace with event-driven trigger from Heartbeat Service for production.
 */
package com.astrastore.replication.healing;

import com.astrastore.replication.db.MockChunkDatabase;
import com.astrastore.replication.db.ReplicaRecord;
import com.astrastore.shared.events.ChunkWrittenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class UnderReplicationScanner {

    private final MockChunkDatabase mockChunkDatabase;
    private final RepairRateLimiter repairRateLimiter;
    private final RecoveryPublisher recoveryPublisher;

    // TODO: For production, this 60s polling interval will cause database exhaustion.
    // It should be replaced with an Event-Driven trigger from the Heartbeat Service,
    // combined with a slow (e.g., 24-hour) background anti-entropy sweep.
    @Scheduled(fixedDelay = 60000)
    public void scanForDegradedChunks() {
        log.debug("Starting degraded chunk scan...");

        List<ReplicaRecord> underReplicated = mockChunkDatabase.findUnderReplicatedChunks();

        if (underReplicated.isEmpty()) {
            log.debug("Scan complete — no under-replicated chunks found");
            return;
        }

        log.info("Scan complete — found {} under-replicated chunk(s)", underReplicated.size());

        List<String> chunkIds = underReplicated.stream()
                .map(ReplicaRecord::getChunkId)
                .collect(Collectors.toList());

        repairRateLimiter.throttle(chunkIds);

        for (ReplicaRecord record : underReplicated) {
            triggerHealing(record);
        }

        log.info("Healing triggered for {} chunk(s)", underReplicated.size());
    }

    private void triggerHealing(ReplicaRecord record) {
        List<String> healthyReplicas = record.getHealthyReplicas();

        if (healthyReplicas.isEmpty()) {
            log.warn("Cannot heal chunk — no healthy replicas available — chunkId={}", record.getChunkId());
            return;
        }

        String sourceNode = healthyReplicas.get(0);

        ChunkWrittenEvent healingEvent = ChunkWrittenEvent.builder()
                .chunkId(record.getChunkId())
                .primaryNodeIp(sourceNode)
                .sizeBytes(record.getSizeBytes())
                .checksum(record.getChecksum())
                .build();

        log.info("Triggering healing — chunkId={}, sourceNode={}, missingReplicas={}",
                record.getChunkId(), sourceNode,
                record.getTargetReplicas() - record.getCurrentReplicas());

        recoveryPublisher.publishRecoveryEvent(healingEvent);
    }

    public int getUnderReplicatedCount() {
        return mockChunkDatabase.getUnderReplicatedCount();
    }
}
