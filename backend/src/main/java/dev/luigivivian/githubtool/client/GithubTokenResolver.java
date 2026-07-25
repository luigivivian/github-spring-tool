package dev.luigivivian.githubtool.client;

import java.util.Optional;

/**
 * Supplies the GitHub token for the current request context. Absent value falls back to the
 * operator-level app.github.token inside GithubClient.
 */
public interface GithubTokenResolver {

    Optional<String> currentToken();
}
