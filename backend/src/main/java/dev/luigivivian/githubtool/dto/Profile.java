package dev.luigivivian.githubtool.dto;

public record Profile(
        String login,
        String name,
        String avatarUrl,
        String bio,
        int followers,
        int publicRepos,
        String htmlUrl) {
}
