package dev.luigivivian.githubtool.dto;

public record MeResponse(boolean authenticated, String login, String name, String avatarUrl) {

    public static MeResponse anonymous() {
        return new MeResponse(false, null, null, null);
    }
}
