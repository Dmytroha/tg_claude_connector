package io.github.dmytroha.tgconnector.infrastructure.security;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lets in only the allowed GitHub accounts: by numeric user id ({@code connector.security.github.allowed-ids}), which
 * never changes, or, when no ids are configured, by login ({@code allowed-logins}). A login can be renamed and then
 * taken by someone else, so ids are preferred.
 */
class AllowedGitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final Set<String> allowedLogins;
    private final Set<Long> allowedIds;

    AllowedGitHubUserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate, List<String> allowedLogins,
                             List<Long> allowedIds) {
        this.delegate = delegate;
        this.allowedLogins = allowedLogins.stream()
                .map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.allowedIds = Set.copyOf(allowedIds);
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        var user = delegate.loadUser(request);
        var login = user.getAttribute("login");
        if (!isAllowed(login, user.getAttribute("id"))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied",
                    "GitHub account '" + login + "' is not allowed to use this connector", null));
        }
        return user;
    }

    private boolean isAllowed(Object login, Object id) {
        if (!allowedIds.isEmpty()) {
            return id instanceof Number number && allowedIds.contains(number.longValue());
        }
        return login instanceof String name && allowedLogins.contains(name.toLowerCase(Locale.ROOT));
    }
}
