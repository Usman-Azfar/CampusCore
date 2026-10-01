package com.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.util.PasswordHasher;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void correctPasswordVerifies() {
        String hash = PasswordHasher.hash("Secret123");
        assertTrue(PasswordHasher.verify("Secret123", hash));
    }

    @Test
    void wrongPasswordFails() {
        String hash = PasswordHasher.hash("Secret123");
        assertFalse(PasswordHasher.verify("secret123", hash));
        assertFalse(PasswordHasher.verify("", hash));
        assertFalse(PasswordHasher.verify(null, hash));
    }

    @Test
    void samePasswordGetsDifferentSalts() {
        assertNotEquals(PasswordHasher.hash("Secret123"), PasswordHasher.hash("Secret123"));
    }

    @Test
    void hashFormat() {
        String[] parts = PasswordHasher.hash("Secret123").split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2_sha256", parts[0]);
        assertFalse(PasswordHasher.needsRehash(PasswordHasher.hash("Secret123")));
    }

    @Test
    void plainTextAndMalformedValuesNeverMatch() {
        assertFalse(PasswordHasher.verify("Secret123", "Secret123")); // legacy plain text
        assertFalse(PasswordHasher.isHash("Secret123"));
        assertTrue(PasswordHasher.needsRehash("Secret123"));
        assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$abc$!!!$!!!"));
        assertFalse(PasswordHasher.verify("x", null));
    }

    @Test
    void lowerIterationCountNeedsRehash() {
        assertTrue(PasswordHasher.needsRehash("pbkdf2_sha256$1000$AAAA$AAAA"));
    }
}
