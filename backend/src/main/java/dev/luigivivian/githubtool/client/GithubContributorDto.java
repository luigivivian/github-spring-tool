package dev.luigivivian.githubtool.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubContributorDto(
        String login,
        @JsonProperty("avatar_url") String avatarUrl,
        int contributions,
        @JsonProperty("html_url") String htmlUrl) {
}
