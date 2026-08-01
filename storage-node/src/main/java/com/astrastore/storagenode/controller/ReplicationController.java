package com.astrastore.storagenode.controller;

import com.astrastore.shared.events.ReplicationCommand;
import com.astrastore.storagenode.dto.ChunkResponse;
import com.astrastore.storagenode.service.HashService;
import com.astrastore.storagenode.service.NodeToNodeClient;
import com.astrastore.storagenode.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * REST controller for replication operations.
 * Handles push replication commands from the Replication Service.
 */
@RestController
@RequestMapping("/api/v1/replication")
@RequiredArgsConstructor
@Slf4j
public class ReplicationController {

    private final NodeToNodeClient nodeToNodeClient;
    private final HashService hashService;
    private final StorageConfig storageConfig;

    /**
     * Pushes a chunk from this node to a target node.
     * Called by the Replication Service to initiate P2P replication.
     *
     * @param command the replication command containing chunkId and target node
     * @return 200 OK if replication succeeded and hash matches, 500 otherwise
     */
    @PostMapping("/push")
    public ResponseEntity<Void> pushChunk(@RequestBody ReplicationCommand command) {
        String chunkId = command.chunkId();
        String targetNodeIp = command.targetNodeIp();

        log.info("Replication push received — chunkId={}, target={}", chunkId, targetNodeIp);

        try {
            ChunkResponse targetResponse = nodeToNodeClient.streamChunk(chunkId, targetNodeIp);

            Path localPath = storageConfig.getFinalPath(chunkId);
            String localChecksum;
            try (InputStream fis = Files.newInputStream(localPath)) {
                localChecksum = hashService.computeSha256(fis);
            }

            if (!hashService.verifyHash(targetResponse.getChecksum(), localChecksum)) {
                log.error("Replication checksum mismatch — chunkId={}, local={}, remote={}",
                        chunkId, localChecksum, targetResponse.getChecksum());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            log.info("Replication push complete — chunkId={}, target={}", chunkId, targetNodeIp);
            return ResponseEntity.ok().build();

        } catch (IOException e) {
            log.error("Replication push failed — chunkId={}, target={}", chunkId, targetNodeIp, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
