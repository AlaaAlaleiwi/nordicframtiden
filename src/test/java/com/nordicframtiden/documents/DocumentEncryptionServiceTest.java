package com.nordicframtiden.documents;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DocumentEncryptionServiceTest {

    private DocumentEncryptionService serviceWithKey(String key) {
        return new DocumentEncryptionService(key);
    }

    @Test
    void roundTrip() {
        DocumentEncryptionService svc = serviceWithKey("test-key-0123456789abcdef");
        byte[] plaintext = "sensitive PDF bytes \u00e5\u00e4\u00f6".getBytes();
        DocumentEncryptionService.Sealed sealed = svc.encrypt(plaintext);

        assertArrayNotEquals(plaintext, sealed.ciphertext(), "ciphertext must differ from plaintext");
        assertEquals(12, sealed.iv().length, "IV must be 12 bytes");

        byte[] decrypted = svc.decrypt(sealed.iv(), sealed.ciphertext());
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void uniqueIvPerDocument() {
        DocumentEncryptionService svc = serviceWithKey("test-key-0123459abcdef");
        byte[] plaintext = "same plaintext".getBytes();
        DocumentEncryptionService.Sealed a = svc.encrypt(plaintext);
        DocumentEncryptionService.Sealed b = svc.encrypt(plaintext);
        assertFalse(Arrays.equals(a.iv(), b.iv()), "IVs must differ between documents");
        assertFalse(Arrays.equals(a.ciphertext(), b.ciphertext()), "same plaintext must encrypt differently");
    }

    @Test
    void tamperedCiphertextFails() {
        DocumentEncryptionService svc = serviceWithKey("test-key-0123456789abcdef");
        DocumentEncryptionService.Sealed sealed = svc.encrypt("top secret".getBytes());
        byte[] tampered = sealed.ciphertext().clone();
        tampered[0] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> svc.decrypt(sealed.iv(), tampered));
    }

    @Test
    void wrongKeyFails() {
        DocumentEncryptionService svcA = serviceWithKey("key-A-0123456789abcdef");
        DocumentEncryptionService svcB = serviceWithKey("key-B-0123456789abcdef");
        DocumentEncryptionService.Sealed sealed = svcA.encrypt("top secret".getBytes());
        assertThrows(IllegalStateException.class, () -> svcB.decrypt(sealed.iv(), sealed.ciphertext()));
    }

    @Test
    void rejectsShortKey() {
        assertThrows(IllegalStateException.class, () -> serviceWithKey("short"));
    }

    private static void assertArrayNotEquals(byte[] unexpected, byte[] actual, String message) {
        assertFalse(Arrays.equals(unexpected, actual), message);
    }
}
