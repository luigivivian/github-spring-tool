package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.RecentSearch;
import dev.luigivivian.githubtool.service.SnapshotService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import dev.luigivivian.githubtool.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contract shape for GET /api/searches/recent (FR-016). */
@WebMvcTest(RecentSearchesController.class)
@Import(SecurityConfig.class)
class RecentSearchesControllerTest {

    private static final Instant BASE = Instant.parse("2026-07-24T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SnapshotService snapshotService;

    @Test
    void returnsUsernameAndIso8601SearchedAtNewestFirst() throws Exception {
        when(snapshotService.recentSearches()).thenReturn(List.of(
                new RecentSearch("octocat", BASE),
                new RecentSearch("torvalds", BASE.minusSeconds(60))));

        mockMvc.perform(get("/api/searches/recent"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("octocat"))
                .andExpect(jsonPath("$[0].searchedAt").value("2026-07-24T12:00:00Z"))
                .andExpect(jsonPath("$[1].username").value("torvalds"))
                .andExpect(jsonPath("$[1].searchedAt").value("2026-07-24T11:59:00Z"));
    }

    @Test
    void returnsEmptyJsonArrayWhenNoSearchesRecorded() throws Exception {
        when(snapshotService.recentSearches()).thenReturn(List.of());

        mockMvc.perform(get("/api/searches/recent"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void serialisesAtMostTenEntries() throws Exception {
        when(snapshotService.recentSearches()).thenReturn(
                IntStream.range(0, 10)
                        .mapToObj(i -> new RecentSearch("user-" + i, BASE.minusSeconds(i)))
                        .toList());

        mockMvc.perform(get("/api/searches/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(10)))
                .andExpect(jsonPath("$[0].username").value("user-0"))
                .andExpect(jsonPath("$[9].username").value("user-9"));
    }

    @Test
    void entriesExposeOnlyUsernameAndSearchedAt() throws Exception {
        when(snapshotService.recentSearches())
                .thenReturn(List.of(new RecentSearch("octocat", BASE)));

        mockMvc.perform(get("/api/searches/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(content().json("[{\"username\":\"octocat\","
                        + "\"searchedAt\":\"2026-07-24T12:00:00Z\"}]", true));
    }
}
