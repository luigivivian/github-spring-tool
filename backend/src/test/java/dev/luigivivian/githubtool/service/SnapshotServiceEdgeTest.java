package dev.luigivivian.githubtool.service;

import dev.luigivivian.githubtool.entity.SearchRecord;
import dev.luigivivian.githubtool.entity.Snapshot;
import dev.luigivivian.githubtool.repository.SearchRecordRepository;
import dev.luigivivian.githubtool.repository.SnapshotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.luigivivian.githubtool.dto.Profile;
import dev.luigivivian.githubtool.dto.RecentSearch;
import dev.luigivivian.githubtool.dto.Repo;
import dev.luigivivian.githubtool.dto.SnapshotPayload;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.config.AppProperties;
import dev.luigivivian.githubtool.client.GithubClient;
import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.client.GithubRepoDto;
import dev.luigivivian.githubtool.client.GithubUserDto;
import dev.luigivivian.githubtool.exception.GithubUserNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Cache-window boundaries, empty results and failure paths for FR-015 / FR-016. */
class SnapshotServiceEdgeTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    private final GithubClient github = mock(GithubClient.class);
    private final SnapshotRepository snapshots = mock(SnapshotRepository.class);
    private final SearchRecordRepository searches = mock(SearchRecordRepository.class);
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final AppProperties props = new AppProperties("https://api.github.com", "", TTL);

    private SnapshotService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotService(github, snapshots, searches, mapper, props,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void snapshotExactlyAtTtlBoundaryIsStillFresh() throws Exception {
        when(snapshots.findByUsername("octocat"))
                .thenReturn(Optional.of(storedSnapshot(NOW.minus(TTL))));

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.fromCache()).isTrue();
        verifyNoInteractions(github);
    }

    @Test
    void snapshotOneSecondPastTtlIsRefetched() throws Exception {
        when(snapshots.findByUsername("octocat"))
                .thenReturn(Optional.of(storedSnapshot(NOW.minus(TTL).minusSeconds(1))));
        stubGithub(1, List.of(repoDto()));

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.fromCache()).isFalse();
        verify(github).fetchUser("octocat");
    }

    @Test
    void cacheHitRecordsSearchAtCurrentTimeNotFetchTime() throws Exception {
        Instant fetchedAt = NOW.minus(Duration.ofMinutes(3));
        when(snapshots.findByUsername("octocat"))
                .thenReturn(Optional.of(storedSnapshot(fetchedAt)));

        SnapshotResponse response = service.getSnapshot("octocat", false);

        ArgumentCaptor<SearchRecord> captor = ArgumentCaptor.forClass(SearchRecord.class);
        verify(searches).save(captor.capture());
        assertThat(captor.getValue().getSearchedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getUsername()).isEqualTo("octocat");
        assertThat(response.fetchedAt()).isEqualTo(fetchedAt);
        verify(snapshots, never()).save(any(Snapshot.class));
    }

    @Test
    void userWithZeroPublicReposProducesEmptyRepoListAndNoTruncation() {
        when(snapshots.findByUsername("emptyuser")).thenReturn(Optional.empty());
        when(github.fetchUser("emptyuser")).thenReturn(
                new GithubUserDto("emptyuser", null, "https://a", null, 0, 0,
                        "https://github.com/emptyuser"));
        when(github.fetchRepos("emptyuser")).thenReturn(List.of());

        SnapshotResponse response = service.getSnapshot("emptyuser", false);

        assertThat(response.repos()).isEmpty();
        assertThat(response.truncated()).isFalse();
        assertThat(response.fromCache()).isFalse();
        verify(snapshots).save(any(Snapshot.class));
        verify(searches).save(any(SearchRecord.class));
    }

    @Test
    void exactlyThreeHundredReposWithMatchingCountIsNotTruncated() {
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.empty());
        stubGithub(300, Collections.nCopies(300, repoDto()));

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.repos()).hasSize(300);
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void justUnderCapIsNotTruncated() {
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.empty());
        stubGithub(299, Collections.nCopies(299, repoDto()));

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.truncated()).isFalse();
    }

    @Test
    void unknownUserRecordsNeitherSnapshotNorSearch() {
        when(snapshots.findByUsername("ghost-user")).thenReturn(Optional.empty());
        when(github.fetchUser("ghost-user")).thenThrow(new GithubUserNotFoundException("ghost-user"));

        assertThatThrownBy(() -> service.getSnapshot("ghost-user", false))
                .isInstanceOf(GithubUserNotFoundException.class);

        verify(snapshots, never()).save(any(Snapshot.class));
        verify(searches, never()).save(any(SearchRecord.class));
    }

    @Test
    void rateLimitDuringRepoFetchRecordsNothing() {
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.empty());
        when(github.fetchUser("octocat")).thenReturn(
                new GithubUserDto("octocat", "The Octocat", "https://a", "bio", 1, 5,
                        "https://github.com/octocat"));
        when(github.fetchRepos("octocat")).thenThrow(new GithubRateLimitedException(30L));

        assertThatThrownBy(() -> service.getSnapshot("octocat", false))
                .isInstanceOf(GithubRateLimitedException.class);

        verify(snapshots, never()).save(any(Snapshot.class));
        verify(searches, never()).save(any(SearchRecord.class));
    }

    @Test
    void refetchReusesExistingRowInsteadOfInsertingDuplicate() throws Exception {
        Snapshot existing = storedSnapshot(NOW.minus(Duration.ofMinutes(30)));
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.of(existing));
        stubGithub(1, List.of(repoDto()));

        service.getSnapshot("octocat", false);

        ArgumentCaptor<Snapshot> captor = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshots).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getFetchedAt()).isEqualTo(NOW);
    }

    @Test
    void refreshOnColdCacheStillFetchesAndStores() {
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.empty());
        stubGithub(1, List.of(repoDto()));

        SnapshotResponse response = service.getSnapshot("octocat", true);

        assertThat(response.fromCache()).isFalse();
        verify(github).fetchUser("octocat");
        verify(snapshots).save(any(Snapshot.class));
    }

    @Test
    void recentSearchesRequestsTenNewestAndMapsView() {
        when(searches.findRecent(any(Pageable.class))).thenReturn(List.of(
                view("octocat", NOW), view("torvalds", NOW.minusSeconds(60))));

        List<RecentSearch> recent = service.recentSearches();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(searches).findRecent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(PageRequest.of(0, 10));
        assertThat(recent).containsExactly(
                new RecentSearch("octocat", NOW),
                new RecentSearch("torvalds", NOW.minusSeconds(60)));
    }

    @Test
    void recentSearchesReturnsEmptyListWhenNoHistory() {
        when(searches.findRecent(any(Pageable.class))).thenReturn(List.of());

        assertThat(service.recentSearches()).isEmpty();
    }

    @Test
    void corruptCachedPayloadFailsFastWithoutLeakingJson() throws Exception {
        Snapshot corrupt = storedSnapshot(NOW.minus(Duration.ofMinutes(1)));
        corrupt.setPayload("{not-json");
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.of(corrupt));

        assertThatThrownBy(() -> service.getSnapshot("octocat", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Corrupt snapshot payload");
    }

    private static SearchRecordRepository.RecentSearchView view(String username, Instant at) {
        return new SearchRecordRepository.RecentSearchView() {
            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public Instant getSearchedAt() {
                return at;
            }
        };
    }

    private void stubGithub(int publicRepos, List<GithubRepoDto> repos) {
        when(github.fetchUser("octocat")).thenReturn(
                new GithubUserDto("octocat", "The Octocat", "https://a", "bio", 10, publicRepos,
                        "https://github.com/octocat"));
        when(github.fetchRepos("octocat")).thenReturn(repos);
    }

    private static GithubRepoDto repoDto() {
        return new GithubRepoDto("hello", "desc", "Java", 5, 2,
                Instant.parse("2026-07-20T10:00:00Z"), false, false,
                "https://github.com/octocat/hello", null,
                "https://github.com/octocat/hello.git");
    }

    private Snapshot storedSnapshot(Instant fetchedAt) throws Exception {
        SnapshotPayload payload = new SnapshotPayload(
                new Profile("octocat", "The Octocat", "https://a", "bio", 10, 1,
                        "https://github.com/octocat"),
                List.of(new Repo("hello", "desc", "Java", 5, 2,
                        Instant.parse("2026-07-20T10:00:00Z"), false, false,
                        "https://github.com/octocat/hello", null,
                        "https://github.com/octocat/hello.git")));
        Snapshot snapshot = new Snapshot();
        snapshot.setUsername("octocat");
        snapshot.setFetchedAt(fetchedAt);
        snapshot.setTruncated(false);
        snapshot.setPayload(mapper.writeValueAsString(payload));
        return snapshot;
    }
}
