package dev.luigivivian.githubtool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.luigivivian.githubtool.entity.ApiCache;
import dev.luigivivian.githubtool.repository.ApiCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ApiCacheService {

    /** Loader result; cacheable=false (e.g. upstream still computing) is returned but not stored. */
    public record Loaded<T>(T value, boolean cacheable) {
        public static <T> Loaded<T> of(T value) {
            return new Loaded<>(value, true);
        }
    }

    public record Cached<T>(T value, Instant fetchedAt, boolean fromCache) {
    }

    private final ApiCacheRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Duration ttl;

    public ApiCacheService(ApiCacheRepository repository, ObjectMapper mapper, Clock clock,
            @Value("${app.insights.ttl:1h}") Duration ttl) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.ttl = ttl;
    }

    public <T> Cached<T> get(String key, Class<T> type, Supplier<Loaded<T>> loader) {
        Instant now = clock.instant();
        Optional<ApiCache> row = repository.findByCacheKey(key);

        if (row.isPresent()
                && Duration.between(row.get().getFetchedAt(), now).compareTo(ttl) <= 0) {
            return new Cached<>(read(row.get().getPayload(), type), row.get().getFetchedAt(), true);
        }

        Loaded<T> loaded = loader.get();
        if (loaded.cacheable()) {
            String json = write(loaded.value());
            ApiCache cache = row.orElseGet(ApiCache::new);
            cache.setCacheKey(key);
            cache.setFetchedAt(now);
            cache.setPayload(json);
            try {
                repository.save(cache);
            } catch (DataIntegrityViolationException e) {
                // concurrent insert for the same key: another request already stored it
            }
        }
        return new Cached<>(loaded.value(), now, false);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt api_cache payload", e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unserializable api_cache payload", e);
        }
    }
}
