package com.astrastore.upload.dto;

import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.shared.manifest.ObjectManifest;

import java.util.List;

public record UploadResponse(
    String globalHash,
    int totalChunks,
    List<ChunkManifest> chunks
) {
    public static UploadResponse fromManifest(ObjectManifest manifest) {
        return new UploadResponse(
            manifest.globalHash(),
            manifest.chunks().size(),
            manifest.chunks()
        );
    }
}
