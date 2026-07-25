package dev.luigivivian.githubtool.client;

import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.exception.GithubUnavailableException;
import dev.luigivivian.githubtool.exception.GithubUserNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.luigivivian.githubtool.config.AppProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
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

/** Pagination, rate-limit header parsing and transport-failure mapping edge cases. */
@RestClientTest(GithubClient.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.github.base-url=https://api.github.com",
        "app.github.token=",
        "app.github.cache-ttl=10m"
})
class GithubClientEdgeTest {

    private static final String USER_URL = "https://api.github.com/users/octocat";
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Autowired
    private GithubClient client;

    @Autowired
    private MockRestServiceServer server;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void pinClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    private static String reposUrl(int page) {
        return reposUrl("octocat", page);
    }

    private static String reposUrl(String username, int page) {
        return "https://api.github.com/users/" + username + "/repos?per_page=100&page=" + page
                + "&sort=updated";
    }

    @Test
    void emptyFirstPageYieldsNoReposAndNoFurtherRequests() {
        server.expect(requestTo(reposUrl(1)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<GithubRepoDto> repos = client.fetchRepos("octocat");

        assertThat(repos).isEmpty();
        server.verify();
    }

    @Test
    void emptySecondPageStopsPaginationAfterFullFirstPage() {
        server.expect(requestTo(reposUrl(1)))
                .andRespond(withSuccess(repoPage(100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(reposUrl(2)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<GithubRepoDto> repos = client.fetchRepos("octocat");

        assertThat(repos).hasSize(100);
        server.verify();
    }

    @Test
    void anonymousRequestsSendNoAuthorizationHeader() {
        server.expect(requestTo(USER_URL))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"login\":\"octocat\"}", MediaType.APPLICATION_JSON));

        client.fetchUser("octocat");

        server.verify();
    }

    @Test
    void repoWithOnlyRequiredFieldsParsesWithNullsAndZeros() {
        server.expect(requestTo(reposUrl(1)))
                .andRespond(withSuccess("[{\"name\":\"bare\"}]", MediaType.APPLICATION_JSON));

        List<GithubRepoDto> repos = client.fetchRepos("octocat");

        assertThat(repos).hasSize(1);
        GithubRepoDto repo = repos.getFirst();
        assertThat(repo.name()).isEqualTo("bare");
        assertThat(repo.description()).isNull();
        assertThat(repo.language()).isNull();
        assertThat(repo.homepage()).isNull();
        assertThat(repo.stars()).isZero();
        assertThat(repo.forks()).isZero();
        assertThat(repo.fork()).isFalse();
        assertThat(repo.archived()).isFalse();
    }

    @Test
    void tooManyRequestsMapsToRateLimitedWithRetryAfterHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("retry-after", "45");
        server.expect(requestTo(USER_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubRateLimitedException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(45L);
    }

    @Test
    void forbiddenWithRemainingQuotaMapsToUnavailableNotRateLimited() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "42");
        server.expect(requestTo(USER_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void forbiddenWithExhaustedQuotaAndNoResetHeadersLeavesRetryUnknown() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "0");
        server.expect(requestTo(USER_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubRateLimitedException.class)
                .extracting("retryAfterSeconds")
                .isNull();
    }

    @Test
    void httpDateRetryAfterFallsBackToRateLimitResetHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "0");
        headers.set("retry-after", "Wed, 21 Oct 2026 07:28:00 GMT");
        headers.set("x-ratelimit-reset", String.valueOf(NOW.getEpochSecond() + 300));
        server.expect(requestTo(USER_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubRateLimitedException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(300L);
    }

    @Test
    void pastRateLimitResetIsClampedToZero() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "0");
        headers.set("x-ratelimit-reset", String.valueOf(NOW.getEpochSecond() - 120));
        server.expect(requestTo(USER_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubRateLimitedException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(0L);
    }

    @Test
    void unparseableResetHeaderLeavesRetryUnknown() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "0");
        headers.set("x-ratelimit-reset", "soon");
        server.expect(requestTo(USER_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubRateLimitedException.class)
                .extracting("retryAfterSeconds")
                .isNull();
    }

    @Test
    void notFoundOnReposCallMapsToUserNotFound() {
        server.expect(requestTo(reposUrl("ghost-user", 1)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchRepos("ghost-user"))
                .isInstanceOf(GithubUserNotFoundException.class)
                .hasFieldOrPropertyWithValue("username", "ghost-user");
    }

    @Test
    void transportFailureMapsToUnavailable() {
        server.expect(requestTo(USER_URL)).andRespond(request -> {
            throw new IOException("connection refused");
        });

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void malformedJsonBodyMapsToUnavailable() {
        server.expect(requestTo(USER_URL))
                .andRespond(withSuccess("{\"login\": ", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void gatewayTimeoutMapsToUnavailable() {
        server.expect(requestTo(USER_URL))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> client.fetchUser("octocat"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    private static String repoPage(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> """
                        {"name": "repo-%d", "stargazers_count": %d, "forks_count": 0,
                         "updated_at": "2026-07-20T10:00:00Z", "fork": false, "archived": false,
                         "html_url": "https://github.com/octocat/repo-%d",
                         "clone_url": "https://github.com/octocat/repo-%d.git"}
                        """.formatted(i, i, i, i))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
