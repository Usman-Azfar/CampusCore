package com.cms.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (built into the JDK, no extra dependency).
 *
 * Stored format: pbkdf2_sha256$&lt;iterations&gt;$&lt;salt, Base64&gt;$&lt;hash, Base64&gt;
 * Each password gets its own random salt, and the iteration count is stored with the hash,
 * so it can be raised later without breaking existing accounts (see {@link #needsRehash}).
 *
 * Generate a hash from the command line (e.g. for seed data):
 *   java -cp target/classes com.cms.util.PasswordHasher &lt;password&gt;
 */
public final class PasswordHasher {

    public static final String PREFIX = "pbkdf2_sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** True when the password matches the stored hash. Anything not in the hash format never matches. */
    public static boolean verify(String password, String stored) {
        if (password == null || !isHash(stored))
            return false;
        String[] parts = stored.split("\\$");
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual); // constant-time comparison
        } catch (IllegalArgumentException e) {
            return false; // malformed number or Base64
        }
    }

    /** True for values produced by {@link #hash}; false for legacy plain-text passwords. */
    public static boolean isHash(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$") && stored.split("\\$").length == 4;
    }

    /** True when the stored hash uses fewer iterations than the current setting. */
    public static boolean needsRehash(String stored) {
        if (!isHash(stored))
            return true;
        try {
            return Integer.parseInt(stored.split("\\$")[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/classes com.cms.util.PasswordHasher <password>");
            System.exit(1);
        }
        System.out.println(hash(args[0]));
    }
}
