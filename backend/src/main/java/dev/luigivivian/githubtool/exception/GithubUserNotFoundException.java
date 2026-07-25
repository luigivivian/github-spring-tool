package dev.luigivivian.githubtool.exception;

public class GithubUserNotFoundException extends RuntimeException {

    private final String username;

    public GithubUserNotFoundException(String username) {
        super("GitHub user not found: " + username);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
