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
        return new AllowedGitHubUserService(request -> new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES,
                Map.of("login", login, "id", 1), "login"), allowed);
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
}
