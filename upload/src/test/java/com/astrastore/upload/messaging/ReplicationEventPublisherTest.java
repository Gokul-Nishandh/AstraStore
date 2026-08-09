package com.astrastore.upload.messaging;

import com.astrastore.shared.events.ChunkWrittenEvent;
import com.astrastore.upload.service.KafkaPublisherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReplicationEventPublisherTest {

    @Mock
    private KafkaPublisherService kafkaPublisherService;

    @Test
    void publishReplicationJob_forwardsEventToKafkaPublisher() {
        ReplicationEventPublisher publisher = new ReplicationEventPublisher(kafkaPublisherService);
        ChunkWrittenEvent event = ChunkWrittenEvent.builder()
                .chunkId("chunk-1")
                .primaryNodeIp("http://storage-node-1:8088")
                .sizeBytes(100L)
                .checksum("abc")
                .build();

        publisher.publishReplicationJob(event);

        verify(kafkaPublisherService).publishChunkWritten(event);
    }
}
