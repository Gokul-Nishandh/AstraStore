package com.astrastore.upload.service;

import com.astrastore.shared.events.ChunkWrittenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for publishing chunk lifecycle events to Kafka.
 * Publishes ChunkWrittenEvent when a chunk has been successfully stored
 * to a primary storage node, enabling async replication by the Replication Service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaPublisherService {

    private static final String CHUNKS_WRITTEN_TOPIC = "astrastore.chunks.written";

    private final KafkaTemplate<String, ChunkWrittenEvent> kafkaTemplate;

    /**
     * Publishes a ChunkWrittenEvent to Kafka for consumption by the Replication Service.
     *
     * @param event the chunk written event containing chunk metadata
     */
    public void publishChunkWritten(ChunkWrittenEvent event) {
        log.info("Publishing ChunkWrittenEvent — chunkId={}, node={}, size={}",
                event.chunkId(), event.primaryNodeIp(), event.sizeBytes());

        CompletableFuture<SendResult<String, ChunkWrittenEvent>> future =
                kafkaTemplate.send(CHUNKS_WRITTEN_TOPIC, event.chunkId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish ChunkWrittenEvent — chunkId={}, error={}",
                        event.chunkId(), ex.getMessage());
            } else {
                log.debug("ChunkWrittenEvent published — chunkId={}, partition={}, offset={}",
                        event.chunkId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
