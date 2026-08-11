package com.astrastore.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadResultDto {
    private UUID objectId;
    private UUID bucketId;
    private String key;
    private Long sizeBytes;
    private String checksum;
    private Integer chunkCount;
    private String status;

    public UploadResultDto() {}

    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }

    public UUID getBucketId() { return bucketId; }
    public void setBucketId(UUID bucketId) { this.bucketId = bucketId; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
