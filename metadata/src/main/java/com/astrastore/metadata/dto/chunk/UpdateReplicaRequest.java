package com.astrastore.metadata.dto.chunk;

import com.astrastore.metadata.entity.ReplicationStatus;

public record UpdateReplicaRequest(

        String replicaNodeId,

        ReplicationStatus replicationStatus

) {
}