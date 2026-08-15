package com.astrastore.storagenode.service;

import com.astrastore.storagenode.config.StorageNodeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Tracks how many bytes this node actually holds.
 *
 * <p>Walking the storage tree on every 10 s heartbeat is O(chunks) and gets
 * slower exactly as the cluster gets busier, so the number is kept as a
 * running counter: {@link #onChunkStored} and {@link #onChunkDeleted} adjust
 * it in O(1) as data moves.
 *
 * <p>A counter alone would drift — a crash between writing a file and
 * incrementing, a chunk placed on disk out of band, or simply a restart with
 * a populated volume. So the counter is <em>derived</em> from a full walk at
 * startup and re-derived periodically. The walk is the source of truth; the
 * counter is a cache of it. Writes landing during a walk are accumulated
 * separately and re-applied afterwards so they are neither lost nor
 * double-counted.
 */
@Component
@Slf4j
public class NodeUsageTracker {

    private final Path storageRoot;

    private final AtomicLong usedBytes = new AtomicLong(0L);
    private final AtomicLong chunkCount = new AtomicLong(0L);

    /** Mutations observed while a reconciling walk is in flight. */
    private final AtomicLong inFlightBytes = new AtomicLong(0L);
    private final AtomicLong inFlightCount = new AtomicLong(0L);

    private final AtomicReference<Instant> lastReconciledAt = new AtomicReference<>(null);
    private final AtomicLong lastReconcileDurationMs = new AtomicLong(-1L);

    private volatile boolean reconciling = false;
    private final Object mutex = new Object();

    public NodeUsageTracker(StorageNodeProperties properties) {
        this.storageRoot = Paths.get(properties.getStorageRoot());
    }

    // ----------------------------------------------------------------
    // Mutations
    // ----------------------------------------------------------------

    /** Records that {@code sizeBytes} of new chunk data landed on this node. */
    public void onChunkStored(long sizeBytes) {
        apply(sizeBytes, 1L);
    }

    /** Records that a chunk of {@code sizeBytes} was removed from this node. */
    public void onChunkDeleted(long sizeBytes) {
        apply(-sizeBytes, -1L);
    }

    private void apply(long byteDelta, long countDelta) {
        synchronized (mutex) {
            usedBytes.addAndGet(byteDelta);
            chunkCount.addAndGet(countDelta);
            if (reconciling) {
                inFlightBytes.addAndGet(byteDelta);
                inFlightCount.addAndGet(countDelta);
            }
        }
    }

    // ----------------------------------------------------------------
    // Reads
    // ----------------------------------------------------------------

    /** Bytes this node is holding right now. Never negative. */
    public long getUsedBytes() {
        return Math.max(0L, usedBytes.get());
    }

    /** Number of chunk files this node is holding. Never negative. */
    public long getChunkCount() {
        return Math.max(0L, chunkCount.get());
    }

    /**
     * When the counter was last re-derived from a full walk, or {@code null}
     * if that has not happened yet — in which case the figures above are a
     * lower bound, not a measurement, and callers should say so.
     */
    public Instant getLastReconciledAt() {
        return lastReconciledAt.get();
    }

    /** Duration of the last full walk in ms, or {@code null} if never run. */
    public Long getLastReconcileDurationMs() {
        long v = lastReconcileDurationMs.get();
        return v < 0 ? null : v;
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    // ----------------------------------------------------------------
    // Reconciliation
    // ----------------------------------------------------------------

    /**
     * Derives the counter from what is actually on disk.
     *
     * <p>Runs after the context is ready rather than during bean creation, so
     * a slow volume cannot stall startup or a container health check.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        reconcile();
    }

    @Scheduled(
            fixedDelayString = "${astrastore.node.usage-reconcile-interval-ms:300000}",
            initialDelayString = "${astrastore.node.usage-reconcile-interval-ms:300000}")
    public void reconcilePeriodically() {
        reconcile();
    }

    /**
     * Walks the storage root and resets the counter to the measured total.
     * Failures are logged, never thrown: a heartbeat must not fail because a
     * directory was briefly unreadable.
     */
    public synchronized void reconcile() {
        long startedAt = System.nanoTime();

        synchronized (mutex) {
            inFlightBytes.set(0L);
            inFlightCount.set(0L);
            reconciling = true;
        }

        try {
            Walk walk = walkStorageRoot();

            synchronized (mutex) {
                usedBytes.set(walk.bytes + inFlightBytes.get());
                chunkCount.set(walk.files + inFlightCount.get());
                reconciling = false;
            }

            lastReconciledAt.set(Instant.now());
            lastReconcileDurationMs.set((System.nanoTime() - startedAt) / 1_000_000L);

            log.info("Usage reconciled — usedBytes={}, chunks={}, tookMs={}",
                    usedBytes.get(), chunkCount.get(), lastReconcileDurationMs.get());

        } catch (IOException | RuntimeException e) {
            synchronized (mutex) {
                reconciling = false;
            }
            log.warn("Usage reconciliation failed — root={}, keeping previous counter ({} bytes)",
                    storageRoot, usedBytes.get(), e);
        }
    }

    private Walk walkStorageRoot() throws IOException {
        if (!Files.isDirectory(storageRoot)) {
            return new Walk(0L, 0L);
        }
        long bytes = 0L;
        long files = 0L;
        try (Stream<Path> stream = Files.walk(storageRoot)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(path)) continue;
                String name = path.getFileName().toString();
                // Partial writes land in the system temp dir, but skip any
                // stray marker files so they are never billed as stored data.
                if (name.endsWith(".tmp")) continue;
                try {
                    bytes += Files.size(path);
                    files++;
                } catch (IOException e) {
                    // File vanished mid-walk (concurrent delete) — not an error.
                    log.debug("Skipping unreadable file during walk — path={}", path);
                }
            }
        }
        return new Walk(bytes, files);
    }

    private record Walk(long bytes, long files) {}
}
