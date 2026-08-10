/**
 * Secure credential storage with AES-256-GCM encryption.
 * Stores API key and user info in ~/.astra/credentials.enc.
 * Uses BouncyCastle for AES-GCM with random IV per encryption.
 * Key derivation: SHA-256 of machine-specific identifier.
 */
package com.astrastore.cli.auth;

import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.exception.CredentialException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Slf4j
public class CredentialStore {

    private static String credentialDir() {
        return System.getProperty("user.home") + "/.astra";
    }

    private static String credentialFile() {
        return credentialDir() + "/credentials.enc";
    }

    private static final int KEY_SIZE_BITS = 256;
    private static final int IV_SIZE_BYTES = 12;
    private static final int MAC_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 100_000;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Credentials {
        private String username;
        private String email;
        private String accessToken;
        private String refreshToken;
        private String apiKey;
        private long expiresAtEpoch;
    }

    private static CredentialStore instance;
    private final ObjectMapper mapper = new ObjectMapper();
    private Credentials credentials;

    public static synchronized CredentialStore getInstance() {
        if (instance == null) instance = new CredentialStore();
        return instance;
    }

    public void save(Credentials credentials) throws Exception {
        this.credentials = credentials;
        byte[] plaintext = mapper.writeValueAsBytes(credentials);
        byte[] key = deriveKey();
        byte[] iv = new byte[IV_SIZE_BYTES];
        new SecureRandom().nextBytes(iv);

        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(true, new AEADParameters(new KeyParameter(key), MAC_TAG_BITS, iv));
        byte[] ciphertext = new byte[cipher.getOutputSize(plaintext.length)];
        int offset = cipher.processBytes(plaintext, 0, plaintext.length, ciphertext, 0);
        offset += cipher.doFinal(ciphertext, offset);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(iv);
        out.write(ciphertext);

        Files.createDirectories(Paths.get(credentialDir()));
        Path target = Paths.get(credentialFile());
        Files.write(target, out.toByteArray());
        java.io.File targetFile = target.toFile();
        targetFile.setReadable(false, false);
        targetFile.setWritable(false, false);
        targetFile.setExecutable(false, false);
        targetFile.setReadable(true, true);
        targetFile.setWritable(true, true);
        log.debug("Credentials saved to {}", target);
    }

    public Credentials load() throws Exception {
        if (credentials != null) return credentials;
        File file = new File(credentialFile());
        if (!file.exists()) return null;

        byte[] allBytes = Files.readAllBytes(file.toPath());
        byte[] iv = new byte[IV_SIZE_BYTES];
        System.arraycopy(allBytes, 0, iv, 0, IV_SIZE_BYTES);
        byte[] ciphertext = new byte[allBytes.length - IV_SIZE_BYTES];
        System.arraycopy(allBytes, IV_SIZE_BYTES, ciphertext, 0, ciphertext.length);

        byte[] key = deriveKey();
        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(false, new AEADParameters(new KeyParameter(key), MAC_TAG_BITS, iv));
        byte[] plaintext = new byte[cipher.getOutputSize(ciphertext.length)];
        int offset = cipher.processBytes(ciphertext, 0, ciphertext.length, plaintext, 0);
        offset += cipher.doFinal(plaintext, offset);

        credentials = mapper.readValue(plaintext, Credentials.class);
        log.debug("Credentials loaded");
        return credentials;
    }

    public void clear() throws Exception {
        File file = new File(credentialFile());
        if (file.exists() && !file.delete()) {
            throw new CredentialException("Failed to delete credentials file");
        }
        credentials = null;
        log.info("Credentials cleared");
    }

    public boolean isLoggedIn() {
        try {
            return load() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public Credentials getCredentials() {
        return credentials;
    }

    private byte[] deriveKey() throws Exception {
        String machineId = System.getProperty("user.name") + "@" + System.getProperty("os.name");
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(machineId.toCharArray(), "astra-salt".getBytes(StandardCharsets.UTF_8),
                PBKDF2_ITERATIONS, KEY_SIZE_BITS);
        byte[] key = factory.generateSecret(spec).getEncoded();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        return sha.digest(key);
    }
}
