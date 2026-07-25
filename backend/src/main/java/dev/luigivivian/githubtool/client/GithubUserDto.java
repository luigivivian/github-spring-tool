package dev.luigivivian.githubtool.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubUserDto(
        String login,
        String name,
        @JsonProperty("avatar_url") String avatarUrl,
        String bio,
        int followers,
        @JsonProperty("public_repos") int publicRepos,
        @JsonProperty("html_url") String htmlUrl) {
}
