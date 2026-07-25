package dev.luigivivian.githubtool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.luigivivian.githubtool.client.GithubClient;
import dev.luigivivian.githubtool.dto.ActivityResponse;
import dev.luigivivian.githubtool.dto.LanguageShare;
import dev.luigivivian.githubtool.dto.LanguagesResponse;
import dev.luigivivian.githubtool.dto.Profile;
import dev.luigivivian.githubtool.dto.Repo;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.entity.ApiCache;
import dev.luigivivian.githubtool.repository.ApiCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InsightsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private final SnapshotService snapshots = mock(SnapshotService.class);
    private final GithubClient github = mock(GithubClient.class);
    private final ApiCacheRepository cacheRepo = mock(ApiCacheRepository.class);

    private InsightsService service;

    @BeforeEach
    void setUp() {
        ApiCacheService cache = new ApiCacheService(cacheRepo, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));
        service = new InsightsService(snapshots, github, cache);
        when(cacheRepo.findByCacheKey(any())).thenReturn(Optional.empty());
        when(cacheRepo.save(any(ApiCache.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void toSharesComputesPercentagesAndGroupsSmallOnesAsOther() {
        List<LanguageShare> shares = InsightsService.toShares(
                Map.of("Java", 970L, "TypeScript", 15L, "Shell", 15L), true);

        assertThat(shares).extracting(LanguageShare::language)
                .containsExactly("Java", "Other");
        assertThat(shares.getFirst().percent()).isEqualTo(97.0);
        assertThat(shares.getLast().percent()).isEqualTo(3.0);
        assertThat(shares.getLast().bytes()).isEqualTo(30L);
    }

    @Test
    void toSharesWithoutGroupingKeepsAllLanguages() {
        List<LanguageShare> shares = InsightsService.toShares(
                Map.of("Java", 970L, "Shell", 30L), false);

        assertThat(shares).hasSize(2);
    }

    @Test
    void toSharesEmptyInputGivesEmptyList() {
        assertThat(InsightsService.toShares(Map.of(), true)).isEmpty();
    }

    @Test
    void languagesAggregatesTopNonForkRepos() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(
                repo("main-lib", 50, false),
                repo("forked-thing", 999, true),
                repo("side-tool", 10, false)));
        when(github.fetchRepoLanguages("octocat", "main-lib"))
                .thenReturn(Map.of("Java", 800L));
        when(github.fetchRepoLanguages("octocat", "side-tool"))
                .thenReturn(Map.of("Java", 100L, "Shell", 100L));

        LanguagesResponse response = service.languages("OctoCat");

        verify(github, never()).fetchRepoLanguages("octocat", "forked-thing");
        assertThat(response.languages()).extracting(LanguageShare::language)
                .containsExactly("Java", "Shell");
        assertThat(response.languages().getFirst().percent()).isEqualTo(90.0);
        assertThat(response.fromCache()).isFalse();
    }

    @Test
    void activitySumsWeeksAlignedToNewest() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(
                repo("a", 5, false), repo("b", 3, false)));
        when(github.fetchCommitActivity("octocat", "a"))
                .thenReturn(fullWeeks(52, 1));
        when(github.fetchCommitActivity("octocat", "b"))
                .thenReturn(new int[] {4, 9}); // young repo: only 2 weeks of history

        ActivityResponse response = service.activity("octocat");

        assertThat(response.weeks()).hasSize(52);
        assertThat(response.pending()).isFalse();
        assertThat(response.weeks().get(51)).isEqualTo(1 + 9);
        assertThat(response.weeks().get(50)).isEqualTo(1 + 4);
        assertThat(response.weeks().get(49)).isEqualTo(1);
        verify(cacheRepo).save(any(ApiCache.class));
    }

    @Test
    void activityStillComputingIsPendingAndNotCached() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(
                repo("a", 5, false), repo("b", 3, false)));
        when(github.fetchCommitActivity("octocat", "a")).thenReturn(fullWeeks(52, 2));
        when(github.fetchCommitActivity("octocat", "b")).thenReturn(null);

        ActivityResponse response = service.activity("octocat");

        assertThat(response.pending()).isTrue();
        assertThat(response.weeks().get(51)).isEqualTo(2);
        verify(cacheRepo, never()).save(any(ApiCache.class));
    }

    @Test
    void userWithoutReposGivesEmptyInsights() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot());

        assertThat(service.languages("octocat").languages()).isEmpty();
        ActivityResponse activity = service.activity("octocat");
        assertThat(activity.pending()).isFalse();
        assertThat(activity.weeks()).allMatch(total -> total == 0);
    }

    private static SnapshotResponse snapshot(Repo... repos) {
        return new SnapshotResponse(
                new Profile("octocat", null, "https://a", null, 1, repos.length,
                        "https://github.com/octocat"),
                List.of(repos), NOW, false, false);
    }

    private static Repo repo(String name, int stars, boolean fork) {
        return new Repo(name, null, "Java", stars, 0, NOW, fork, false,
                "https://github.com/octocat/" + name, null,
                "https://github.com/octocat/" + name + ".git");
    }

    private static int[] fullWeeks(int count, int value) {
        int[] weeks = new int[count];
        java.util.Arrays.fill(weeks, value);
        return weeks;
    }
}
