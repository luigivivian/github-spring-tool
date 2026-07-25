package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.Profile;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.service.SnapshotService;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.exception.GithubUnavailableException;
import dev.luigivivian.githubtool.exception.GithubUserNotFoundException;
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

@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SnapshotService snapshotService;

    @Test
    void returnsSnapshotForValidUser() throws Exception {
        SnapshotResponse response = new SnapshotResponse(
                new Profile("octocat", "The Octocat", "https://a", "bio", 10, 1,
                        "https://github.com/octocat"),
                List.of(),
                Instant.parse("2026-07-24T12:00:00Z"), true, false);
        when(snapshotService.getSnapshot("octocat", false)).thenReturn(response);

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.login").value("octocat"))
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void rejectsInvalidUsername() throws Exception {
        mockMvc.perform(get("/api/users/bad--name"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid username"));
    }

    @Test
    void unknownUserReturnsProblem404() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubUserNotFoundException("ghost-user"));

        mockMvc.perform(get("/api/users/ghost-user"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("ghost-user")));
    }

    @Test
    void rateLimitReturnsProblem429WithRetrySeconds() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubRateLimitedException(90L));

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.retryAfterSeconds").value(90));
    }

    @Test
    void upstreamFailureReturnsProblem502() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean()))
                .thenThrow(new GithubUnavailableException(new RuntimeException("boom")));

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("GitHub unavailable"));
    }
}
