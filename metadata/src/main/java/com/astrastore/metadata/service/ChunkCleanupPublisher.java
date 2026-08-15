package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.event.ChunkCleanupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes chunk-cleanup events when metadata rows are hard deleted.
 *
 * <p>Mirrors {@code upload}'s {@code KafkaPublisherService}: fire-and-forget
 * {@code KafkaTemplate.send} keyed by chunk id, failures logged rather than
 * thrown. A broker outage must not roll back a delete the user already
 * confirmed — the row is gone either way, and an orphaned chunk is recoverable
 * by a sweep where a half-deleted object is not.
 *
 * <p>The template is resolved lazily through an {@link ObjectProvider} so the
 * service still starts (and the test profile still loads) when Kafka
 * auto-configuration is switched off.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkCleanupPublisher {

    public static final String CHUNKS_DELETED_TOPIC = "astrastore.chunks.deleted";

    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

    /** Publishes one cleanup event per chunk of a permanently deleted object. */
    public void publishCleanup(UUID objectId, List<ChunkLocation> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.debug("No chunk locations recorded for object {} — nothing to clean up.", objectId);
            return;
        }

        KafkaTemplate<String, Object> template = kafkaTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("Kafka is not configured — {} chunk-cleanup events for object {} were not published.",
                    chunks.size(), objectId);
            return;
        }

        Instant deletedAt = Instant.now();
        for (ChunkLocation chunk : chunks) {
            String chunkId = ChunkCleanupEvent.chunkId(objectId, chunk.getChunkIndex());
            ChunkCleanupEvent event = new ChunkCleanupEvent(
                    objectId,
                    chunkId,
                    chunk.getChunkIndex(),
                    chunk.getNodeId(),
                    chunk.getReplicaNodeId(),
                    deletedAt);
            try {
                template.send(CHUNKS_DELETED_TOPIC, chunkId, event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish ChunkCleanupEvent — chunkId={}, error={}",
                                        chunkId, ex.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.error("Failed to publish ChunkCleanupEvent — chunkId={}, error={}",
                        chunkId, e.getMessage());
            }
        }
        log.info("Published {} chunk-cleanup events for object {}", chunks.size(), objectId);
    }
}
