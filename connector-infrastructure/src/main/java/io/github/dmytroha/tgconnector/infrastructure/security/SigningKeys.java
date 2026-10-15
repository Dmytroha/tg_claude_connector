package io.github.dmytroha.tgconnector.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.text.ParseException;
import java.util.UUID;

/**
 * Where the RSA key that signs access tokens comes from.
 */
interface SigningKeys {

    RSAKey current();

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
    }

    /** Key kept in PostgreSQL, so issued tokens survive redeploys. Generated on first start. */
    final class Jdbc implements SigningKeys {

        private final JdbcClient jdbc;
        private volatile RSAKey key;

        Jdbc(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public RSAKey current() {
            var cached = key;
            if (cached == null) {
                synchronized (this) {
                    if (key == null) {
                        key = loadOrCreate();
                    }
                    cached = key;
                }
            }
            return cached;
        }

        private RSAKey loadOrCreate() {
            var created = generate();
            // first writer wins if several instances start at once
            jdbc.sql("INSERT INTO oauth2_signing_key (id, jwk) VALUES ('current', :jwk) ON CONFLICT (id) DO NOTHING")
                    .param("jwk", created.toJSONString())
                    .update();
            var stored = jdbc.sql("SELECT jwk FROM oauth2_signing_key WHERE id = 'current'").query(String.class).single();
            try {
                return RSAKey.parse(stored);
            } catch (ParseException e) {
                throw new IllegalStateException("Stored signing key is not a valid JWK", e);
            }
        }
    }
}
