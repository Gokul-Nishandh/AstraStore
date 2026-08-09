package com.astrastore.upload.client;

import com.astrastore.shared.manifest.ChunkManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class StorageNodeClient {

    private final ObjectMapper objectMapper;

    public StorageNodeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HttpStorageStreamClient createStreamClient() {
        return new HttpStorageStreamClient(objectMapper);
    }

    public ChunkManifest writeChunk(String nodeAddress, String chunkId, byte[] bytes) throws IOException {
        HttpStorageStreamClient client = createStreamClient();
        client.openStream(nodeAddress, chunkId);
        client.write(bytes, 0, bytes.length);
        return client.finalizeStream();
    }
}
