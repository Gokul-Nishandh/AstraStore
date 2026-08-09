package com.astrastore.upload.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundaryTrackerTest {

    private static final long CHUNK = BoundaryTracker.CHUNK_SIZE_BYTES;

    @Test
    void addAndCheck_returnsFalseBelowBoundary() {
        BoundaryTracker tracker = new BoundaryTracker();

        assertThat(tracker.addAndCheck(CHUNK - 1)).isFalse();
        assertThat(tracker.getCurrentBytes()).isEqualTo(CHUNK - 1);
    }

    @Test
    void addAndCheck_returnsTrueAtExactBoundary() {
        BoundaryTracker tracker = new BoundaryTracker();

        assertThat(tracker.addAndCheck(CHUNK)).isTrue();
    }

    @Test
    void addAndCheck_returnsTrueWhenExceedingBoundary() {
        BoundaryTracker tracker = new BoundaryTracker();

        assertThat(tracker.addAndCheck(CHUNK + 100)).isTrue();
    }

    @Test
    void getBytesIntoChunk_returnsModuloOfChunkSize() {
        BoundaryTracker tracker = new BoundaryTracker();
        tracker.addAndCheck(CHUNK);
        tracker.addAndCheck(50);

        assertThat(tracker.getBytesIntoChunk()).isEqualTo(50);
    }

    @Test
    void getBytesUntilBoundary_decreasesAsBytesAccumulate() {
        BoundaryTracker tracker = new BoundaryTracker();

        assertThat(tracker.getBytesUntilBoundary()).isEqualTo(CHUNK);
        tracker.addAndCheck(1);
        assertThat(tracker.getBytesUntilBoundary()).isEqualTo(CHUNK - 1);
    }

    @Test
    void getBytesUntilBoundary_wrapsAfterCrossingBoundary() {
        BoundaryTracker tracker = new BoundaryTracker();
        tracker.addAndCheck(CHUNK + 50);

        assertThat(tracker.getBytesUntilBoundary()).isEqualTo(CHUNK - 50);
    }

    @Test
    void reset_zeroesCounter() {
        BoundaryTracker tracker = new BoundaryTracker();
        tracker.addAndCheck(500);

        tracker.reset();

        assertThat(tracker.getCurrentBytes()).isZero();
        assertThat(tracker.getBytesUntilBoundary()).isEqualTo(CHUNK);
    }
}
