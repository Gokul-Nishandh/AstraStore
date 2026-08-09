package com.astrastore.upload.chunking;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ChecksumCalculator {

    public String calculateSha256(byte[] bytes) {
        return calculateSha256(bytes, 0, bytes.length);
    }

    public String calculateSha256(byte[] bytes, int offset, int len) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, offset, len);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
