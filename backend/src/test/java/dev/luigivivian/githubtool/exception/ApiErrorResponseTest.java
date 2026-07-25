package dev.luigivivian.githubtool.exception;

import dev.luigivivian.githubtool.controller.SearchController;
import dev.luigivivian.githubtool.dto.Profile;
import dev.luigivivian.githubtool.dto.Repo;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.service.SnapshotService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.exception.GithubUnavailableException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import dev.luigivivian.githubtool.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Problem-detail bodies (FR-011, SC-004): friendly wording, retry hints, and no leakage of
 * upstream exception text or stack traces.
 */
@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
class ApiErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SnapshotService snapshotService;

    @Test
    void rateLimitUnderOneMinuteReportsSeconds() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubRateLimitedException(45L));

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfterSeconds").value(45))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("45 seconds")));
    }

    @Test
    void rateLimitOfExactlyOneMinuteUsesSingularMinute() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubRateLimitedException(60L));

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("1 minute")));
    }

    @Test
    void rateLimitRoundsPartialMinutesUp() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubRateLimitedException(61L));

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("2 minutes")));
    }

    @Test
    void rateLimitWithUnknownResetStillGivesFriendlyAdvice() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubRateLimitedException(null));

        String body = mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("Try again later")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"retryAfterSeconds\":0");
    }

    @Test
    void rateLimitMessageMentionsTokenRemedy() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubRateLimitedException(30L));

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("GITHUB_TOKEN")));
    }

    @Test
    void upstreamFailureNeverLeaksCauseOrStackTrace() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubUnavailableException(
                        new IllegalStateException("jdbc://secret-host password=hunter2")));

        String body = mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("secret-host")
                .doesNotContain("hunter2")
                .doesNotContain("IllegalStateException")
                .doesNotContain("dev.luigivivian.githubtool")
                .doesNotContain("java.lang");
    }

    @Test
    void snapshotResponseSerialisesTimestampsAsIso8601Strings() throws Exception {
        SnapshotResponse response = new SnapshotResponse(
                new Profile("octocat", null, "https://a", null, 1, 1,
                        "https://github.com/octocat"),
                List.of(new Repo("hello", null, null, 3, 1,
                        Instant.parse("2026-07-20T10:00:00Z"), false, false,
                        "https://github.com/octocat/hello", null,
                        "https://github.com/octocat/hello.git")),
                Instant.parse("2026-07-24T12:00:00Z"), true, false);
        when(snapshotService.getSnapshot(anyString(), anyBoolean())).thenReturn(response);

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchedAt").value("2026-07-24T12:00:00Z"))
                .andExpect(jsonPath("$.repos[0].updatedAt").value("2026-07-20T10:00:00Z"))
                .andExpect(jsonPath("$.profile.name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.repos[0].language")
                        .value(org.hamcrest.Matchers.nullValue()));
    }
}
