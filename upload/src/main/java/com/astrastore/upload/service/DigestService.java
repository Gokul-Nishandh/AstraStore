package com.astrastore.upload.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes SHA-256 checksums.
 * Thread-safe and designed to be instantiated per upload operation.
 */
public class DigestService {

    private static final String ALGORITHM = "SHA-256";

    private final MessageDigest digest;

    /**
     * Creates a new DigestService and initializes the SHA-256 digest.
     */
    public DigestService() {
        try {
            digest = MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Updates the digest with a portion of data.
     */
    public void update(byte[] buffer, int offset, int length) {
        digest.update(buffer, offset, length);
    }

    /**
     * Updates the digest with an entire byte array.
     */
    public void update(byte[] data) {
        digest.update(data);
    }

    /**
     * Extracts the final hash as a lowercase hexadecimal string.
     */
    public String extractHex() {
        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) {
                hex.append('0');
            }
            hex.append(h);
        }
        return hex.toString();
    }
}
