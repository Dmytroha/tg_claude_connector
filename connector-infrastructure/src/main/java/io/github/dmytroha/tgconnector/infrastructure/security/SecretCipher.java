package io.github.dmytroha.tgconnector.infrastructure.security;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts secrets stored in PostgreSQL (the token signing key, issued tokens) with AES-256-GCM, using a key
 * derived from {@code connector.security.encryption-key}.
 * <p>
 * Encryption is deterministic: the nonce is an HMAC of the plaintext, so the same token always encrypts to the
 * same value and can still be looked up by it. That only reveals whether two stored values are equal, which is
 * harmless for random tokens.
 * <p>
 * Values without the {@value #PREFIX} prefix are stored in plaintext (from before encryption was enabled) and are
 * returned as they are. Without a key the cipher is disabled and stores plaintext.
 * <p>
 * Changing the key makes everything encrypted with the old one unreadable; the stores then discard it
 * (see {@link #canDecrypt}), which revokes the signing key and all issued tokens.
 */
final class SecretCipher {

    static final String PREFIX = "enc:v1:";
    static final int MIN_KEY_LENGTH = 32;

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec nonceKey;

    private SecretCipher(SecretKeySpec encryptionKey, SecretKeySpec nonceKey) {
        this.encryptionKey = encryptionKey;
        this.nonceKey = nonceKey;
    }

    static SecretCipher disabled() {
        return new SecretCipher(null, null);
    }

    /**
     * @param secret at least {@value #MIN_KEY_LENGTH} characters, e.g. a random password
     */
    static SecretCipher fromSecret(String secret) {
        if (secret == null || secret.strip().length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "connector.security.encryption-key must be at least " + MIN_KEY_LENGTH + " characters");
        }
        var master = secret.strip().getBytes(StandardCharsets.UTF_8);
        return new SecretCipher(new SecretKeySpec(hmac(master, "tg-connector/encryption"), "AES"),
                new SecretKeySpec(hmac(master, "tg-connector/nonce"), "HmacSHA256"));
    }

    boolean enabled() {
        return encryptionKey != null;
    }

    static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    String encrypt(String plaintext) {
        if (!enabled() || plaintext == null || isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            var bytes = plaintext.getBytes(StandardCharsets.UTF_8);
            var nonce = Arrays.copyOf(hmac(nonceKey, bytes), NONCE_BYTES);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            var sealed = cipher.doFinal(bytes);
            var out = ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt a secret", e);
        }
    }

    /**
     * Whether {@link #decrypt} would succeed: plaintext always can, encrypted values only with the key they were
     * encrypted with.
     */
    boolean canDecrypt(String value) {
        if (!isEncrypted(value)) {
            return true;
        }
        try {
            decrypt(value);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        if (!enabled()) {
            throw new IllegalStateException("A stored secret is encrypted, but connector.security.encryption-key "
                    + "(CONNECTOR_ENCRYPTION_KEY) is not set");
        }
        try {
            var in = Base64.getUrlDecoder().decode(value.substring(PREFIX.length()));
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, in, 0, NONCE_BYTES));
            return new String(cipher.doFinal(in, NONCE_BYTES, in.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Cannot decrypt a stored secret: connector.security.encryption-key "
                    + "(CONNECTOR_ENCRYPTION_KEY) differs from the one it was encrypted with", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot decrypt a stored secret", e);
        }
    }

    private static byte[] hmac(byte[] key, String label) {
        return hmac(new SecretKeySpec(key, "HmacSHA256"), label.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(SecretKeySpec key, byte[] data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
