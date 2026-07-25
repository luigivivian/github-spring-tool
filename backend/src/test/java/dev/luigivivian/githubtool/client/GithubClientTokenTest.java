package dev.luigivivian.githubtool.client;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.luigivivian.githubtool.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

/** FR-012: a configured operator token is used for upstream calls. */
@RestClientTest(GithubClient.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.github.base-url=https://api.github.com",
        "app.github.token=ghp-test-token",
        "app.github.cache-ttl=10m"
})
class GithubClientTokenTest {

    @Autowired
    private GithubClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void sendsBearerTokenOnUserRequest() {
        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghp-test-token"))
                .andRespond(withSuccess("{\"login\":\"octocat\"}", MediaType.APPLICATION_JSON));

        client.fetchUser("octocat");

        server.verify();
    }

    @Test
    void sendsBearerTokenOnReposRequest() {
        server.expect(requestTo(
                "https://api.github.com/users/octocat/repos?per_page=100&page=1&sort=updated"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghp-test-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.fetchRepos("octocat");

        server.verify();
    }
}
