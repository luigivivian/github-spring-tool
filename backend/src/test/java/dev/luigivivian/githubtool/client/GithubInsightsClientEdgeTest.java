package dev.luigivivian.githubtool.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.luigivivian.githubtool.config.AppProperties;
import dev.luigivivian.githubtool.exception.GithubAuthExpiredException;
import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.exception.GithubResourceNotFoundException;
import dev.luigivivian.githubtool.exception.GithubUnavailableException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
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

/** Body-shape and failure-mapping edge cases for the feature-002 insights endpoints. */
@RestClientTest(GithubClient.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.github.base-url=https://api.github.com",
        "app.github.token=",
        "app.github.cache-ttl=10m"
})
class GithubInsightsClientEdgeTest {

    private static final String ACTIVITY_URL =
            "https://api.github.com/repos/octocat/hello/stats/commit_activity";
    private static final String LANGUAGES_URL =
            "https://api.github.com/repos/octocat/hello/languages";
    private static final String README_URL = "https://api.github.com/repos/octocat/hello/readme";
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Autowired
    private GithubClient client;

    @Autowired
    private MockRestServiceServer server;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private GithubTokenResolver tokenResolver;

    @BeforeEach
    void pinClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    // --- commit activity ------------------------------------------------------------------------

    @Test
    void malformedArrayBodyMapsToUnavailable() {
        server.expect(requestTo(ACTIVITY_URL)).andRespond(
                withSuccess("[{\"week\":1752000000,\"total\":", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchCommitActivity("octocat", "hello"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void objectBodyWithOkStatusMeansNoHistoryRatherThanPending() {
        server.expect(requestTo(ACTIVITY_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // only a 202 may report "still computing"; a 200 without array data is simply no history
        assertThat(client.fetchCommitActivity("octocat", "hello")).isEmpty();
    }

    @Test
    void emptyArrayGivesAnEmptyWeekSeries() {
        server.expect(requestTo(ACTIVITY_URL))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchCommitActivity("octocat", "hello")).isEmpty();
    }

    @Test
    void leadingWhitespaceBeforeTheArrayStillParses() {
        server.expect(requestTo(ACTIVITY_URL)).andRespond(withSuccess(
                "\n\t  [{\"week\":1752000000,\"total\":5}]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchCommitActivity("octocat", "hello")).containsExactly(5);
    }

    @Test
    void acceptedStatusIsTheOnlyPendingSignalEvenWithAnArrayBody() {
        server.expect(requestTo(ACTIVITY_URL)).andRespond(withStatus(HttpStatus.ACCEPTED)
                .body("[{\"week\":1752000000,\"total\":5}]")
                .contentType(MediaType.APPLICATION_JSON));

        assertThat(client.fetchCommitActivity("octocat", "hello")).isNull();
    }

    @Test
    void emptyBodyMeansNoHistoryRatherThanPending() {
        // GitHub answers 204 for a repository with no commits at all
        server.expect(requestTo(ACTIVITY_URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(client.fetchCommitActivity("octocat", "hello")).isEmpty();
    }

    @Test
    void weeksWithoutATotalFieldDefaultToZero() {
        server.expect(requestTo(ACTIVITY_URL)).andRespond(withSuccess(
                "[{\"week\":1752000000},{\"week\":1752604800,\"total\":4}]",
                MediaType.APPLICATION_JSON));

        assertThat(client.fetchCommitActivity("octocat", "hello")).containsExactly(0, 4);
    }

    @Test
    void missingRepoOnActivityMapsToResourceNotFound() {
        server.expect(requestTo(ACTIVITY_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchCommitActivity("octocat", "hello"))
                .isInstanceOf(GithubResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("resource", "octocat/hello");
    }

    @Test
    void rateLimitedActivityCallSurfacesRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("retry-after", "60");
        server.expect(requestTo(ACTIVITY_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> client.fetchCommitActivity("octocat", "hello"))
                .isInstanceOf(GithubRateLimitedException.class)
                .extracting("retryAfterSeconds").isEqualTo(60L);
    }

    // --- languages ------------------------------------------------------------------------------

    @Test
    void emptyLanguageObjectGivesAnEmptyMap() {
        server.expect(requestTo(LANGUAGES_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchRepoLanguages("octocat", "hello")).isEmpty();
    }

    @Test
    void missingRepoOnLanguagesMapsToResourceNotFound() {
        server.expect(requestTo(LANGUAGES_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchRepoLanguages("octocat", "hello"))
                .isInstanceOf(GithubResourceNotFoundException.class);
    }

    @Test
    void languageByteCountsBeyondIntRangeStayExact() {
        server.expect(requestTo(LANGUAGES_URL))
                .andRespond(withSuccess("{\"Java\": 3000000000}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchRepoLanguages("octocat", "hello"))
                .containsEntry("Java", 3_000_000_000L);
    }

    // --- releases / contributors ------------------------------------------------------------------

    @Test
    void emptyReleasesBodyGivesAnEmptyList() {
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/releases?per_page=5"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(client.fetchReleases("octocat", "hello", 5)).isEmpty();
    }

    @Test
    void emptyContributorsBodyGivesAnEmptyList() {
        server.expect(
                requestTo("https://api.github.com/repos/octocat/hello/contributors?per_page=10"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(client.fetchContributors("octocat", "hello", 10)).isEmpty();
    }

    @Test
    void releaseWithoutANameKeepsTheTagForTheServiceToFallBackOn() {
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/releases?per_page=5"))
                .andRespond(withSuccess("[{\"tag_name\":\"v3.1\","
                        + "\"published_at\":\"2026-07-01T00:00:00Z\","
                        + "\"html_url\":\"https://github.com/octocat/hello/releases/3\"}]",
                        MediaType.APPLICATION_JSON));

        GithubReleaseDto release = client.fetchReleases("octocat", "hello", 5).getFirst();

        assertThat(release.name()).isNull();
        assertThat(release.tagName()).isEqualTo("v3.1");
    }

    // --- readme -----------------------------------------------------------------------------------

    @Test
    void lineWrappedBase64ReadmeIsDecoded() {
        String text = "# Hello\n".repeat(20);
        String encoded = Base64.getMimeEncoder()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(README_URL)).andRespond(withSuccess(
                "{\"content\":" + quoteJson(encoded) + ",\"encoding\":\"base64\"}",
                MediaType.APPLICATION_JSON));

        assertThat(client.fetchReadme("octocat", "hello")).isEqualTo(text);
    }

    @Test
    void readmePayloadWithoutContentReturnsNull() {
        server.expect(requestTo(README_URL))
                .andRespond(withSuccess("{\"encoding\":\"base64\"}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchReadme("octocat", "hello")).isNull();
    }

    @Test
    void emptyReadmeContentDecodesToAnEmptyString() {
        server.expect(requestTo(README_URL)).andRespond(
                withSuccess("{\"content\":\"\",\"encoding\":\"base64\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetchReadme("octocat", "hello")).isEmpty();
    }

    @Test
    void rateLimitedReadmeCallIsNotSwallowedAsMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "0");
        server.expect(requestTo(README_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.fetchReadme("octocat", "hello"))
                .isInstanceOf(GithubRateLimitedException.class);
    }

    // --- token expiry -----------------------------------------------------------------------------

    @Test
    void unauthorizedWithASignedInVisitorTokenMapsToAuthExpired() {
        when(tokenResolver.currentToken()).thenReturn(Optional.of("gho_revoked"));
        server.expect(requestTo(LANGUAGES_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.fetchRepoLanguages("octocat", "hello"))
                .isInstanceOf(GithubAuthExpiredException.class);
    }

    @Test
    void unauthorizedWithoutAVisitorTokenStaysAnUpstreamFailure() {
        when(tokenResolver.currentToken()).thenReturn(Optional.empty());
        server.expect(requestTo(ACTIVITY_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.fetchCommitActivity("octocat", "hello"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void visitorTokenIsSentOnInsightsRequests() {
        when(tokenResolver.currentToken()).thenReturn(Optional.of("gho_visitor"));
        server.expect(requestTo(LANGUAGES_URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header(HttpHeaders.AUTHORIZATION, "Bearer gho_visitor"))
                .andRespond(withSuccess("{\"Java\": 1}", MediaType.APPLICATION_JSON));

        client.fetchRepoLanguages("octocat", "hello");

        server.verify();
    }

    private static String quoteJson(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
}
