package dev.luigivivian.githubtool.service;

import dev.luigivivian.githubtool.repository.SearchRecordRepository;
import dev.luigivivian.githubtool.repository.SnapshotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.luigivivian.githubtool.dto.RecentSearch;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.config.AppProperties;
import dev.luigivivian.githubtool.client.GithubClient;
import dev.luigivivian.githubtool.client.GithubRepoDto;
import dev.luigivivian.githubtool.client.GithubUserDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * SnapshotService against a real (in-memory) database: cache hit/miss behaviour, single-row
 * upsert per username, and the recent-search history (FR-015, FR-016, SC-007).
 */
@DataJpaTest
@Import(SnapshotService.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.github.base-url=https://api.github.com",
        "app.github.token=",
        "app.github.cache-ttl=10m"
})
class SnapshotCacheIntegrationTest {

    private static final Instant START = Instant.parse("2026-07-24T12:00:00Z");

    @TestConfiguration
    static class Config {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build();
        }
    }

    @MockitoBean
    private GithubClient github;

    @MockitoBean
    private Clock clock;

    @Autowired
    private SnapshotService service;

    @Autowired
    private SnapshotRepository snapshots;

    @Autowired
    private SearchRecordRepository searches;

    @BeforeEach
    void resetClock() {
        when(clock.instant()).thenReturn(START);
    }

    private void clockAt(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    @Test
    void repeatSearchWithinWindowUsesNoGithubQuota() {
        stubGithub("octocat", 2, List.of(repoDto("hello"), repoDto("world")));

        SnapshotResponse first = service.getSnapshot("octocat", false);
        clockAt(START.plus(Duration.ofMinutes(9)));
        SnapshotResponse second = service.getSnapshot("octocat", false);

        assertThat(first.fromCache()).isFalse();
        assertThat(second.fromCache()).isTrue();
        assertThat(second.fetchedAt()).isEqualTo(START);
        assertThat(second.repos()).hasSize(2);
        assertThat(second.repos().getFirst().updatedAt())
                .isEqualTo(Instant.parse("2026-07-20T10:00:00Z"));
        verify(github, times(1)).fetchUser("octocat");
        verify(github, times(1)).fetchRepos("octocat");
        assertThat(snapshots.count()).isEqualTo(1);
        assertThat(searches.count()).isEqualTo(2);
    }

    @Test
    void expiredWindowRefetchesAndUpdatesTheSameRow() {
        stubGithub("octocat", 1, List.of(repoDto("hello")));

        service.getSnapshot("octocat", false);
        clockAt(START.plus(Duration.ofMinutes(10)).plusSeconds(1));
        SnapshotResponse second = service.getSnapshot("octocat", false);

        assertThat(second.fromCache()).isFalse();
        assertThat(second.fetchedAt()).isEqualTo(START.plus(Duration.ofMinutes(10)).plusSeconds(1));
        verify(github, times(2)).fetchUser("octocat");
        assertThat(snapshots.count()).isEqualTo(1);
        assertThat(snapshots.findByUsername("octocat").orElseThrow().getFetchedAt())
                .isEqualTo(START.plus(Duration.ofMinutes(10)).plusSeconds(1));
    }

    @Test
    void refreshBypassesFreshCacheWithoutDuplicatingRows() {
        stubGithub("octocat", 1, List.of(repoDto("hello")));

        service.getSnapshot("octocat", false);
        SnapshotResponse refreshed = service.getSnapshot("octocat", true);

        assertThat(refreshed.fromCache()).isFalse();
        verify(github, times(2)).fetchUser("octocat");
        assertThat(snapshots.count()).isEqualTo(1);
    }

    @Test
    void mixedCaseAndPaddedInputHitsTheSameCacheRow() {
        stubGithub("octocat", 1, List.of(repoDto("hello")));

        service.getSnapshot("OctoCat", false);
        SnapshotResponse second = service.getSnapshot("  octocat  ", false);

        assertThat(second.fromCache()).isTrue();
        verify(github, times(1)).fetchUser("octocat");
        assertThat(snapshots.count()).isEqualTo(1);
        assertThat(service.recentSearches()).containsExactly(new RecentSearch("octocat", START));
    }

    @Test
    void truncationFlagAndFullRepoListSurviveTheCacheRoundTrip() {
        stubGithub("bigaccount", 500, Collections.nCopies(300, repoDto("hello")));

        SnapshotResponse live = service.getSnapshot("bigaccount", false);
        SnapshotResponse cached = service.getSnapshot("bigaccount", false);

        assertThat(live.truncated()).isTrue();
        assertThat(cached.truncated()).isTrue();
        assertThat(cached.repos()).hasSize(300);
        assertThat(cached.fromCache()).isTrue();
    }

    @Test
    void maximumLengthUsernameIsPersistable() {
        String username = "a123456789b123456789c123456789d12345678"; // 39 chars
        stubGithub(username, 1, List.of(repoDto("hello")));

        SnapshotResponse response = service.getSnapshot(username, false);

        assertThat(username).hasSize(39);
        assertThat(response.fromCache()).isFalse();
        assertThat(snapshots.findByUsername(username)).isPresent();
        assertThat(service.recentSearches())
                .containsExactly(new RecentSearch(username, START));
    }

    @Test
    void recentSearchesAreDedupedNewestFirstAndCappedAtTen() {
        for (int i = 0; i < 12; i++) {
            String username = "user-" + i;
            stubGithub(username, 0, List.of());
            clockAt(START.plusSeconds(i));
            service.getSnapshot(username, false);
        }
        // re-search the oldest user so it becomes the newest entry
        clockAt(START.plusSeconds(100));
        service.getSnapshot("user-0", true);

        List<RecentSearch> recent = service.recentSearches();

        assertThat(recent).hasSize(10);
        assertThat(recent.getFirst().username()).isEqualTo("user-0");
        assertThat(recent.getFirst().searchedAt()).isEqualTo(START.plusSeconds(100));
        assertThat(recent.stream().map(RecentSearch::username).distinct().count()).isEqualTo(10);
    }

    private void stubGithub(String username, int publicRepos, List<GithubRepoDto> repos) {
        when(github.fetchUser(username)).thenReturn(
                new GithubUserDto(username, "Display Name", "https://a", "bio", 7, publicRepos,
                        "https://github.com/" + username));
        when(github.fetchRepos(username)).thenReturn(repos);
    }

    private static GithubRepoDto repoDto(String name) {
        return new GithubRepoDto(name, "desc", "Java", 5, 2,
                Instant.parse("2026-07-20T10:00:00Z"), false, false,
                "https://github.com/octocat/" + name, null,
                "https://github.com/octocat/" + name + ".git");
    }
}
