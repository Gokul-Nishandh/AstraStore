/**
 * Publisher for self-healing recovery events to Kafka.
 * Publishes ChunkWrittenEvent to trigger the existing Phase 3 ReplicationOrchestrator.
 * Reuses the existing Kafka topic and replication pipeline for healing operations.
 */
package com.astrastore.replication.healing;

import com.astrastore.shared.events.ChunkWrittenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryPublisher {

    private static final String CHUNKS_WRITTEN_TOPIC = "astrastore.chunks.written";

    private final KafkaTemplate<String, ChunkWrittenEvent> kafkaTemplate;

    public void publishRecoveryEvent(ChunkWrittenEvent event) {
        log.info("Publishing recovery event — chunkId={}, sourceNode={}, size={}, checksum={}",
                event.chunkId(), event.primaryNodeIp(), event.sizeBytes(), event.checksum());

        CompletableFuture<SendResult<String, ChunkWrittenEvent>> future =
                kafkaTemplate.send(CHUNKS_WRITTEN_TOPIC, event.chunkId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish recovery event — chunkId={}, error={}",
                        event.chunkId(), ex.getMessage());
            } else {
                log.info("Recovery event published — chunkId={}, partition={}, offset={}",
                        event.chunkId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
