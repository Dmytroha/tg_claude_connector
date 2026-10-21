package io.github.dmytroha.tgconnector.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Where the RSA keys for access tokens come from.
 */
interface SigningKeys {

    /** The key new tokens are signed with. */
    RSAKey current();

    /** Keys tokens are accepted from: the current one, plus the previous one while its tokens can still be valid. */
    List<RSAKey> verificationKeys();

    static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot generate RSA signing key", e);
        }
    }

    /** New key on every start: tokens become invalid after a restart. For the "memory" profile and tests. */
    final class InMemory implements SigningKeys {

        private final RSAKey key = generate();

        @Override
        public RSAKey current() {
            return key;
        }

        @Override
        public List<RSAKey> verificationKeys() {
            return List.of(key);
        }
    }

    /**
     * Keys kept in PostgreSQL, so issued tokens survive redeploys. Stored encrypted when an encryption key is set.
     * <p>
     * A new key replaces the current one once it is older than {@code rotationPeriod}. The previous key still
     * verifies tokens for {@code gracePeriod} (the access token lifetime), then it is no longer accepted.
     * Only the two newest keys are kept.
     */
    final class Jdbc implements SigningKeys {

        private static final Logger log = LoggerFactory.getLogger(Jdbc.class);

        private record StoredKey(String id, RSAKey key, Instant createdAt) {
        }

        private final JdbcClient jdbc;
        private final SecretCipher cipher;
        private final Clock clock;
        private final Duration rotationPeriod;
        private final Duration gracePeriod;
        /** Newest first. */
        private volatile List<StoredKey> keys;

        Jdbc(JdbcClient jdbc, SecretCipher cipher, Clock clock, Duration rotationPeriod, Duration gracePeriod) {
            this.jdbc = jdbc;
            this.cipher = cipher;
            this.clock = clock;
            this.rotationPeriod = rotationPeriod;
            this.gracePeriod = gracePeriod;
        }

        @Override
        public RSAKey current() {
            return keys().getFirst().key();
        }

        @Override
        public List<RSAKey> verificationKeys() {
            var all = keys();
            var newest = all.getFirst();
            if (all.size() > 1 && clock.instant().isBefore(newest.createdAt().plus(gracePeriod))) {
                return List.of(newest.key(), all.get(1).key());
            }
            return List.of(newest.key());
        }

        private List<StoredKey> keys() {
            var cached = keys;
            if (cached == null || isDue(cached)) {
                synchronized (this) {
                    if (keys == null || isDue(keys)) {
                        keys = loadAndRotate();
                    }
                    cached = keys;
                }
            }
            return cached;
        }

        private boolean isDue(List<StoredKey> stored) {
            return !clock.instant().isBefore(stored.getFirst().createdAt().plus(rotationPeriod));
        }

        private List<StoredKey> loadAndRotate() {
            var stored = load();
            if (stored.isEmpty() || isDue(stored)) {
                var created = generate();
                jdbc.sql("INSERT INTO oauth2_signing_key (id, jwk, created_at) VALUES (:id, :jwk, :createdAt)")
                        .param("id", created.getKeyID())
                        .param("jwk", cipher.encrypt(created.toJSONString()))
                        .param("createdAt", Timestamp.from(clock.instant()))
                        .update();
                jdbc.sql("""
                                DELETE FROM oauth2_signing_key WHERE id NOT IN
                                    (SELECT id FROM oauth2_signing_key ORDER BY created_at DESC LIMIT 2)""")
                        .update();
                log.info(stored.isEmpty() ? "Created a token signing key" : "Rotated the token signing key");
                stored = load();
            }
            return stored;
        }

        private List<StoredKey> load() {
            record Row(String id, String jwk, Instant createdAt) {
            }
            var rows = jdbc.sql("SELECT id, jwk, created_at FROM oauth2_signing_key ORDER BY created_at DESC")
                    .query((rs, rowNum) -> new Row(rs.getString("id"), rs.getString("jwk"),
                            rs.getTimestamp("created_at").toInstant()))
                    .list();
            for (var row : rows) {
                if (cipher.enabled() && !SecretCipher.isEncrypted(row.jwk())) {
                    // stored before encryption was enabled
                    jdbc.sql("UPDATE oauth2_signing_key SET jwk = :jwk WHERE id = :id")
                            .param("jwk", cipher.encrypt(row.jwk())).param("id", row.id()).update();
                    log.info("Encrypted the stored token signing key {}", row.id());
                }
            }
            return rows.stream()
                    .map(row -> new StoredKey(row.id(), parse(cipher.decrypt(row.jwk())), row.createdAt()))
                    .toList();
        }

        private static RSAKey parse(String jwk) {
            try {
                return RSAKey.parse(jwk);
            } catch (ParseException e) {
                throw new IllegalStateException("Stored signing key is not a valid JWK", e);
            }
        }
    }
}
