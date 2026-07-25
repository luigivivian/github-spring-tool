package dev.luigivivian.githubtool.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommitWeekDto(long week, int total) {
}
