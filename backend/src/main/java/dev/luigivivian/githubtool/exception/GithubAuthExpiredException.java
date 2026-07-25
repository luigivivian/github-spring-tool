package dev.luigivivian.githubtool.exception;

/** The signed-in visitor's GitHub token was rejected upstream (revoked or expired). */
public class GithubAuthExpiredException extends RuntimeException {

    public GithubAuthExpiredException() {
        super("GitHub rejected the visitor's OAuth token");
    }
}
