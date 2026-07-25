package dev.luigivivian.githubtool.dto;

import java.time.Instant;

public record ReleaseInfo(String name, Instant publishedAt, String htmlUrl) {
}
