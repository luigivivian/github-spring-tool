package dev.luigivivian.githubtool.dto;

import java.time.Instant;
import java.util.List;

public record RepoDetailResponse(
        String readme,
        List<ReleaseInfo> releases,
        List<ContributorInfo> contributors,
        List<LanguageShare> languages,
        Instant fetchedAt,
        boolean fromCache) {
}
