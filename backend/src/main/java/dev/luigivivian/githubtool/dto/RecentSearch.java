package dev.luigivivian.githubtool.dto;

import java.time.Instant;

public record RecentSearch(String username, Instant searchedAt) {
}
