package dev.luigivivian.githubtool.dto;

import java.util.List;

public record SnapshotPayload(Profile profile, List<Repo> repos) {
}
