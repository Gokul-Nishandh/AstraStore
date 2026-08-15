package com.astrastore.upload.controller;

import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.upload.dto.UploadResponse;
import com.astrastore.upload.model.UploadResult;
import com.astrastore.upload.service.UploadOrchestrator;
import com.astrastore.upload.service.ZeroMemoryEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final UploadOrchestrator uploadOrchestrator;
    private final ZeroMemoryEngine zeroMemoryEngine;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Upload request — filename={}, size={}", file.getOriginalFilename(), file.getSize());

        ObjectManifest manifest = zeroMemoryEngine.process(file.getInputStream());

        log.info("Upload complete — chunks={}, globalHash={}",
                manifest.chunks().size(), manifest.globalHash());

        return ResponseEntity.ok(UploadResponse.fromManifest(manifest));
    }

    /**
     * Accepts any content type on purpose.
     *
     * <p>This is an object store: the bytes are opaque and the declared type
     * is metadata to be recorded and handed back on download, not a filter.
     * Restricting {@code consumes} to {@code application/octet-stream} made
     * Spring answer 415 for every upload a browser labelled honestly — a PDF,
     * a .docx, a .md — which surfaced to the user as "that file type isn't
     * supported" for types the platform stores perfectly well.
     */
    @PutMapping(value = "/api/v1/buckets/{bucketId}/objects/{*key}")
    public ResponseEntity<UploadResult> putObject(
            @PathVariable UUID bucketId,
            @PathVariable String key,
            @RequestHeader(value = "Content-Type", defaultValue = "application/octet-stream") String contentType,
            jakarta.servlet.http.HttpServletRequest request) throws IOException {

        log.info("PUT Object request — bucketId={}, key={}, contentType={}", bucketId, key, contentType);

        UploadResult result = uploadOrchestrator.handleUpload(
                request.getInputStream(), bucketId, normalizeKey(key), contentType);

        log.info("PUT Object complete — bucketId={}, key={}, chunks={}, globalHash={}",
                bucketId, key, result.chunkCount(), result.checksum());

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    private static String normalizeKey(String key) {
        String normalized = key == null ? "" : key;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}


