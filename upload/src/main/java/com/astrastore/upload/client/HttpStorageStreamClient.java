package com.astrastore.upload.client;

import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.shared.strategy.StorageStreamClient;
import com.astrastore.upload.exception.ChunkWriteException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP client for streaming chunk data to storage nodes.
 * Implements StorageStreamClient using chunked transfer encoding.
 * This class is stateful and should be instantiated per upload operation.
 */
public class HttpStorageStreamClient implements StorageStreamClient {

    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final ObjectMapper objectMapper;

    private URL url;
    private HttpURLConnection connection;
    private OutputStream outputStream;

    public HttpStorageStreamClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void openStream(String nodeAddress, String chunkId) throws IOException {
        String endpoint = nodeAddress + "/api/v1/chunks/" + chunkId;

        try {
            url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setChunkedStreamingMode(BUFFER_SIZE);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            outputStream = connection.getOutputStream();
        } catch (IOException e) {
            throw new ChunkWriteException("Failed to open chunk stream to node " + nodeAddress + " — " + e.getMessage(), e);
        }
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        try {
            outputStream.write(buffer, offset, length);
        } catch (IOException e) {
            throw new ChunkWriteException("Failed to write chunk bytes to node " + url + " — " + e.getMessage(), e);
        }
    }

    @Override
    public ChunkManifest finalizeStream() throws IOException {
        try {
            outputStream.flush();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            if (responseCode != 201) {
                throw new ChunkWriteException("Storage node returned non-201: " + responseCode);
            }

            String response = new String(connection.getInputStream().readAllBytes());

            JsonNode node = objectMapper.readTree(response);
            return ChunkManifest.builder()
                    .chunkId(node.get("chunkId").asText())
                    .checksum(node.get("checksum").asText())
                    .sizeBytes(node.get("sizeBytes").asLong())
                    .nodeIp(url.getProtocol() + "://" + url.getHost() + ":" + url.getPort())
                    .build();

        } catch (IOException e) {
            throw new ChunkWriteException("Storage node chunk write failed — " + e.getMessage(), e);
        } finally {
            disconnect();
        }
    }

    private void disconnect() {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
        outputStream = null;
    }
}
