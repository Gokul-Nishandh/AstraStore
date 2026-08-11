package com.astrastore.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectDto {
    private UUID id;
    private UUID bucketId;
    private String key;
    private Long sizeBytes;
    private String checksum;
    private String contentType;
    private String status;
    private Instant createdAt;
    private Long chunksReplicated;
    private Long chunksTotal;

    public ObjectDto() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBucketId() { return bucketId; }
    public void setBucketId(UUID bucketId) { this.bucketId = bucketId; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Long getChunksReplicated() { return chunksReplicated; }
    public void setChunksReplicated(Long chunksReplicated) { this.chunksReplicated = chunksReplicated; }

    public Long getChunksTotal() { return chunksTotal; }
    public void setChunksTotal(Long chunksTotal) { this.chunksTotal = chunksTotal; }
}
