package dev.luigivivian.githubtool.service;

import dev.luigivivian.githubtool.client.GithubClient;
import dev.luigivivian.githubtool.exception.GithubResourceNotFoundException;
import dev.luigivivian.githubtool.dto.ActivityResponse;
import dev.luigivivian.githubtool.dto.LanguageShare;
import dev.luigivivian.githubtool.dto.LanguagesResponse;
import dev.luigivivian.githubtool.dto.Repo;
import dev.luigivivian.githubtool.dto.SnapshotResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InsightsService {

    static final int LANGUAGE_REPO_CAP = 25;
    static final int ACTIVITY_REPO_CAP = 10;
    static final int WEEKS = 52;
    static final double OTHER_THRESHOLD_PERCENT = 2.0;

    public record LanguagesData(List<LanguageShare> languages) {
    }

    public record ActivityData(List<Integer> weeks, boolean pending) {
    }

    private final SnapshotService snapshotService;
    private final GithubClient github;
    private final ApiCacheService cache;

    public InsightsService(SnapshotService snapshotService, GithubClient github,
            ApiCacheService cache) {
        this.snapshotService = snapshotService;
        this.github = github;
        this.cache = cache;
    }

    public LanguagesResponse languages(String username) {
        String key = normalize(username);
        ApiCacheService.Cached<LanguagesData> cached =
                cache.get("languages:" + key, LanguagesData.class, () -> {
                    SnapshotResponse snapshot = snapshotService.getSnapshot(key, false);
                    Map<String, Long> totals = new LinkedHashMap<>();
                    for (Repo repo : topRepos(snapshot, LANGUAGE_REPO_CAP)) {
                        try {
                            github.fetchRepoLanguages(key, repo.name())
                                    .forEach((language, bytes) ->
                                            totals.merge(language, bytes, Long::sum));
                        } catch (GithubResourceNotFoundException e) {
                            // repo renamed/deleted since the snapshot — skip, keep the chart alive
                        }
                    }
                    return ApiCacheService.Loaded.of(new LanguagesData(toShares(totals, true)));
                });
        return new LanguagesResponse(cached.value().languages(), cached.fetchedAt(),
                cached.fromCache());
    }

    public ActivityResponse activity(String username) {
        String key = normalize(username);
        ApiCacheService.Cached<ActivityData> cached =
                cache.get("activity:" + key, ActivityData.class, () -> {
                    SnapshotResponse snapshot = snapshotService.getSnapshot(key, false);
                    int[] sums = new int[WEEKS];
                    boolean pending = false;
                    for (Repo repo : topRepos(snapshot, ACTIVITY_REPO_CAP)) {
                        int[] weeks;
                        try {
                            weeks = github.fetchCommitActivity(key, repo.name());
                        } catch (GithubResourceNotFoundException e) {
                            continue; // repo renamed/deleted since the snapshot
                        }
                        if (weeks == null) {
                            pending = true;
                            continue;
                        }
                        // align newest week at the end of the 52-slot window
                        int span = Math.min(weeks.length, WEEKS);
                        for (int i = 0; i < span; i++) {
                            sums[WEEKS - 1 - i] += weeks[weeks.length - 1 - i];
                        }
                    }
                    List<Integer> list = new ArrayList<>(WEEKS);
                    for (int total : sums) {
                        list.add(total);
                    }
                    // partial (still computing) results are served but never cached
                    return new ApiCacheService.Loaded<>(new ActivityData(list, pending), !pending);
                });
        ActivityData data = cached.value();
        return new ActivityResponse(data.weeks(), data.pending(), cached.fetchedAt(),
                cached.fromCache());
    }

    /** Shared percentage math; groupOther folds languages below 2% into an "Other" bucket. */
    public static List<LanguageShare> toShares(Map<String, Long> totals, boolean groupOther) {
        long sum = totals.values().stream().mapToLong(Long::longValue).sum();
        if (sum == 0) {
            return List.of();
        }
        List<LanguageShare> shares = totals.entrySet().stream()
                .map(e -> new LanguageShare(e.getKey(),
                        round1(100.0 * e.getValue() / sum), e.getValue()))
                .sorted(Comparator.comparingDouble(LanguageShare::percent).reversed()
                        .thenComparing(LanguageShare::language))
                .toList();
        if (!groupOther) {
            return shares;
        }
        List<LanguageShare> result = new ArrayList<>();
        long otherBytes = 0;
        for (LanguageShare share : shares) {
            if (share.percent() >= OTHER_THRESHOLD_PERCENT) {
                result.add(share);
            } else {
                otherBytes += share.bytes();
            }
        }
        if (otherBytes > 0) {
            result.add(new LanguageShare("Other", round1(100.0 * otherBytes / sum), otherBytes));
        }
        return result;
    }

    private static List<Repo> topRepos(SnapshotResponse snapshot, int cap) {
        return snapshot.repos().stream()
                .filter(repo -> !repo.fork())
                .sorted(Comparator.comparingInt(Repo::stars).reversed())
                .limit(cap)
                .toList();
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
