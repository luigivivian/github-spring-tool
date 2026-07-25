package dev.luigivivian.githubtool.dto;

import java.time.Instant;
import java.util.List;

public record ActivityResponse(List<Integer> weeks, boolean pending, Instant fetchedAt,
        boolean fromCache) {
}
