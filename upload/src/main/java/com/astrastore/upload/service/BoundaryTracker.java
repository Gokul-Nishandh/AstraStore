package com.astrastore.upload.service;

/**
 * Tracks byte count and detects when an 8MB chunk boundary is reached.
 * Designed to be instantiated per upload operation.
 */
public class BoundaryTracker {

    public static final long CHUNK_SIZE_BYTES = 8 * 1024 * 1024;

    private long currentBytes;

    /**
     * Creates a new BoundaryTracker with counter reset to zero.
     */
    public BoundaryTracker() {
        this.currentBytes = 0;
    }

    /**
     * Adds bytes to the counter and returns true if boundary is hit.
     *
     * @param addedBytes the number of bytes to add
     * @return true if the 8MB boundary has been reached or exceeded
     */
    public boolean addAndCheck(long addedBytes) {
        currentBytes += addedBytes;
        return currentBytes >= CHUNK_SIZE_BYTES;
    }

    /**
     * Returns the number of bytes in the current chunk.
     */
    public long getCurrentBytes() {
        return currentBytes;
    }

    /**
     * Returns the number of bytes into the current chunk (0 to 8MB-1).
     */
    public long getBytesIntoChunk() {
        return currentBytes % CHUNK_SIZE_BYTES;
    }

    /**
     * Returns the remaining bytes until the next boundary.
     */
    public long getBytesUntilBoundary() {
        return Math.max(0, CHUNK_SIZE_BYTES - (currentBytes % CHUNK_SIZE_BYTES));
    }

    /**
     * Resets the counter to zero for a new chunk.
     */
    public void reset() {
        currentBytes = 0;
    }
}
