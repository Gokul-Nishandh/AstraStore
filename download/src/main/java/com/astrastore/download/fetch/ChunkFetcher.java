package com.astrastore.download.fetch;

import com.astrastore.download.client.ChunkLocation;
import com.astrastore.download.client.StorageNodeClient;
import com.astrastore.download.exception.ChunkUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class ChunkFetcher {

    private final StorageNodeClient storageNodeClient;
    private final ExecutorService executor;

    public ChunkFetcher(
            StorageNodeClient storageNodeClient,
            @Value("${download.fetch-parallelism:4}") int fetchParallelism) {
        this.storageNodeClient = storageNodeClient;

        AtomicInteger threadCounter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                fetchParallelism,
                fetchParallelism,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(fetchParallelism * 2),
                runnable -> {
                    Thread thread = new Thread(runnable, "download-fetch-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public List<CompletableFuture<FetchedChunk>> submitAll(UUID objectId, List<ChunkLocation> orderedLocations) {
        List<CompletableFuture<FetchedChunk>> futures = new ArrayList<>(orderedLocations.size());
        for (ChunkLocation location : orderedLocations) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchChunk(objectId, location), executor));
        }
        return futures;
    }

    public FetchedChunk await(CompletableFuture<FetchedChunk> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ChunkUnavailableException("Chunk fetch failed — " + e.getMessage(), e.getCause());
        }
    }

    private FetchedChunk fetchChunk(UUID objectId, ChunkLocation location) {
        String chunkId = chunkId(objectId, location.chunkIndex());
        String primary = location.nodeId();

        try {
            byte[] data = storageNodeClient.readChunk(primary, chunkId);
            return new FetchedChunk(location.chunkIndex(), primary, data, location.checksum());
        } catch (ChunkUnavailableException primaryFailure) {
            String replica = eligibleReplica(location);
            if (replica == null) {
                throw primaryFailure;
            }
            log.warn("Primary chunk read failed — chunkId={}, primary={}, falling back to replica={}",
                    chunkId, primary, replica);
            byte[] data = storageNodeClient.readChunk(replica, chunkId);
            return new FetchedChunk(location.chunkIndex(), replica, data, location.checksum());
        }
    }

    private String eligibleReplica(ChunkLocation location) {
        if (location.replicaNodeId() == null || location.replicaNodeId().isBlank()) {
            return null;
        }
        String status = location.replicationStatus();
        if ("REPLICATED".equalsIgnoreCase(status) || "COMPLETE".equalsIgnoreCase(status)) {
            return location.replicaNodeId();
        }
        return null;
    }

    public static String chunkId(UUID objectId, int chunkIndex) {
        return objectId + "-chunk-" + String.format("%04d", chunkIndex);
    }
}
