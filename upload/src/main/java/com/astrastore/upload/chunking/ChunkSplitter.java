package com.astrastore.upload.chunking;

import com.astrastore.upload.model.ChunkDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChunkSplitter {

    @Value("${upload.chunk-size-bytes:8388608}")
    private int chunkSize = 8388608;

    private final ChecksumCalculator checksumCalculator;

    public List<ChunkDescriptor> split(InputStream inputStream) throws IOException {
        List<ChunkDescriptor> chunks = new ArrayList<>();
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream();
        int chunkIndex = 0;

        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            chunkBuffer.write(buffer, 0, bytesRead);
            if (chunkBuffer.size() >= chunkSize) {
                byte[] chunkBytes = chunkBuffer.toByteArray();
                String checksum = checksumCalculator.calculateSha256(chunkBytes);
                chunks.add(new ChunkDescriptor(chunkIndex++, chunkBytes, checksum));
                chunkBuffer.reset();
            }
        }

        if (chunkBuffer.size() > 0) {
            byte[] chunkBytes = chunkBuffer.toByteArray();
            String checksum = checksumCalculator.calculateSha256(chunkBytes);
            chunks.add(new ChunkDescriptor(chunkIndex, chunkBytes, checksum));
        }

        return chunks;
    }
}
