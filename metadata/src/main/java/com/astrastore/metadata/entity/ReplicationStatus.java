package com.astrastore.metadata.entity;

public enum ReplicationStatus {
    PENDING,
    REPLICATING,
    REPLICATED,
    UNDER_REPLICATED,
    REPAIRING,
    FAILED,
    COMPLETE
}

