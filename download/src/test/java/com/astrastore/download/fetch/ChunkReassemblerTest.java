package com.astrastore.download.fetch;

import com.astrastore.download.verify.ChecksumVerifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkReassemblerTest {

    private final ChunkReassembler reassembler = new ChunkReassembler();
    private final ChecksumVerifier verifier = new ChecksumVerifier();

    @Test
    void writesChunksBackToBackInOrder() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageDigest digest = reassembler.newObjectDigest();

        reassembler.write("hello".getBytes(StandardCharsets.UTF_8), digest, out);
        reassembler.write(" ".getBytes(StandardCharsets.UTF_8), digest, out);
        reassembler.write("world".getBytes(StandardCharsets.UTF_8), digest, out);

        assertThat(out.toByteArray()).isEqualTo("hello world".getBytes(StandardCharsets.UTF_8));
        assertThat(reassembler.objectChecksum(digest))
                .isEqualTo(verifier.sha256("hello world".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void emptyReassemblyProducesEmptyStreamAndEmptyDigest() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageDigest digest = reassembler.newObjectDigest();

        assertThat(out.toByteArray()).isEmpty();
        assertThat(reassembler.objectChecksum(digest))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void objectChecksumMatchesFullObjectDigest() throws IOException {
        byte[] chunk0 = "part-one-".getBytes(StandardCharsets.UTF_8);
        byte[] chunk1 = "part-two".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageDigest digest = reassembler.newObjectDigest();
        reassembler.write(chunk0, digest, out);
        reassembler.write(chunk1, digest, out);

        byte[] whole = "part-one-part-two".getBytes(StandardCharsets.UTF_8);
        assertThat(reassembler.objectChecksum(digest)).isEqualTo(verifier.sha256(whole));
    }
}
