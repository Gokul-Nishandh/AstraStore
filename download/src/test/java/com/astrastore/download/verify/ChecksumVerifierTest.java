package com.astrastore.download.verify;

import com.astrastore.download.exception.ChecksumVerificationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChecksumVerifierTest {

    private final ChecksumVerifier verifier = new ChecksumVerifier();

    @Test
    void sha256_knownVector() {
        assertThat(verifier.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256_emptyInput() {
        assertThat(verifier.sha256(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void verifyChunk_acceptsMatchingChecksumCaseInsensitively() {
        byte[] data = "chunk-bytes".getBytes(StandardCharsets.UTF_8);
        String checksum = verifier.sha256(data);

        verifier.verifyChunk(data, checksum.toUpperCase());
    }

    @Test
    void verifyChunk_rejectsMismatch() {
        byte[] data = "chunk-bytes".getBytes(StandardCharsets.UTF_8);
        String wrong = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThatThrownBy(() -> verifier.verifyChunk(data, wrong))
                .isInstanceOf(ChecksumVerificationException.class)
                .hasMessageContaining("mismatch");
    }

    @Test
    void verifyChunk_rejectsBlankExpected() {
        assertThatThrownBy(() -> verifier.verifyChunk(new byte[]{1, 2, 3}, " "))
                .isInstanceOf(ChecksumVerificationException.class)
                .hasMessageContaining("Missing expected checksum");
    }

    @Test
    void objectDigestMatches_comparesCaseInsensitively() {
        assertThat(verifier.objectDigestMatches("abcdEF12", "ABCDef12")).isTrue();
        assertThat(verifier.objectDigestMatches("abcdEF12", "00000000")).isFalse();
        assertThat(verifier.objectDigestMatches(null, "00000000")).isFalse();
        assertThat(verifier.objectDigestMatches("abcdEF12", null)).isFalse();
    }
}
