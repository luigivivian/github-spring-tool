package dev.luigivivian.githubtool.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.luigivivian.githubtool.config.SecurityConfig;
import dev.luigivivian.githubtool.dto.ContributorInfo;
import dev.luigivivian.githubtool.dto.LanguageShare;
import dev.luigivivian.githubtool.dto.ReleaseInfo;
import dev.luigivivian.githubtool.dto.RepoDetailResponse;
import dev.luigivivian.githubtool.exception.GithubResourceNotFoundException;
import dev.luigivivian.githubtool.service.RepoDetailService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepoDetailController.class)
@Import(SecurityConfig.class)
class RepoDetailControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RepoDetailService repoDetailService;

    @Test
    void detailReturnsAllSections() throws Exception {
        when(repoDetailService.detail("octocat", "hello")).thenReturn(new RepoDetailResponse(
                "# Hello",
                List.of(new ReleaseInfo("v1.0", NOW, "https://github.com/r/1")),
                List.of(new ContributorInfo("alice", "https://a", 42, "https://github.com/alice")),
                List.of(new LanguageShare("Java", 100.0, 1000L)),
                NOW, false));

        mockMvc.perform(get("/api/repos/octocat/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readme").value("# Hello"))
                .andExpect(jsonPath("$.releases[0].name").value("v1.0"))
                .andExpect(jsonPath("$.contributors[0].login").value("alice"))
                .andExpect(jsonPath("$.languages[0].language").value("Java"));
    }

    @Test
    void invalidRepoNameRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/repos/octocat/bad%20name!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRepoReturnsProblem404() throws Exception {
        when(repoDetailService.detail(anyString(), anyString()))
                .thenThrow(new GithubResourceNotFoundException("octocat/gone"));

        mockMvc.perform(get("/api/repos/octocat/gone"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not found"));
    }
}
