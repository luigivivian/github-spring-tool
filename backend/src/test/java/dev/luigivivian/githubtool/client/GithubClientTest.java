package dev.luigivivian.githubtool.client;

import dev.luigivivian.githubtool.dto.Repo;
import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.exception.GithubUnavailableException;
import dev.luigivivian.githubtool.exception.GithubUserNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.mockito.Mockito.when;

import dev.luigivivian.githubtool.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(GithubClient.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.github.base-url=https://api.github.com",
        "app.github.token=",
        "app.github.cache-ttl=10m"
})
class GithubClientTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @MockitoBean
    private Clock clock;

    private static final String USER_JSON = """
            {
              "login": "octocat",
              "name": "The Octocat",
              "avatar_url": "https://avatars.githubusercontent.com/u/583231",
              "bio": "Mascot",
              "followers": 1234,
              "public_repos": 8,
              "html_url": "https://github.com/octocat",
              "company": "GitHub"
            }
            """;

    @Autowired
    private GithubClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void fetchUserParsesProfileAndSendsVersionHeader() {
        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andExpect(header("X-GitHub-Api-Version", "2026-03-10"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        GithubUserDto user = client.fetchUser("octocat");

        assertThat(user.login()).isEqualTo("octocat");
        assertThat(user.name()).isEqualTo("The Octocat");
        assertThat(user.followers()).isEqualTo(1234);
        assertThat(user.publicRepos()).isEqualTo(8);
    }

    @Test
    void fetchReposPaginatesUntilShortPage() {
        server.expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100&page=1&sort=updated"))
                .andRespond(withSuccess(repoPage(100), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100&page=2&sort=updated"))
                .andRespond(withSuccess(repoPage(30), MediaType.APPLICATION_JSON));

        List<GithubRepoDto> repos = client.fetchRepos("octocat");

        assertThat(repos).hasSize(130);
        assertThat(repos.getFirst().name()).isEqualTo("repo-0");
        server.verify();
    }

    @Test
    void fetchReposStopsAtThreePages() {
        for (int page = 1; page <= 3; page++) {
            server.expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100&page="
                    + page + "&sort=updated"))
                    .andRespond(withSuccess(repoPage(100), MediaType.APPLICATION_JSON));
        }

        List<GithubRepoDto> repos = client.fetchRepos("octocat");

        assertThat(repos).hasSize(300);
        server.verify();
    }

    @Test
    void notFoundMapsToUserNotFound() {
        server.expect(requestTo("https://api.github.com/users/ghost-user"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchUser("ghost-user"))
                .isInstanceOf(GithubUserNotFoundException.class)
                .hasFieldOrPropertyWithValue("username", "ghost-user");
    }

    @Test
    void forbiddenWithExhaustedQuotaMapsToRateLimited() {
        when(clock.instant()).thenReturn(NOW);
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "0");
        headers.set("x-ratelimit-reset", String.valueOf(NOW.getEpochSecond() + 120));
        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubRateLimitedException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(120L);
    }

    @Test
    void serverErrorMapsToUnavailable() {
        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    private static String repoPage(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> """
                        {
                          "name": "repo-%d",
                          "description": "Repo %d",
                          "language": "Java",
                          "stargazers_count": %d,
                          "forks_count": 1,
                          "updated_at": "2026-07-20T10:00:00Z",
                          "fork": false,
                          "archived": false,
                          "html_url": "https://github.com/octocat/repo-%d",
                          "homepage": null,
                          "clone_url": "https://github.com/octocat/repo-%d.git",
                          "size": 42
                        }
                        """.formatted(i, i, i, i, i))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
