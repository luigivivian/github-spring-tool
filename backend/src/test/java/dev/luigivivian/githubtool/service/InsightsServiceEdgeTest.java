package dev.luigivivian.githubtool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import dev.luigivivian.githubtool.exception.GithubResourceNotFoundException;
import dev.luigivivian.githubtool.repository.ApiCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Repo caps, week-window alignment, cache keys and "Other" bucket boundaries. */
class InsightsServiceEdgeTest {

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

    // --- language repo cap (25) ---------------------------------------------------------------

    @Test
    void exactlyTwentyFiveNonForkReposAreAllAggregated() {
        List<Repo> repos = numberedRepos(25, false);
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(repos));
        when(github.fetchRepoLanguages(eq("octocat"), anyString())).thenReturn(Map.of("Java", 4L));

        LanguagesResponse response = service.languages("octocat");

        verify(github, times(25)).fetchRepoLanguages(eq("octocat"), anyString());
        assertThat(response.languages()).singleElement()
                .extracting(LanguageShare::bytes).isEqualTo(100L);
    }

    @Test
    void twentySixthRepoByStarsIsDropped() {
        List<Repo> repos = numberedRepos(26, false);
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(repos));
        when(github.fetchRepoLanguages(eq("octocat"), anyString())).thenReturn(Map.of("Java", 1L));

        service.languages("octocat");

        // numberedRepos gives repo-0 the most stars, so repo-25 is the least starred
        verify(github, times(25)).fetchRepoLanguages(eq("octocat"), anyString());
        verify(github, never()).fetchRepoLanguages("octocat", "repo-25");
        verify(github).fetchRepoLanguages("octocat", "repo-24");
    }

    @Test
    void highlyStarredForksNeverConsumeCapSlots() {
        List<Repo> repos = new ArrayList<>(numberedRepos(25, false));
        for (int i = 0; i < 10; i++) {
            repos.add(repo("fork-" + i, 100_000, true));
        }
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(repos));
        when(github.fetchRepoLanguages(eq("octocat"), anyString())).thenReturn(Map.of("Java", 1L));

        service.languages("octocat");

        verify(github, times(25)).fetchRepoLanguages(eq("octocat"), anyString());
        for (int i = 0; i < 10; i++) {
            verify(github, never()).fetchRepoLanguages("octocat", "fork-" + i);
        }
    }

    @Test
    void reposWithEqualStarsKeepSnapshotOrderAtTheCap() {
        List<Repo> repos = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            repos.add(repo("tie-" + i, 7, false));
        }
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(repos));
        when(github.fetchRepoLanguages(eq("octocat"), anyString())).thenReturn(Map.of("Java", 1L));

        service.languages("octocat");

        verify(github).fetchRepoLanguages("octocat", "tie-24");
        verify(github, never()).fetchRepoLanguages("octocat", "tie-25");
    }

    @Test
    void everyRepoReportingNoLanguagesGivesAnEmptyBreakdown() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(numberedRepos(3, false)));
        when(github.fetchRepoLanguages(eq("octocat"), anyString())).thenReturn(Map.of());

        assertThat(service.languages("octocat").languages()).isEmpty();
    }

    // --- "Other" bucket boundary --------------------------------------------------------------

    @Test
    void exactlyTwoPercentIsKeptOutOfTheOtherBucket() {
        List<LanguageShare> shares =
                InsightsService.toShares(Map.of("Java", 980L, "Shell", 20L), true);

        assertThat(shares).extracting(LanguageShare::language).containsExactly("Java", "Shell");
        assertThat(shares.getLast().percent()).isEqualTo(2.0);
    }

    @Test
    void justUnderTwoPercentIsFoldedIntoOther() {
        List<LanguageShare> shares =
                InsightsService.toShares(Map.of("Java", 981L, "Shell", 19L), true);

        assertThat(shares).extracting(LanguageShare::language).containsExactly("Java", "Other");
        assertThat(shares.getLast().percent()).isEqualTo(1.9);
        assertThat(shares.getLast().bytes()).isEqualTo(19L);
    }

    @Test
    void aShareRoundingUpToTwoPercentSurvivesGrouping() {
        // 19.6 bytes-worth: 1.96% rounds to 2.0 and is compared after rounding
        List<LanguageShare> shares =
                InsightsService.toShares(Map.of("Java", 9804L, "Shell", 196L), true);

        assertThat(shares).extracting(LanguageShare::language).containsExactly("Java", "Shell");
    }

    @Test
    void tiedPercentagesAreOrderedByLanguageName() {
        List<LanguageShare> shares =
                InsightsService.toShares(Map.of("Zig", 100L, "Ada", 100L, "Ruby", 100L), false);

        assertThat(shares).extracting(LanguageShare::language)
                .containsExactly("Ada", "Ruby", "Zig");
    }

    // --- activity window ----------------------------------------------------------------------

    @Test
    void activityCapsAtTenRepos() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(numberedRepos(11, false)));
        when(github.fetchCommitActivity(eq("octocat"), anyString()))
                .thenReturn(new int[] {1});

        service.activity("octocat");

        verify(github, times(10)).fetchCommitActivity(eq("octocat"), anyString());
        verify(github, never()).fetchCommitActivity("octocat", "repo-10");
    }

    @Test
    void historyLongerThan52WeeksKeepsOnlyTheNewestWindow() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(List.of(repo("a", 5,
                false))));
        int[] sixtyWeeks = new int[60];
        for (int i = 0; i < 60; i++) {
            sixtyWeeks[i] = i; // oldest = 0 ... newest = 59
        }
        when(github.fetchCommitActivity("octocat", "a")).thenReturn(sixtyWeeks);

        ActivityResponse response = service.activity("octocat");

        assertThat(response.weeks()).hasSize(52);
        assertThat(response.weeks().get(51)).isEqualTo(59);
        assertThat(response.weeks().get(50)).isEqualTo(58);
        assertThat(response.weeks().getFirst()).isEqualTo(8);
    }

    @Test
    void emptyWeekArrayContributesNothingAndIsNotPending() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(List.of(repo("a", 5, false), repo("b", 1, false))));
        when(github.fetchCommitActivity("octocat", "a")).thenReturn(new int[0]);
        when(github.fetchCommitActivity("octocat", "b")).thenReturn(new int[] {3});

        ActivityResponse response = service.activity("octocat");

        assertThat(response.pending()).isFalse();
        assertThat(response.weeks().get(51)).isEqualTo(3);
        assertThat(response.weeks().subList(0, 51)).allMatch(total -> total == 0);
    }

    @Test
    void singleWeekOfHistoryLandsOnTheNewestSlot() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(List.of(repo("a", 5, false))));
        when(github.fetchCommitActivity("octocat", "a")).thenReturn(new int[] {42});

        ActivityResponse response = service.activity("octocat");

        assertThat(response.weeks().get(51)).isEqualTo(42);
        assertThat(response.weeks().stream().mapToInt(Integer::intValue).sum()).isEqualTo(42);
    }

    @Test
    void allReposStillComputingGivesZeroesAndPending() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(numberedRepos(3, false)));
        when(github.fetchCommitActivity(eq("octocat"), anyString())).thenReturn(null);

        ActivityResponse response = service.activity("octocat");

        assertThat(response.pending()).isTrue();
        assertThat(response.weeks()).hasSize(52).allMatch(total -> total == 0);
        verify(cacheRepo, never()).save(any(ApiCache.class));
    }

    @Test
    void pendingActivityIsRecomputedOnEverySubsequentCall() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(List.of(repo("a", 5, false))));
        when(github.fetchCommitActivity("octocat", "a")).thenReturn(null);

        service.activity("octocat");
        service.activity("octocat");

        verify(github, times(2)).fetchCommitActivity("octocat", "a");
        verify(cacheRepo, never()).save(any(ApiCache.class));
    }

    @Test
    void onlyForkedReposMeansNoUpstreamActivityCalls() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(List.of(repo("f1", 9, true), repo("f2", 8, true))));

        ActivityResponse response = service.activity("octocat");

        verify(github, never()).fetchCommitActivity(anyString(), anyString());
        assertThat(response.pending()).isFalse();
        assertThat(response.weeks()).allMatch(total -> total == 0);
    }

    // --- repos that disappeared between snapshot and insight fetch ------------------------------

    @Test
    void repoDeletedSinceTheSnapshotIsSkippedInTheLanguageChart() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(
                snapshot(List.of(repo("gone", 9, false), repo("alive", 5, false))));
        when(github.fetchRepoLanguages("octocat", "gone"))
                .thenThrow(new GithubResourceNotFoundException("octocat/gone"));
        when(github.fetchRepoLanguages("octocat", "alive")).thenReturn(Map.of("Java", 100L));

        LanguagesResponse response = service.languages("octocat");

        assertThat(response.languages()).singleElement()
                .extracting(LanguageShare::language).isEqualTo("Java");
    }

    @Test
    void everyRepoDeletedSinceTheSnapshotGivesAnEmptyChartNotAnError() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(numberedRepos(3, false)));
        when(github.fetchRepoLanguages(eq("octocat"), anyString()))
                .thenThrow(new GithubResourceNotFoundException("octocat/repo-0"));

        assertThat(service.languages("octocat").languages()).isEmpty();
    }

    @Test
    void repoDeletedSinceTheSnapshotIsSkippedInActivityWithoutMarkingPending() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(
                snapshot(List.of(repo("gone", 9, false), repo("alive", 5, false))));
        when(github.fetchCommitActivity("octocat", "gone"))
                .thenThrow(new GithubResourceNotFoundException("octocat/gone"));
        when(github.fetchCommitActivity("octocat", "alive")).thenReturn(new int[] {6});

        ActivityResponse response = service.activity("octocat");

        assertThat(response.pending()).isFalse();
        assertThat(response.weeks().get(51)).isEqualTo(6);
        verify(cacheRepo).save(any(ApiCache.class));
    }

    @Test
    void reposWithNoCommitHistoryAreNotReportedAsPending() {
        // GithubClient returns an empty series (not null) for a repo without commits
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(numberedRepos(2, false)));
        when(github.fetchCommitActivity(eq("octocat"), anyString())).thenReturn(new int[0]);

        ActivityResponse response = service.activity("octocat");

        assertThat(response.pending()).isFalse();
        assertThat(response.weeks()).allMatch(total -> total == 0);
        verify(cacheRepo).save(any(ApiCache.class));
    }

    // --- cache keys and normalization ---------------------------------------------------------

    @Test
    void usernameIsTrimmedAndLowercasedForCacheKeyAndUpstreamCalls() {
        when(snapshots.getSnapshot("octocat", false))
                .thenReturn(snapshot(List.of(repo("a", 5, false))));
        when(github.fetchRepoLanguages("octocat", "a")).thenReturn(Map.of("Java", 10L));

        service.languages("  OctoCat  ");

        verify(cacheRepo).findByCacheKey("languages:octocat");
        verify(snapshots).getSnapshot("octocat", false);
        verify(github).fetchRepoLanguages("octocat", "a");
        ArgumentCaptor<ApiCache> saved = ArgumentCaptor.forClass(ApiCache.class);
        verify(cacheRepo).save(saved.capture());
        assertThat(saved.getValue().getCacheKey()).isEqualTo("languages:octocat");
    }

    @Test
    void activityUsesItsOwnCacheKeyNamespace() {
        when(snapshots.getSnapshot("octocat", false)).thenReturn(snapshot(List.of()));

        service.activity("OCTOCAT");

        verify(cacheRepo).findByCacheKey("activity:octocat");
    }

    @Test
    void freshLanguagesRowSkipsSnapshotAndUpstreamEntirely() {
        ApiCache row = new ApiCache();
        row.setCacheKey("languages:octocat");
        row.setFetchedAt(NOW.minus(Duration.ofMinutes(59)));
        row.setPayload("{\"languages\":[{\"language\":\"Rust\",\"percent\":100.0,"
                + "\"bytes\":2048}]}");
        when(cacheRepo.findByCacheKey("languages:octocat")).thenReturn(Optional.of(row));

        LanguagesResponse response = service.languages("octocat");

        assertThat(response.fromCache()).isTrue();
        assertThat(response.fetchedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(59)));
        assertThat(response.languages()).singleElement()
                .extracting(LanguageShare::language).isEqualTo("Rust");
        verifyNoInteractions(snapshots);
        verifyNoInteractions(github);
    }

    @Test
    void freshActivityRowIsServedWithoutRecomputing() {
        ApiCache row = new ApiCache();
        row.setCacheKey("activity:octocat");
        row.setFetchedAt(NOW.minus(Duration.ofMinutes(5)));
        row.setPayload("{\"weeks\":" + Arrays.toString(new int[52]) + ",\"pending\":false}");
        when(cacheRepo.findByCacheKey("activity:octocat")).thenReturn(Optional.of(row));

        ActivityResponse response = service.activity("octocat");

        assertThat(response.fromCache()).isTrue();
        assertThat(response.weeks()).hasSize(52);
        assertThat(response.pending()).isFalse();
        verifyNoInteractions(snapshots);
        verifyNoInteractions(github);
    }

    // --- helpers --------------------------------------------------------------------------------

    private static List<Repo> numberedRepos(int count, boolean fork) {
        List<Repo> repos = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            repos.add(repo("repo-" + i, 1000 - i, fork));
        }
        return repos;
    }

    private static SnapshotResponse snapshot(List<Repo> repos) {
        return new SnapshotResponse(
                new Profile("octocat", null, "https://a", null, 1, repos.size(),
                        "https://github.com/octocat"),
                List.copyOf(repos), NOW, false, false);
    }

    private static Repo repo(String name, int stars, boolean fork) {
        return new Repo(name, null, "Java", stars, 0, NOW, fork, false,
                "https://github.com/octocat/" + name, null,
                "https://github.com/octocat/" + name + ".git");
    }
}
