package dev.luigivivian.githubtool.client;

import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

/** Uses the logged-in visitor's own GitHub OAuth token; anonymous requests resolve empty. */
@Component
public class DefaultGithubTokenResolver implements GithubTokenResolver {

    private final ObjectProvider<OAuth2AuthorizedClientService> authorizedClients;

    public DefaultGithubTokenResolver(
            ObjectProvider<OAuth2AuthorizedClientService> authorizedClients) {
        this.authorizedClients = authorizedClients;
    }

    @Override
    public Optional<String> currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return Optional.empty();
        }
        OAuth2AuthorizedClientService service = authorizedClients.getIfAvailable();
        if (service == null) {
            return Optional.empty();
        }
        OAuth2AuthorizedClient client = service.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(), token.getName());
        return client != null
                ? Optional.of(client.getAccessToken().getTokenValue())
                : Optional.empty();
    }
}
