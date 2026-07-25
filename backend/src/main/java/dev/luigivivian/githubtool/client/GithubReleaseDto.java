package dev.luigivivian.githubtool.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubReleaseDto(
        String name,
        @JsonProperty("tag_name") String tagName,
        @JsonProperty("published_at") Instant publishedAt,
        @JsonProperty("html_url") String htmlUrl) {
}
