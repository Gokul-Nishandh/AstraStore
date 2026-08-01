package com.astrastore.shared.strategy;

import com.astrastore.shared.manifest.ChunkManifest;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for streaming chunk data to a storage node.
 * Abstracts the HTTP communication details from the core upload logic.
 */
public interface StorageStreamClient {

    /**
     * Opens a connection to the specified storage node for writing.
     *
     * @param nodeAddress the full URL of the storage node (e.g., "http://localhost:8088")
     * @param chunkId     the unique identifier for this chunk
     * @throws IOException if the connection cannot be established
     */
    void openStream(String nodeAddress, String chunkId) throws IOException;

    /**
     * Writes a buffer of data to the open stream.
     *
     * @param buffer the byte array to write
     * @param offset the starting offset in the buffer
     * @param length the number of bytes to write
     * @throws IOException if writing fails
     */
    void write(byte[] buffer, int offset, int length) throws IOException;

    /**
     * Finalizes the stream and returns the chunk manifest from the storage node.
     *
     * @return the ChunkManifest containing the chunk metadata
     * @throws IOException if the finalize fails or the server returns an error
     */
    ChunkManifest finalizeStream() throws IOException;
}
