package com.astrastore.replication.metadata;

/**
 * Client interface for updating metadata about replicated chunks.
 * Used by the Replication Service to record replica locations.
 */
public interface MetadataClient {

    /**
     * Records that a chunk has been replicated to a storage node.
     *
     * @param chunkId  the unique identifier of the chunk
     * @param nodeIp   the IP:port of the node where the replica was stored
     */
    void addReplicaLocation(String chunkId, String nodeIp);
}
