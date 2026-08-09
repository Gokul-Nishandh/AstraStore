package com.astrastore.download.fetch;

import com.astrastore.download.client.ChunkLocation;
import com.astrastore.download.client.StorageNodeClient;
import com.astrastore.download.exception.ChunkUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkFetcherTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();

    @Mock
    private StorageNodeClient storageNodeClient;

    private ChunkFetcher chunkFetcher;

    @BeforeEach
    void setUp() {
        chunkFetcher = new ChunkFetcher(storageNodeClient, 2);
    }

    @Test
    void fetchChunk_readsFromPrimary() {
        ChunkLocation location = location(0, "http://node-a", null, "PENDING");
        when(storageNodeClient.readChunk("http://node-a", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenReturn(new byte[]{1, 2, 3});

        FetchedChunk chunk = awaitFirst(location);

        assertThat(chunk.chunkIndex()).isZero();
        assertThat(chunk.sourceNode()).isEqualTo("http://node-a");
        assertThat(chunk.data()).containsExactly(1, 2, 3);
        verify(storageNodeClient, never()).readChunk(org.mockito.ArgumentMatchers.eq("http://node-b"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void fetchChunk_fallsBackToReplicatedReplicaOnPrimaryFailure() {
        ChunkLocation location = location(0, "http://node-a", "http://node-b", "REPLICATED");
        when(storageNodeClient.readChunk("http://node-a", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenThrow(new ChunkUnavailableException("primary down"));
        when(storageNodeClient.readChunk("http://node-b", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenReturn(new byte[]{9, 9});

        FetchedChunk chunk = awaitFirst(location);

        assertThat(chunk.sourceNode()).isEqualTo("http://node-b");
        assertThat(chunk.data()).containsExactly(9, 9);
    }

    @Test
    void fetchChunk_doesNotUseReplicaThatIsNotReplicated() {
        ChunkLocation location = location(0, "http://node-a", "http://node-b", "PENDING");
        when(storageNodeClient.readChunk("http://node-a", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenThrow(new ChunkUnavailableException("primary down"));

        assertThatThrownBy(() -> awaitFirst(location))
                .isInstanceOf(ChunkUnavailableException.class);
        verify(storageNodeClient, never()).readChunk("http://node-b", ChunkFetcher.chunkId(OBJECT_ID, 0));
    }

    @Test
    void fetchChunk_failsWhenNoReplicaConfigured() {
        ChunkLocation location = location(0, "http://node-a", null, "REPLICATED");
        when(storageNodeClient.readChunk("http://node-a", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenThrow(new ChunkUnavailableException("primary down"));

        assertThatThrownBy(() -> awaitFirst(location))
                .isInstanceOf(ChunkUnavailableException.class)
                .hasMessageContaining("primary down");
    }

    @Test
    void fetchChunk_failsWhenBothPrimaryAndReplicaUnavailable() {
        ChunkLocation location = location(0, "http://node-a", "http://node-b", "REPLICATED");
        when(storageNodeClient.readChunk("http://node-a", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenThrow(new ChunkUnavailableException("primary down"));
        when(storageNodeClient.readChunk("http://node-b", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenThrow(new ChunkUnavailableException("replica down"));

        assertThatThrownBy(() -> awaitFirst(location))
                .isInstanceOf(ChunkUnavailableException.class)
                .hasMessageContaining("replica down");
    }

    @Test
    void submitAll_returnsOneFuturePerOrderedLocation() {
        ChunkLocation chunk0 = location(0, "http://node-a", null, "PENDING");
        ChunkLocation chunk1 = location(1, "http://node-b", null, "PENDING");
        when(storageNodeClient.readChunk("http://node-a", ChunkFetcher.chunkId(OBJECT_ID, 0)))
                .thenReturn(new byte[]{1});
        when(storageNodeClient.readChunk("http://node-b", ChunkFetcher.chunkId(OBJECT_ID, 1)))
                .thenReturn(new byte[]{2});

        List<CompletableFuture<FetchedChunk>> futures = chunkFetcher.submitAll(OBJECT_ID, List.of(chunk0, chunk1));

        assertThat(futures).hasSize(2);
        assertThat(chunkFetcher.await(futures.get(0)).chunkIndex()).isZero();
        assertThat(chunkFetcher.await(futures.get(1)).chunkIndex()).isEqualTo(1);
    }

    private FetchedChunk awaitFirst(ChunkLocation location) {
        CompletableFuture<FetchedChunk> future = chunkFetcher.submitAll(OBJECT_ID, List.of(location)).get(0);
        return chunkFetcher.await(future);
    }

    private ChunkLocation location(int index, String nodeId, String replicaNodeId, String status) {
        return new ChunkLocation(
                UUID.randomUUID(),
                OBJECT_ID,
                index,
                nodeId,
                replicaNodeId,
                status,
                "aa");
    }
}
