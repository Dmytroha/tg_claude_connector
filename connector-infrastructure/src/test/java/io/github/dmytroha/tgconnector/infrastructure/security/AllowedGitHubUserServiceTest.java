package io.github.dmytroha.tgconnector.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedGitHubUserServiceTest {

    private static AllowedGitHubUserService service(String login, List<String> allowed) {
        return service(login, 1, allowed, List.of());
    }

    private static AllowedGitHubUserService service(String login, int id, List<String> logins, List<Long> ids) {
        return new AllowedGitHubUserService(request -> new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES,
                Map.of("login", login, "id", id), "login"), logins, ids);
    }

    @Test
    void allowsListedLoginIgnoringCase() {
        assertThat(service("Dmytroha", List.of(" dmytroha ")).loadUser(null).getName()).isEqualTo("Dmytroha");
    }

    @Test
    void rejectsOtherLoginsAndEmptyAllowlist() {
        assertThatThrownBy(() -> service("stranger", List.of("dmytroha")).loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class).hasMessageContaining("stranger");
        assertThatThrownBy(() -> service("dmytroha", List.of()).loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void checksIdsInsteadOfLoginsWhenIdsAreConfigured() {
        assertThat(service("renamed", 58972635, List.of("dmytroha"), List.of(58972635L)).loadUser(null).getName())
                .isEqualTo("renamed");
        // someone who took the login after a rename has a different id
        assertThatThrownBy(() -> service("Dmytroha", 42, List.of("dmytroha"), List.of(58972635L)).loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
