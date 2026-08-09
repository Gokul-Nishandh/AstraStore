package com.astrastore.download.controller;

import com.astrastore.download.dto.DownloadPayload;
import com.astrastore.download.service.DownloadOrchestrator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DownloadController {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String CHECKSUM_HEADER = "X-Checksum-SHA256";

    private final DownloadOrchestrator downloadOrchestrator;

    @GetMapping("/api/v1/buckets/{bucketId}/objects/{key}")
    public void downloadByBucketAndKey(
            @PathVariable UUID bucketId,
            @PathVariable String key,
            HttpServletResponse response) throws IOException {

        log.info("Download by bucket + key — bucketId={}, key={}", bucketId, key);
        stream(downloadOrchestrator.prepareByBucketAndKey(bucketId, key), response);
    }

    @GetMapping("/api/v1/objects/{objectId}")
    public void downloadById(
            @PathVariable UUID objectId,
            HttpServletResponse response) throws IOException {

        log.info("Download by object id — objectId={}", objectId);
        stream(downloadOrchestrator.prepare(objectId), response);
    }

    @RequestMapping(value = "/api/v1/objects/{objectId}", method = RequestMethod.HEAD)
    public void head(
            @PathVariable UUID objectId,
            HttpServletResponse response) {

        log.info("HEAD object — objectId={}", objectId);
        applyHeaders(downloadOrchestrator.prepare(objectId), response);
    }

    private void stream(DownloadPayload payload, HttpServletResponse response) throws IOException {
        applyHeaders(payload, response);
        downloadOrchestrator.writeBody(payload, response.getOutputStream());
    }

    private void applyHeaders(DownloadPayload payload, HttpServletResponse response) {
        String contentType = payload.contentType() != null ? payload.contentType() : DEFAULT_CONTENT_TYPE;
        response.setContentType(contentType);
        response.setContentLengthLong(payload.sizeBytes());
        response.setHeader(CHECKSUM_HEADER, payload.checksum());
    }
}
