package com.astrastore.upload.chunking;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumCalculatorTest {

    private final ChecksumCalculator calculator = new ChecksumCalculator();

    @Test
    void calculateSha256_knownVector() {
        assertThat(calculator.calculateSha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void calculateSha256_emptyInput() {
        assertThat(calculator.calculateSha256(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void calculateSha256_offsetAndLengthIgnoresSurroundingBytes() {
        byte[] bytes = "xx-hello-world-zz".getBytes(StandardCharsets.UTF_8);

        String offsetDigest = calculator.calculateSha256(bytes, 3, 11);

        assertThat(offsetDigest).isEqualTo(
                calculator.calculateSha256("hello-world".getBytes(StandardCharsets.UTF_8)));
    }
}
