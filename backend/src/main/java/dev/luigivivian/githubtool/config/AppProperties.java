package dev.luigivivian.githubtool.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github")
public record AppProperties(String baseUrl, String token, Duration cacheTtl) {
}
