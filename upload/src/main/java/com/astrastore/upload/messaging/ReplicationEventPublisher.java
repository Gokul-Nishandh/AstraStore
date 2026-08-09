package com.astrastore.upload.messaging;

import com.astrastore.shared.events.ChunkWrittenEvent;
import com.astrastore.upload.service.KafkaPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReplicationEventPublisher {

    private final KafkaPublisherService kafkaPublisherService;

    public void publishReplicationJob(ChunkWrittenEvent event) {
        log.info("Publishing replication job — chunkId={}, node={}", event.chunkId(), event.primaryNodeIp());
        kafkaPublisherService.publishChunkWritten(event);
    }
}
