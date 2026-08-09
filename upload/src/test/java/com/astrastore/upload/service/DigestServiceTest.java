package com.astrastore.upload.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DigestServiceTest {

    @Test
    void extractHex_matchesSha256OfInput() {
        DigestService digestService = new DigestService();
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        digestService.update(data);

        assertThat(digestService.extractHex())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void incrementalUpdatesEqualSingleUpdate() {
        byte[] whole = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

        DigestService incremental = new DigestService();
        incremental.update("the quick ".getBytes(StandardCharsets.UTF_8));
        incremental.update("brown fox".getBytes(StandardCharsets.UTF_8));

        DigestService single = new DigestService();
        single.update(whole);

        assertThat(incremental.extractHex()).isEqualTo(single.extractHex());
    }

    @Test
    void updateWithOffsetAndLength() {
        byte[] data = "xx-abcdef-zz".getBytes(StandardCharsets.UTF_8);

        DigestService digestService = new DigestService();
        digestService.update(data, 3, 6);

        DigestService expected = new DigestService();
        expected.update("abcdef".getBytes(StandardCharsets.UTF_8));

        assertThat(digestService.extractHex()).isEqualTo(expected.extractHex());
    }

    @Test
    void emptyInputProducesEmptyStringDigest() {
        DigestService digestService = new DigestService();

        assertThat(digestService.extractHex())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
