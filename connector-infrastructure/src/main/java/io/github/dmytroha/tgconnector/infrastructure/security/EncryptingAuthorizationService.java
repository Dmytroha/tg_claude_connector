package io.github.dmytroha.tgconnector.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Stores issued tokens (authorization codes, access and refresh tokens) encrypted, so a copy of the database
 * does not hand out working tokens. Everything else is the stock {@link JdbcOAuth2AuthorizationService}:
 * token values are encrypted on the way into SQL and decrypted when rows are read back.
 */
class EncryptingAuthorizationService extends JdbcOAuth2AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(EncryptingAuthorizationService.class);

    private static final List<String> TOKEN_COLUMNS = List.of("authorization_code_value", "access_token_value",
            "oidc_id_token_value", "refresh_token_value", "user_code_value", "device_code_value");
    private static final List<Class<? extends OAuth2Token>> TOKEN_TYPES = List.of(OAuth2AuthorizationCode.class,
            OAuth2AccessToken.class, OidcIdToken.class, OAuth2RefreshToken.class, OAuth2UserCode.class,
            OAuth2DeviceCode.class);

    private final SecretCipher cipher;

    EncryptingAuthorizationService(JdbcOperations jdbc, RegisteredClientRepository clients, SecretCipher cipher) {
        super(jdbc, clients);
        this.cipher = cipher;
        var rows = getAuthorizationRowMapper();
        var parameters = getAuthorizationParametersMapper();
        setAuthorizationRowMapper((rs, rowNum) -> rows.mapRow(decrypting(rs), rowNum));
        setAuthorizationParametersMapper(authorization -> encryptTokens(authorization, parameters.apply(authorization)));
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return super.findByToken(cipher.encrypt(token), tokenType);
    }

    /**
     * Brings stored tokens in line with the current encryption key. Safe to run on every start.
     */
    void prepareStoredTokens() {
        removeUnreadableTokens();
        encryptStoredTokens();
    }

    /**
     * Deletes authorizations whose tokens were encrypted with another key: changing the encryption key revokes all
     * issued tokens, and Claude signs in again.
     */
    int removeUnreadableTokens() {
        var removed = 0;
        for (var row : getJdbcOperations().queryForList("SELECT id, " + String.join(", ", TOKEN_COLUMNS)
                + " FROM oauth2_authorization")) {
            var unreadable = TOKEN_COLUMNS.stream()
                    .map(column -> asString(row.get(column)))
                    .anyMatch(value -> value != null && !cipher.canDecrypt(value));
            if (unreadable) {
                removed += getJdbcOperations().update("DELETE FROM oauth2_authorization WHERE id = ?", row.get("id"));
            }
        }
        if (removed > 0) {
            log.warn("Removed {} authorization(s) whose tokens cannot be decrypted with the current encryption key; "
                    + "clients have to sign in again", removed);
        }
        return removed;
    }

    /**
     * Encrypts token values stored before encryption was enabled.
     */
    int encryptStoredTokens() {
        if (!cipher.enabled()) {
            return 0;
        }
        var updated = 0;
        for (var column : TOKEN_COLUMNS) {
            var plaintext = getJdbcOperations().queryForList(
                    "SELECT id, " + column + " AS token FROM oauth2_authorization WHERE " + column
                            + " IS NOT NULL AND " + column + " NOT LIKE '" + SecretCipher.PREFIX + "%'");
            for (var row : plaintext) {
                updated += getJdbcOperations().update("UPDATE oauth2_authorization SET " + column + " = ? WHERE id = ?",
                        cipher.encrypt(asString(row.get("token"))), row.get("id"));
            }
        }
        if (updated > 0) {
            log.info("Encrypted {} stored token(s)", updated);
        }
        return updated;
    }

    private List<SqlParameterValue> encryptTokens(OAuth2Authorization authorization, List<SqlParameterValue> parameters) {
        var tokens = new HashSet<String>();
        TOKEN_TYPES.stream()
                .map(authorization::getToken)
                .flatMap(token -> token == null ? Stream.empty() : Stream.of(token.getToken().getTokenValue()))
                .forEach(tokens::add);
        // a mutable list: the update statement moves the id parameter to the end
        var result = new ArrayList<SqlParameterValue>(parameters.size());
        for (var parameter : parameters) {
            result.add(encryptIfToken(parameter, tokens));
        }
        return result;
    }

    private SqlParameterValue encryptIfToken(SqlParameterValue parameter, Set<String> tokens) {
        var value = parameter.getValue();
        if (value instanceof String text && tokens.contains(text)) {
            return new SqlParameterValue(parameter.getSqlType(), cipher.encrypt(text));
        }
        if (value instanceof byte[] bytes && tokens.contains(new String(bytes, StandardCharsets.UTF_8))) {
            var encrypted = cipher.encrypt(new String(bytes, StandardCharsets.UTF_8));
            return new SqlParameterValue(parameter.getSqlType(), encrypted.getBytes(StandardCharsets.UTF_8));
        }
        return parameter;
    }

    /**
     * The stock row mapper reads token columns as strings (or bytes for BLOB columns); decrypt them on the way.
     */
    private ResultSet decrypting(ResultSet rs) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(rs, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (args == null || args.length != 1 || !(args[0] instanceof String column)
                            || !TOKEN_COLUMNS.contains(column.toLowerCase(Locale.ROOT))) {
                        return result;
                    }
                    if (result instanceof String text) {
                        return cipher.decrypt(text);
                    }
                    if (result instanceof byte[] bytes && "getBytes".equals(method.getName())) {
                        return cipher.decrypt(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                    }
                    return result;
                });
    }

    private static String asString(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : (String) value;
    }
}
