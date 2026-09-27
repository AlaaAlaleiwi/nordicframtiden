package com.nordicframtiden.documents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for profile documents at rest.
 *
 * The key is {@code APP_DOCUMENT_KEY} (env). Any string of 16+ chars works:
 * it is stretched through SHA-256, so rotating the value makes existing
 * ciphertext unreadable — treat it like a password and never change it
 * without re-encrypting. Each document gets a fresh 12-byte IV; the auth
 * tag detects any tampering (decryption fails loudly).
 */
@Service
public class DocumentEncryptionService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public DocumentEncryptionService(@Value("${app.document-key:${APP_DOCUMENT_KEY:}}") String keyMaterial) {
        if (keyMaterial == null || keyMaterial.length() < 16) {
            throw new IllegalStateException(
                "APP_DOCUMENT_KEY must be set to a long random secret (16+ characters).");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] derived = digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize document encryption", e);
        }
    }

    /** Encrypts plaintext into IV+ciphertext bytes ready for storage. */
    public Sealed encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new Sealed(iv, ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Document encryption failed", e);
        }
    }

    /** Decrypts stored IV+ciphertext; throws when tampered or wrong key. */
    public byte[] decrypt(byte[] iv, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Document decryption failed (wrong key or corrupted data)", e);
        }
    }

    public record Sealed(byte[] iv, byte[] ciphertext) {}
}
