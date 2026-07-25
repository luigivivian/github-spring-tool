package dev.luigivivian.githubtool.exception;

public class GithubResourceNotFoundException extends RuntimeException {

    private final String resource;

    public GithubResourceNotFoundException(String resource) {
        super("GitHub resource not found: " + resource);
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }
}
