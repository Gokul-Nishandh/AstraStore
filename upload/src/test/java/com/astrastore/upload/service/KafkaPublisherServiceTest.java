package com.astrastore.upload.service;

import com.astrastore.shared.events.ChunkWrittenEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaPublisherServiceTest {

    private static final String TOPIC = "astrastore.chunks.written";

    @Test
    void publishChunkWritten_sendsEventToTopic() {
        KafkaTemplate<String, ChunkWrittenEvent> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaPublisherService service = new KafkaPublisherService(kafkaTemplate);
        ChunkWrittenEvent event = event();
        CompletableFuture<SendResult<String, ChunkWrittenEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(TOPIC), eq("chunk-1"), any(ChunkWrittenEvent.class))).thenReturn(future);

        service.publishChunkWritten(event);

        verify(kafkaTemplate).send(TOPIC, "chunk-1", event);
    }

    @Test
    void publishChunkWritten_handlesSuccessfulSend() {
        KafkaTemplate<String, ChunkWrittenEvent> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaPublisherService service = new KafkaPublisherService(kafkaTemplate);
        ChunkWrittenEvent event = event();

        SendResult<String, ChunkWrittenEvent> result = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(result.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(7L);

        CompletableFuture<SendResult<String, ChunkWrittenEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(TOPIC), eq("chunk-1"), any(ChunkWrittenEvent.class))).thenReturn(future);

        service.publishChunkWritten(event);
        future.complete(result);
    }

    @Test
    void publishChunkWritten_toleratesAsyncFailure() {
        KafkaTemplate<String, ChunkWrittenEvent> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaPublisherService service = new KafkaPublisherService(kafkaTemplate);
        ChunkWrittenEvent event = event();

        CompletableFuture<SendResult<String, ChunkWrittenEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(TOPIC), eq("chunk-1"), any(ChunkWrittenEvent.class))).thenReturn(future);

        service.publishChunkWritten(event);
        future.completeExceptionally(new RuntimeException("kafka unavailable"));
    }

    private ChunkWrittenEvent event() {
        return ChunkWrittenEvent.builder()
                .chunkId("chunk-1")
                .primaryNodeIp("http://storage-node-1:8088")
                .sizeBytes(100L)
                .checksum("abc")
                .build();
    }
}
