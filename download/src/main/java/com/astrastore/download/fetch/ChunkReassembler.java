package com.astrastore.download.fetch;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ChunkReassembler {

    public MessageDigest newObjectDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void write(byte[] data, MessageDigest objectDigest, OutputStream out) throws IOException {
        objectDigest.update(data);
        out.write(data);
    }

    public String objectChecksum(MessageDigest objectDigest) {
        return HexFormat.of().formatHex(objectDigest.digest());
    }
}
