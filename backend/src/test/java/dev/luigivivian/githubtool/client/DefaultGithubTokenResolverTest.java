package dev.luigivivian.githubtool.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Per-visitor token resolution: anonymous and half-configured contexts must resolve empty. */
class DefaultGithubTokenResolverTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<OAuth2AuthorizedClientService> provider =
            mock(ObjectProvider.class);
    private final OAuth2AuthorizedClientService clients =
            mock(OAuth2AuthorizedClientService.class);

    private DefaultGithubTokenResolver resolver;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        resolver = new DefaultGithubTokenResolver(provider);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static OAuth2AuthenticationToken oauthToken() {
        OAuth2User user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("login", "octocat"), "login");
        return new OAuth2AuthenticationToken(user,
                List.of(new SimpleGrantedAuthority("ROLE_USER")), "github");
    }

    @Test
    void anonymousRequestResolvesEmpty() {
        when(provider.getIfAvailable()).thenReturn(clients);

        assertThat(resolver.currentToken()).isEmpty();
        verify(clients, never()).loadAuthorizedClient(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void nonOauthAuthenticationResolvesEmpty() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("octocat", "secret", List.of()));
        when(provider.getIfAvailable()).thenReturn(clients);

        assertThat(resolver.currentToken()).isEmpty();
    }

    @Test
    void missingAuthorizedClientServiceResolvesEmpty() {
        SecurityContextHolder.getContext().setAuthentication(oauthToken());
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(resolver.currentToken()).isEmpty();
    }

    @Test
    void unknownAuthorizedClientResolvesEmpty() {
        SecurityContextHolder.getContext().setAuthentication(oauthToken());
        when(provider.getIfAvailable()).thenReturn(clients);
        when(clients.loadAuthorizedClient("github", "octocat")).thenReturn(null);

        assertThat(resolver.currentToken()).isEmpty();
    }

    @Test
    void loggedInVisitorResolvesTheirOwnAccessToken() {
        SecurityContextHolder.getContext().setAuthentication(oauthToken());
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "gho_visitor_token",
                Instant.parse("2026-07-25T11:00:00Z"), Instant.parse("2026-07-25T13:00:00Z")));
        when(provider.getIfAvailable()).thenReturn(clients);
        when(clients.loadAuthorizedClient("github", "octocat")).thenReturn(client);

        assertThat(resolver.currentToken()).contains("gho_visitor_token");
    }

    @Test
    void lookupUsesRegistrationIdAndPrincipalNameFromTheToken() {
        SecurityContextHolder.getContext().setAuthentication(oauthToken());
        when(provider.getIfAvailable()).thenReturn(clients);
        when(clients.loadAuthorizedClient("github", "octocat")).thenReturn(null);

        Optional<String> token = resolver.currentToken();

        assertThat(token).isEmpty();
        verify(clients).loadAuthorizedClient("github", "octocat");
    }
}
