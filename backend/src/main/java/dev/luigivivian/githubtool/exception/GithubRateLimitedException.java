package dev.luigivivian.githubtool.exception;

public class GithubRateLimitedException extends RuntimeException {

    private final Long retryAfterSeconds;

    public GithubRateLimitedException(Long retryAfterSeconds) {
        super("GitHub API rate limit exhausted");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
