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
 * Lets in only the GitHub accounts listed in {@code connector.security.github.allowed-logins}.
 */
class AllowedGitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final Set<String> allowedLogins;

    AllowedGitHubUserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate, List<String> allowedLogins) {
        this.delegate = delegate;
        this.allowedLogins = allowedLogins.stream()
                .map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        var user = delegate.loadUser(request);
        var login = user.getAttribute("login");
        if (!(login instanceof String name) || !allowedLogins.contains(name.toLowerCase(Locale.ROOT))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied",
                    "GitHub account '" + login + "' is not allowed to use this connector", null));
        }
        return user;
    }
}
