package com.astrastore.download.verify;

import com.astrastore.download.exception.ChecksumVerificationException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ChecksumVerifier {

    public String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void verifyChunk(byte[] data, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            throw new ChecksumVerificationException("Missing expected checksum for chunk");
        }
        String computed = sha256(data);
        if (!computed.equalsIgnoreCase(expectedChecksum)) {
            throw new ChecksumVerificationException(
                    "Chunk checksum mismatch — expected=" + expectedChecksum + ", computed=" + computed);
        }
    }

    public boolean objectDigestMatches(String computedDigest, String expectedChecksum) {
        return expectedChecksum != null
                && computedDigest != null
                && computedDigest.equalsIgnoreCase(expectedChecksum);
    }
}
