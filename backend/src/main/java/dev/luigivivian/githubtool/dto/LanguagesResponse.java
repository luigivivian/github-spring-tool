package dev.luigivivian.githubtool.dto;

import java.time.Instant;
import java.util.List;

public record LanguagesResponse(List<LanguageShare> languages, Instant fetchedAt, boolean fromCache) {
}
