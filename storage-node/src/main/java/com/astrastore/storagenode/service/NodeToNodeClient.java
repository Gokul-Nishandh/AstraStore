package com.astrastore.storagenode.service;

import com.astrastore.storagenode.config.StorageConfig;
import com.astrastore.storagenode.dto.ChunkResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client for streaming chunks to other storage nodes during replication.
 * Reads a local chunk file and pushes it to a target storage node via HTTP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NodeToNodeClient {

    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;

    /**
     * Streams a local chunk to a target storage node.
     *
     * @param chunkId      the chunk ID to stream
     * @param targetNodeIp the target node address (e.g., "storage-node-2:8088")
     * @return the ChunkResponse from the target node
     * @throws IOException if the streaming fails
     */
    public ChunkResponse streamChunk(String chunkId, String targetNodeIp) throws IOException {
        Path localPath = storageConfig.getFinalPath(chunkId);

        if (!Files.exists(localPath)) {
            throw new IOException("Chunk not found on disk: " + chunkId);
        }

        String endpoint = targetNodeIp + "/api/v1/chunks/" + chunkId;
        log.info("Starting P2P stream — chunkId={}, target={}", chunkId, targetNodeIp);

        HttpURLConnection connection = null;
        InputStream fileIn = null;

        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setChunkedStreamingMode(BUFFER_SIZE);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            OutputStream out = connection.getOutputStream();
            fileIn = Files.newInputStream(localPath);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalWritten = 0;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalWritten += bytesRead;
            }

            out.flush();
            log.debug("P2P stream sent — chunkId={}, bytes={}", chunkId, totalWritten);

            int responseCode = connection.getResponseCode();
            if (responseCode != 201) {
                throw new IOException("Target node returned non-201: " + responseCode);
            }

            String response = new String(connection.getInputStream().readAllBytes());
            ChunkResponse chunkResponse = objectMapper.readValue(response, ChunkResponse.class);

            log.info("P2P stream complete — chunkId={}, target={}, responseChecksum={}",
                    chunkId, targetNodeIp, chunkResponse.getChecksum());

            return chunkResponse;

        } finally {
            if (fileIn != null) {
                try {
                    fileIn.close();
                } catch (IOException e) {
                    log.warn("Failed to close file input stream", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
