package com.astrastore.storagenode.service;

import com.astrastore.storagenode.config.StorageNodeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The usage counter is the number the whole capacity story rests on, so the
 * cases that matter are the ones where a naive counter would lie: after a
 * restart, after drift, and after a delete.
 */
class NodeUsageTrackerTest {

    private static StorageNodeProperties propsFor(Path root) {
        StorageNodeProperties props = new StorageNodeProperties();
        props.setId("storage-node-1");
        props.setCapacityBytes(1_000L);
        props.setStorageRoot(root.toString());
        return props;
    }

    private static void writeChunk(Path root, String prefix, String chunkId, int sizeBytes) throws IOException {
        Path dir = root.resolve(prefix);
        Files.createDirectories(dir);
        Files.write(dir.resolve(chunkId), new byte[sizeBytes]);
    }

    @Test
    void startsEmptyAndUnmeasuredUntilFirstWalk(@TempDir Path root) {
        NodeUsageTracker tracker = new NodeUsageTracker(propsFor(root));

        assertThat(tracker.getUsedBytes()).isZero();
        assertThat(tracker.getChunkCount()).isZero();
        // Never measured yet — callers must be able to tell this apart from
        // a measured zero rather than assume the node is empty.
        assertThat(tracker.getLastReconciledAt()).isNull();
    }

    @Test
    void tracksWritesAndDeletesIncrementally(@TempDir Path root) {
        NodeUsageTracker tracker = new NodeUsageTracker(propsFor(root));

        tracker.onChunkStored(400L);
        tracker.onChunkStored(250L);

        assertThat(tracker.getUsedBytes()).isEqualTo(650L);
        assertThat(tracker.getChunkCount()).isEqualTo(2L);

        tracker.onChunkDeleted(400L);

        assertThat(tracker.getUsedBytes()).isEqualTo(250L);
        assertThat(tracker.getChunkCount()).isEqualTo(1L);
    }

    @Test
    void neverReportsNegativeUsage(@TempDir Path root) {
        NodeUsageTracker tracker = new NodeUsageTracker(propsFor(root));

        // More deletes than writes can only happen through drift, and a
        // negative "bytes stored" would corrupt every cluster total.
        tracker.onChunkDeleted(500L);

        assertThat(tracker.getUsedBytes()).isZero();
        assertThat(tracker.getChunkCount()).isZero();
    }

    @Test
    void usageSurvivesRestartByReconcilingFromDisk(@TempDir Path root) throws IOException {
        // --- First "process life": three chunks land on the volume --------
        NodeUsageTracker before = new NodeUsageTracker(propsFor(root));
        writeChunk(root, "ab", "abcdef01", 300);
        before.onChunkStored(300L);
        writeChunk(root, "cd", "cdef0123", 120);
        before.onChunkStored(120L);
        writeChunk(root, "ef", "ef012345", 80);
        before.onChunkStored(80L);

        assertThat(before.getUsedBytes()).isEqualTo(500L);

        // --- Restart: brand new instance, in-memory counter is gone -------
        NodeUsageTracker after = new NodeUsageTracker(propsFor(root));
        assertThat(after.getUsedBytes()).isZero();

        after.reconcileOnStartup();

        // The volume outlived the process, so the counter must too.
        assertThat(after.getUsedBytes()).isEqualTo(500L);
        assertThat(after.getChunkCount()).isEqualTo(3L);
        assertThat(after.getLastReconciledAt()).isNotNull();
        assertThat(after.getLastReconcileDurationMs()).isNotNull();
    }

    @Test
    void reconcileRepairsDriftFromOutOfBandChanges(@TempDir Path root) throws IOException {
        NodeUsageTracker tracker = new NodeUsageTracker(propsFor(root));

        writeChunk(root, "ab", "abcdef01", 300);
        tracker.onChunkStored(300L);
        writeChunk(root, "cd", "cdef0123", 200);
        tracker.onChunkStored(200L);
        assertThat(tracker.getUsedBytes()).isEqualTo(500L);

        // Something removed a chunk without telling us — an operator, a
        // volume restore, a crash between the delete and the decrement.
        Files.delete(root.resolve("cd").resolve("cdef0123"));

        tracker.reconcile();

        assertThat(tracker.getUsedBytes()).isEqualTo(300L);
        assertThat(tracker.getChunkCount()).isEqualTo(1L);
    }

    @Test
    void reconcileIgnoresPartialWriteTempFiles(@TempDir Path root) throws IOException {
        NodeUsageTracker tracker = new NodeUsageTracker(propsFor(root));

        writeChunk(root, "ab", "abcdef01", 100);
        writeChunk(root, "ab", "chunk-9999.tmp", 4_096);

        tracker.reconcile();

        // A half-written chunk is not stored data and must not be billed.
        assertThat(tracker.getUsedBytes()).isEqualTo(100L);
        assertThat(tracker.getChunkCount()).isEqualTo(1L);
    }

    @Test
    void reconcileOnMissingRootIsSafeAndReportsZero(@TempDir Path root) {
        StorageNodeProperties props = propsFor(root.resolve("does-not-exist"));
        NodeUsageTracker tracker = new NodeUsageTracker(props);

        tracker.reconcile();

        assertThat(tracker.getUsedBytes()).isZero();
        assertThat(tracker.getChunkCount()).isZero();
        assertThat(tracker.getLastReconciledAt()).isNotNull();
    }

    @Test
    void writesDuringAReconcileAreNotLost(@TempDir Path root) throws IOException {
        NodeUsageTracker tracker = new NodeUsageTracker(propsFor(root));
        writeChunk(root, "ab", "abcdef01", 300);

        // Simulates a chunk landing between the walk and the counter reset:
        // reconcile must fold it in, not discard it.
        tracker.reconcile();
        tracker.onChunkStored(50L);
        writeChunk(root, "cd", "cdef0123", 50);
        tracker.reconcile();

        assertThat(tracker.getUsedBytes()).isEqualTo(350L);
        assertThat(tracker.getChunkCount()).isEqualTo(2L);
    }
}
