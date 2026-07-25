package dev.luigivivian.githubtool.exception;

public class GithubUnavailableException extends RuntimeException {

    public GithubUnavailableException(Throwable cause) {
        super("GitHub API request failed", cause);
    }
}
