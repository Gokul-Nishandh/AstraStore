package com.astrastore.upload.controller;

import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.upload.dto.UploadResponse;
import com.astrastore.upload.service.ZeroMemoryEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final ZeroMemoryEngine zeroMemoryEngine;
    private final ObjectMapper objectMapper;

    public UploadController(ZeroMemoryEngine zeroMemoryEngine, ObjectMapper objectMapper) {
        this.zeroMemoryEngine = zeroMemoryEngine;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Upload request — filename={}, size={}", file.getOriginalFilename(), file.getSize());

        ObjectManifest manifest = zeroMemoryEngine.process(file.getInputStream());

        log.info("Upload complete — chunks={}, globalHash={}",
                manifest.chunks().size(), manifest.globalHash());

        return ResponseEntity.ok(UploadResponse.fromManifest(manifest));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
