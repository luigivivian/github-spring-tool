package dev.luigivivian.githubtool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.luigivivian.githubtool.entity.ApiCache;
import dev.luigivivian.githubtool.repository.ApiCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiCacheServiceTest {

    record Payload(String value) {
    }

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private final ApiCacheRepository repository = mock(ApiCacheRepository.class);
    private ApiCacheService service;

    @BeforeEach
    void setUp() {
        service = new ApiCacheService(repository, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));
        when(repository.save(any(ApiCache.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void missLoadsAndStores() {
        when(repository.findByCacheKey("k")).thenReturn(Optional.empty());

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class,
                () -> ApiCacheService.Loaded.of(new Payload("fresh")));

        assertThat(result.value().value()).isEqualTo("fresh");
        assertThat(result.fromCache()).isFalse();
        assertThat(result.fetchedAt()).isEqualTo(NOW);
        verify(repository).save(any(ApiCache.class));
    }

    @Test
    void freshRowServedWithoutLoading() {
        ApiCache row = new ApiCache();
        row.setCacheKey("k");
        row.setFetchedAt(NOW.minus(Duration.ofMinutes(30)));
        row.setPayload("{\"value\":\"cached\"}");
        when(repository.findByCacheKey("k")).thenReturn(Optional.of(row));

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class, () -> {
            throw new AssertionError("loader must not run on fresh hit");
        });

        assertThat(result.value().value()).isEqualTo("cached");
        assertThat(result.fromCache()).isTrue();
        assertThat(result.fetchedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(30)));
    }

    @Test
    void expiredRowReloads() {
        ApiCache row = new ApiCache();
        row.setCacheKey("k");
        row.setFetchedAt(NOW.minus(Duration.ofMinutes(61)));
        row.setPayload("{\"value\":\"stale\"}");
        when(repository.findByCacheKey("k")).thenReturn(Optional.of(row));

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class,
                () -> ApiCacheService.Loaded.of(new Payload("fresh")));

        assertThat(result.value().value()).isEqualTo("fresh");
        assertThat(result.fromCache()).isFalse();
        verify(repository).save(row);
    }

    @Test
    void nonCacheableResultIsReturnedButNotStored() {
        when(repository.findByCacheKey("k")).thenReturn(Optional.empty());

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class,
                () -> new ApiCacheService.Loaded<>(new Payload("partial"), false));

        assertThat(result.value().value()).isEqualTo("partial");
        verify(repository, never()).save(any(ApiCache.class));
    }
}
