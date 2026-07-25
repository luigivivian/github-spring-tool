package dev.luigivivian.githubtool.service;

import dev.luigivivian.githubtool.client.GithubClient;
import dev.luigivivian.githubtool.dto.ContributorInfo;
import dev.luigivivian.githubtool.dto.LanguageShare;
import dev.luigivivian.githubtool.dto.ReleaseInfo;
import dev.luigivivian.githubtool.dto.RepoDetailResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class RepoDetailService {

    static final int RELEASE_CAP = 5;
    static final int CONTRIBUTOR_CAP = 10;

    public record DetailData(String readme, List<ReleaseInfo> releases,
            List<ContributorInfo> contributors, List<LanguageShare> languages) {
    }

    private final GithubClient github;
    private final ApiCacheService cache;

    public RepoDetailService(GithubClient github, ApiCacheService cache) {
        this.github = github;
        this.cache = cache;
    }

    public RepoDetailResponse detail(String owner, String repo) {
        String key = "repo:" + owner.toLowerCase(Locale.ROOT) + "/" + repo.toLowerCase(Locale.ROOT);
        ApiCacheService.Cached<DetailData> cached = cache.get(key, DetailData.class, () -> {
            // languages first: it throws GithubResourceNotFoundException for a missing repo,
            // so a deleted repo 404s cleanly instead of being masked as "no README"
            List<LanguageShare> languages =
                    InsightsService.toShares(github.fetchRepoLanguages(owner, repo), false);
            String readme = github.fetchReadme(owner, repo);
            List<ReleaseInfo> releases = github.fetchReleases(owner, repo, RELEASE_CAP).stream()
                    .map(r -> new ReleaseInfo(r.name() != null && !r.name().isBlank()
                            ? r.name() : r.tagName(), r.publishedAt(), r.htmlUrl()))
                    .toList();
            List<ContributorInfo> contributors =
                    github.fetchContributors(owner, repo, CONTRIBUTOR_CAP).stream()
                            .map(c -> new ContributorInfo(c.login(), c.avatarUrl(),
                                    c.contributions(), c.htmlUrl()))
                            .toList();
            return ApiCacheService.Loaded.of(new DetailData(readme, releases, contributors,
                    languages));
        });
        DetailData data = cached.value();
        return new RepoDetailResponse(data.readme(), data.releases(), data.contributors(),
                data.languages(), cached.fetchedAt(), cached.fromCache());
    }
}
