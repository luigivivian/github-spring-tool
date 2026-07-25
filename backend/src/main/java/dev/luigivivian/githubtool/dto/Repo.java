package dev.luigivivian.githubtool.dto;

import java.time.Instant;

public record Repo(
        String name,
        String description,
        String language,
        int stars,
        int forks,
        Instant updatedAt,
        boolean fork,
        boolean archived,
        String htmlUrl,
        String homepage,
        String cloneUrl) {
}
