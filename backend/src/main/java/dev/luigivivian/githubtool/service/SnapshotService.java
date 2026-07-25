package dev.luigivivian.githubtool.service;

import dev.luigivivian.githubtool.entity.SearchRecord;
import dev.luigivivian.githubtool.entity.Snapshot;
import dev.luigivivian.githubtool.repository.SearchRecordRepository;
import dev.luigivivian.githubtool.repository.SnapshotRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.luigivivian.githubtool.dto.Profile;
import dev.luigivivian.githubtool.dto.RecentSearch;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotService {

    private final GithubClient github;
    private final SnapshotRepository snapshots;
    private final SearchRecordRepository searches;
    private final ObjectMapper mapper;
    private final AppProperties props;
    private final Clock clock;

    public SnapshotService(GithubClient github, SnapshotRepository snapshots,
            SearchRecordRepository searches, ObjectMapper mapper, AppProperties props, Clock clock) {
        this.github = github;
        this.snapshots = snapshots;
        this.searches = searches;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    // No @Transactional here on purpose: the GitHub calls must not hold a DB
    // connection open; each repository call runs in its own short transaction.
    public SnapshotResponse getSnapshot(String username, boolean refresh) {
        String key = username.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        Optional<Snapshot> existing = snapshots.findByUsername(key);

        if (!refresh && existing.isPresent() && isFresh(existing.get(), now)) {
            Snapshot snapshot = existing.get();
            searches.save(new SearchRecord(key, now));
            SnapshotPayload payload = readPayload(snapshot.getPayload());
            return new SnapshotResponse(payload.profile(), payload.repos(),
                    snapshot.getFetchedAt(), true, snapshot.isTruncated());
        }

        GithubUserDto user = github.fetchUser(key);
        List<GithubRepoDto> repoDtos = github.fetchRepos(key);
        boolean truncated = repoDtos.size() >= GithubClient.PER_PAGE * GithubClient.MAX_PAGES
                && user.publicRepos() > repoDtos.size();

        SnapshotPayload payload = new SnapshotPayload(toProfile(user),
                repoDtos.stream().map(SnapshotService::toRepo).toList());

        String json = writePayload(payload);
        Snapshot snapshot = existing.orElseGet(Snapshot::new);
        snapshot.setUsername(key);
        snapshot.setFetchedAt(now);
        snapshot.setTruncated(truncated);
        snapshot.setPayload(json);
        try {
            snapshots.save(snapshot);
        } catch (DataIntegrityViolationException e) {
            // concurrent first-time search for the same user: update the winner's row
            snapshots.findByUsername(key).ifPresent(winner -> {
                winner.setFetchedAt(now);
                winner.setTruncated(truncated);
                winner.setPayload(json);
                snapshots.save(winner);
            });
        }
        searches.save(new SearchRecord(key, now));

        return new SnapshotResponse(payload.profile(), payload.repos(), now, false, truncated);
    }

    @Transactional(readOnly = true)
    public List<RecentSearch> recentSearches() {
        return searches.findRecent(PageRequest.of(0, 10)).stream()
                .map(view -> new RecentSearch(view.getUsername(), view.getSearchedAt()))
                .toList();
    }

    private boolean isFresh(Snapshot snapshot, Instant now) {
        return Duration.between(snapshot.getFetchedAt(), now).compareTo(props.cacheTtl()) <= 0;
    }

    private SnapshotPayload readPayload(String json) {
        try {
            return mapper.readValue(json, SnapshotPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt snapshot payload", e);
        }
    }

    private String writePayload(SnapshotPayload payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unserializable snapshot payload", e);
        }
    }

    private static Profile toProfile(GithubUserDto user) {
        return new Profile(user.login(), user.name(), user.avatarUrl(), user.bio(),
                user.followers(), user.publicRepos(), user.htmlUrl());
    }

    private static Repo toRepo(GithubRepoDto dto) {
        return new Repo(dto.name(), dto.description(), dto.language(), dto.stars(), dto.forks(),
                dto.updatedAt(), dto.fork(), dto.archived(), dto.htmlUrl(), dto.homepage(),
                dto.cloneUrl());
    }
}
