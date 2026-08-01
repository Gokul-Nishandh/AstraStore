package com.astrastore.replication.listener;

import com.astrastore.shared.events.ChunkWrittenEvent;
import com.astrastore.replication.orchestrator.ReplicationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for ChunkWrittenEvent.
 * Consumes events from the astrastore.chunks.written topic
 * and triggers the replication orchestration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaChunkListener {

    private final ReplicationOrchestrator replicationOrchestrator;

    /**
     * Consumes ChunkWrittenEvent from Kafka and triggers replication.
     *
     * @param event the chunk written event
     */
    @KafkaListener(
            topics = "astrastore.chunks.written",
            groupId = "replication-service"
    )
    public void onChunkWritten(ChunkWrittenEvent event) {
        log.info("Received ChunkWrittenEvent — chunkId={}, primary={}, size={}",
                event.chunkId(), event.primaryNodeIp(), event.sizeBytes());

        try {
            replicationOrchestrator.orchestrateReplication(event);
        } catch (Exception e) {
            log.error("Replication orchestration failed — chunkId={}, error={}",
                    event.chunkId(), e.getMessage(), e);
        }
    }
}
