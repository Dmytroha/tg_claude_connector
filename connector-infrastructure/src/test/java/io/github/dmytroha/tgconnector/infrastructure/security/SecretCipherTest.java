package io.github.dmytroha.tgconnector.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef-test";

    private final SecretCipher cipher = SecretCipher.fromSecret(KEY);

    @Test
    void encryptsDeterministicallyAndDecrypts() {
        var encrypted = cipher.encrypt("refresh-token-value");

        assertThat(encrypted).startsWith(SecretCipher.PREFIX).doesNotContain("refresh-token-value");
        assertThat(cipher.encrypt("refresh-token-value")).as("same input, same output: tokens are looked up by it")
                .isEqualTo(encrypted);
        assertThat(cipher.encrypt("other")).isNotEqualTo(encrypted);
        assertThat(cipher.decrypt(encrypted)).isEqualTo("refresh-token-value");
        assertThat(cipher.encrypt(encrypted)).as("never encrypts twice").isEqualTo(encrypted);
    }

    @Test
    void passesPlaintextFromBeforeEncryptionThrough() {
        assertThat(cipher.decrypt("legacy-plaintext")).isEqualTo("legacy-plaintext");
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(SecretCipher.disabled().encrypt("value")).isEqualTo("value");
    }

    @Test
    void failsClearlyWithAWrongOrMissingKey() {
        var encrypted = cipher.encrypt("secret");

        assertThatThrownBy(() -> SecretCipher.fromSecret("another-key-another-key-another-key").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("differs");
        assertThatThrownBy(() -> SecretCipher.disabled().decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not set");
        assertThat(cipher.canDecrypt(encrypted)).isTrue();
        assertThat(cipher.canDecrypt("legacy-plaintext")).isTrue();
        assertThat(SecretCipher.fromSecret("another-key-another-key-another-key").canDecrypt(encrypted)).isFalse();
        assertThat(SecretCipher.disabled().canDecrypt(encrypted)).isFalse();
        assertThatThrownBy(() -> SecretCipher.fromSecret("too-short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
