/**
 * Coordinates the end-to-end upload flow described in Volume 1, Section 10.1.
 * Delegates to ZeroMemoryEngine which streams chunks to storage nodes and
 * records object metadata + chunk locations via MetadataClient.
 * Returns upload result with objectId, size, checksum, and chunk count.
 */
package com.astrastore.upload.service;

import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.upload.model.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadOrchestrator {

    private final ZeroMemoryEngine zeroMemoryEngine;

    public UploadResult handleUpload(InputStream inputStream, UUID bucketId, String key, String contentType) throws IOException {
        log.info("UploadOrchestrator handling upload — bucketId={}, key={}", bucketId, key);

        ObjectManifest manifest = zeroMemoryEngine.process(inputStream, bucketId, key, contentType);

        UUID objectId = manifest.objectId() != null ? UUID.fromString(manifest.objectId()) : null;

        return new UploadResult(
                objectId,
                bucketId,
                key,
                (long) manifest.chunks().stream().mapToLong(c -> c.sizeBytes()).sum(),
                manifest.globalHash(),
                manifest.chunks().size(),
                Instant.now()
        );
    }
}
