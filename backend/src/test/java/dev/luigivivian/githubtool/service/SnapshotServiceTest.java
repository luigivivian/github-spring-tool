package dev.luigivivian.githubtool.service;

import dev.luigivivian.githubtool.entity.SearchRecord;
import dev.luigivivian.githubtool.entity.Snapshot;
import dev.luigivivian.githubtool.repository.SearchRecordRepository;
import dev.luigivivian.githubtool.repository.SnapshotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.luigivivian.githubtool.dto.Profile;
import dev.luigivivian.githubtool.dto.Repo;
import dev.luigivivian.githubtool.dto.SnapshotPayload;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.config.AppProperties;
import dev.luigivivian.githubtool.client.GithubClient;
import dev.luigivivian.githubtool.client.GithubRepoDto;
import dev.luigivivian.githubtool.client.GithubUserDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnapshotServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    private final GithubClient github = mock(GithubClient.class);
    private final SnapshotRepository snapshots = mock(SnapshotRepository.class);
    private final SearchRecordRepository searches = mock(SearchRecordRepository.class);
    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final AppProperties props = new AppProperties("https://api.github.com", "",
            Duration.ofMinutes(10));

    private SnapshotService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotService(github, snapshots, searches, mapper, props,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(snapshots.save(any(Snapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cacheMissFetchesLiveAndSavesSnapshot() {
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.empty());
        stubGithub();

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.fromCache()).isFalse();
        assertThat(response.fetchedAt()).isEqualTo(NOW);
        assertThat(response.repos()).hasSize(1);

        ArgumentCaptor<Snapshot> captor = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshots).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("octocat");
        assertThat(captor.getValue().getPayload()).contains("\"login\":\"octocat\"");
        verify(searches).save(any(SearchRecord.class));
    }

    @Test
    void freshSnapshotServedFromCacheWithoutGithubCall() throws Exception {
        when(snapshots.findByUsername("octocat"))
                .thenReturn(Optional.of(storedSnapshot(NOW.minus(Duration.ofMinutes(5)))));

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.fromCache()).isTrue();
        assertThat(response.fetchedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
        assertThat(response.profile().login()).isEqualTo("octocat");
        verifyNoInteractions(github);
        verify(searches).save(any(SearchRecord.class));
    }

    @Test
    void expiredSnapshotTriggersRefetch() throws Exception {
        when(snapshots.findByUsername("octocat"))
                .thenReturn(Optional.of(storedSnapshot(NOW.minus(Duration.ofMinutes(11)))));
        stubGithub();

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.fromCache()).isFalse();
        assertThat(response.fetchedAt()).isEqualTo(NOW);
        verify(github).fetchUser("octocat");
    }

    @Test
    void refreshFlagBypassesFreshCache() throws Exception {
        when(snapshots.findByUsername("octocat"))
                .thenReturn(Optional.of(storedSnapshot(NOW.minus(Duration.ofMinutes(1)))));
        stubGithub();

        SnapshotResponse response = service.getSnapshot("octocat", true);

        assertThat(response.fromCache()).isFalse();
        verify(github).fetchUser("octocat");
    }

    @Test
    void usernameNormalizedToLowercase() {
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.empty());
        stubGithub();

        service.getSnapshot("  OctoCat ", false);

        verify(snapshots).findByUsername("octocat");
        verify(github).fetchUser("octocat");
    }

    @Test
    void truncatedWhenCapReachedAndMoreReposExist() {
        when(snapshots.findByUsername("octocat")).thenReturn(Optional.empty());
        when(github.fetchUser("octocat")).thenReturn(
                new GithubUserDto("octocat", "The Octocat", "https://a", "bio", 10, 450,
                        "https://github.com/octocat"));
        when(github.fetchRepos("octocat")).thenReturn(java.util.Collections.nCopies(300, repoDto()));

        SnapshotResponse response = service.getSnapshot("octocat", false);

        assertThat(response.truncated()).isTrue();
    }

    private void stubGithub() {
        when(github.fetchUser("octocat")).thenReturn(
                new GithubUserDto("octocat", "The Octocat", "https://a", "bio", 10, 1,
                        "https://github.com/octocat"));
        when(github.fetchRepos("octocat")).thenReturn(List.of(repoDto()));
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
