package dev.luigivivian.githubtool.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.luigivivian.githubtool.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
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
class GithubInsightsClientTest {

    @MockitoBean
    private Clock clock;

    @Autowired
    private GithubClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void fetchRepoLanguagesParsesByteMap() {
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/languages"))
                .andRespond(withSuccess("{\"Java\": 1200, \"TypeScript\": 300}",
                        MediaType.APPLICATION_JSON));

        Map<String, Long> languages = client.fetchRepoLanguages("octocat", "hello");

        assertThat(languages).containsEntry("Java", 1200L).containsEntry("TypeScript", 300L);
    }

    @Test
    void commitActivityStillComputingReturnsNull() {
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/stats/commit_activity"))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .body("{}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(client.fetchCommitActivity("octocat", "hello")).isNull();
    }

    @Test
    void commitActivityParsesWeeklyTotals() {
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/stats/commit_activity"))
                .andRespond(withSuccess(
                        "[{\"week\":1752000000,\"total\":3},{\"week\":1752604800,\"total\":7}]",
                        MediaType.APPLICATION_JSON));

        int[] weeks = client.fetchCommitActivity("octocat", "hello");

        assertThat(weeks).containsExactly(3, 7);
    }

    @Test
    void readmeIsBase64Decoded() {
        String encoded = Base64.getEncoder()
                .encodeToString("# Hello\nWorld".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/readme"))
                .andRespond(withSuccess(
                        "{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetchReadme("octocat", "hello")).isEqualTo("# Hello\nWorld");
    }

    @Test
    void missingReadmeReturnsNull() {
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/readme"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetchReadme("octocat", "hello")).isNull();
    }

    @Test
    void releasesAndContributorsParse() {
        server.expect(requestTo("https://api.github.com/repos/octocat/hello/releases?per_page=5"))
                .andRespond(withSuccess(
                        "[{\"name\":\"v1.0\",\"tag_name\":\"1.0\","
                                + "\"published_at\":\"2026-07-01T00:00:00Z\","
                                + "\"html_url\":\"https://github.com/octocat/hello/releases/1\"}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                "https://api.github.com/repos/octocat/hello/contributors?per_page=10"))
                .andRespond(withSuccess(
                        "[{\"login\":\"alice\",\"avatar_url\":\"https://a\","
                                + "\"contributions\":42,\"html_url\":\"https://github.com/alice\"}]",
                        MediaType.APPLICATION_JSON));

        List<GithubReleaseDto> releases = client.fetchReleases("octocat", "hello", 5);
        List<GithubContributorDto> contributors = client.fetchContributors("octocat", "hello", 10);

        assertThat(releases).hasSize(1);
        assertThat(releases.getFirst().name()).isEqualTo("v1.0");
        assertThat(contributors).hasSize(1);
        assertThat(contributors.getFirst().contributions()).isEqualTo(42);
    }
}
