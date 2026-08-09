package com.astrastore.upload.chunking;

import com.astrastore.upload.model.ChunkDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkSplitterTest {

    private static final int CHUNK_SIZE = 25_000;

    private final ChecksumCalculator checksumCalculator = new ChecksumCalculator();
    private ChunkSplitter chunkSplitter;

    @BeforeEach
    void setUp() {
        chunkSplitter = new ChunkSplitter(checksumCalculator);
        ReflectionTestUtils.setField(chunkSplitter, "chunkSize", CHUNK_SIZE);
    }

    @Test
    void split_singleChunkForInputBelowThreshold() throws IOException {
        byte[] input = filled(100);

        List<ChunkDescriptor> chunks = chunkSplitter.split(new ByteArrayInputStream(input));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).bytes()).isEqualTo(input);
    }

    @Test
    void split_splitsIntoMultipleChunksAcrossThreshold() throws IOException {
        byte[] input = filled(50_000);

        List<ChunkDescriptor> chunks = chunkSplitter.split(new ByteArrayInputStream(input));

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.bytes().length > 0);
        assertThat(chunks).allMatch(chunk -> chunk.checksum()
                .equals(checksumCalculator.calculateSha256(chunk.bytes())));

        List<Integer> expectedIndices = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            expectedIndices.add(i);
        }
        assertThat(chunks).extracting(ChunkDescriptor::index).containsExactlyElementsOf(expectedIndices);
        assertThat(chunks.stream().mapToLong(chunk -> chunk.bytes().length).sum()).isEqualTo(input.length);
    }

    @Test
    void split_exactThresholdProducesNoEmptyTrailingChunk() throws IOException {
        byte[] input = filled(CHUNK_SIZE);

        List<ChunkDescriptor> chunks = chunkSplitter.split(new ByteArrayInputStream(input));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allMatch(chunk -> chunk.bytes().length > 0);
        assertThat(chunks.stream().mapToLong(chunk -> chunk.bytes().length).sum()).isEqualTo(input.length);
    }

    @Test
    void split_emptyStreamProducesNoChunks() throws IOException {
        assertThat(chunkSplitter.split(new ByteArrayInputStream(new byte[0]))).isEmpty();
    }

    @Test
    void split_reassembledChunksEqualOriginalInput() throws IOException {
        byte[] input = filled(50_000);

        List<ChunkDescriptor> chunks = chunkSplitter.split(new ByteArrayInputStream(input));

        byte[] reassembled = new byte[input.length];
        int offset = 0;
        for (ChunkDescriptor chunk : chunks) {
            System.arraycopy(chunk.bytes(), 0, reassembled, offset, chunk.bytes().length);
            offset += chunk.bytes().length;
        }
        assertThat(reassembled).isEqualTo(input);
    }

    private byte[] filled(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 0x41);
        return bytes;
    }
}
