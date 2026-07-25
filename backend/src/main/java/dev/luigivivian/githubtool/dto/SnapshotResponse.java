package dev.luigivivian.githubtool.dto;

import java.time.Instant;
import java.util.List;

public record SnapshotResponse(
        Profile profile,
        List<Repo> repos,
        Instant fetchedAt,
        boolean fromCache,
        boolean truncated) {
}
