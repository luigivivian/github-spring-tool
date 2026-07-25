package dev.luigivivian.githubtool.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepoDto(
        String name,
        String description,
        String language,
        @JsonProperty("stargazers_count") int stars,
        @JsonProperty("forks_count") int forks,
        @JsonProperty("updated_at") Instant updatedAt,
        boolean fork,
        boolean archived,
        @JsonProperty("html_url") String htmlUrl,
        String homepage,
        @JsonProperty("clone_url") String cloneUrl) {
}
