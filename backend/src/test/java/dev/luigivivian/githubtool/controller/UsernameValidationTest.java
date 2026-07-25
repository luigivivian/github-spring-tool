package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.Profile;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.service.SnapshotService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import dev.luigivivian.githubtool.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Boundary coverage for the GitHub username rules of FR-001 / contract
 * {@code ^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$}, plus binding of the
 * {@code refresh} query parameter.
 */
@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
class UsernameValidationTest {

    /** Exactly 39 characters - the documented maximum. */
    private static final String MAX_LENGTH = "a123456789b123456789c123456789d12345678";
    /** 40 characters - one over the maximum. */
    private static final String TOO_LONG = "a123456789b123456789c123456789d123456789";

    private static final SnapshotResponse SNAPSHOT = new SnapshotResponse(
            new Profile("octocat", "The Octocat", "https://a", "bio", 1, 0,
                    "https://github.com/octocat"),
            List.of(), Instant.parse("2026-07-24T12:00:00Z"), false, false);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SnapshotService snapshotService;

    @ParameterizedTest
    @ValueSource(strings = {
            "a",                                       // single letter
            "9",                                       // single digit
            "a-b",                                     // single interior hyphen
            "A1-b2-C3",                                // mixed case, multiple hyphens
            "octocat",
            MAX_LENGTH                                 // 39 chars
    })
    void acceptsUsernamesAllowedByGithubRules(String username) throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean())).thenReturn(SNAPSHOT);

        mockMvc.perform(get("/api/users/{username}", username))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            TOO_LONG,        // 40 chars
            "-octocat",      // leading hyphen
            "octocat-",      // trailing hyphen
            "octo--cat",     // double hyphen
            "-",             // hyphen only
            "octo_cat",      // underscore
            "octo.cat",      // dot
            "octo cat",      // space
            "octo+cat",      // plus
            "octo$cat"       // symbol
    })
    void rejectsUsernamesOutsideGithubRules(String username) throws Exception {
        mockMvc.perform(get("/api/users/{username}", username))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid username"));

        verifyNoInteractions(snapshotService);
    }

    @Test
    void invalidUsernameProblemCarriesStatusAndGuidance() throws Exception {
        mockMvc.perform(get("/api/users/{username}", TOO_LONG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("39 characters")));
    }

    @Test
    void refreshDefaultsToFalseWhenAbsent() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean())).thenReturn(SNAPSHOT);

        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isOk());

        verify(snapshotService).getSnapshot("octocat", false);
    }

    @Test
    void refreshTrueIsForwardedToService() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean())).thenReturn(SNAPSHOT);

        mockMvc.perform(get("/api/users/octocat").param("refresh", "true"))
                .andExpect(status().isOk());

        verify(snapshotService).getSnapshot("octocat", true);
    }

    @Test
    void unparseableRefreshValueIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/octocat").param("refresh", "maybe"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
    }

    /** SC-004: no input should surface a raw technical error (5xx) to a visitor. */
    @Test
    void emptyRefreshValueIsNotServerError() throws Exception {
        when(snapshotService.getSnapshot(anyString(), anyBoolean())).thenReturn(SNAPSHOT);

        MvcResult result = mockMvc.perform(get("/api/users/octocat").param("refresh", ""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isLessThan(500);
    }

    @Test
    void missingUsernameSegmentIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/")).andReturn();

        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
        verifyNoInteractions(snapshotService);
    }
}
