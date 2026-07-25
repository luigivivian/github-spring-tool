package dev.luigivivian.githubtool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/** TTL boundaries, concurrent-insert swallow and (de)serialization failure paths. */
class ApiCacheServiceEdgeTest {

    record Payload(String value) {
    }

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);

    private final ApiCacheRepository repository = mock(ApiCacheRepository.class);
    private ApiCacheService service;

    @BeforeEach
    void setUp() {
        service = new ApiCacheService(repository, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), TTL);
        when(repository.save(any(ApiCache.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ApiCache row(String key, Instant fetchedAt, String payload) {
        ApiCache row = new ApiCache();
        row.setCacheKey(key);
        row.setFetchedAt(fetchedAt);
        row.setPayload(payload);
        return row;
    }

    @Test
    void concurrentInsertOnSameKeyIsSwallowedAndValueStillReturned() {
        when(repository.findByCacheKey("k")).thenReturn(Optional.empty());
        when(repository.save(any(ApiCache.class)))
                .thenThrow(new DataIntegrityViolationException("unique index on cache_key"));

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class,
                () -> ApiCacheService.Loaded.of(new Payload("fresh")));

        assertThat(result.value().value()).isEqualTo("fresh");
        assertThat(result.fromCache()).isFalse();
        assertThat(result.fetchedAt()).isEqualTo(NOW);
    }

    @Test
    void rowExactlyAtTtlBoundaryIsStillServedFromCache() {
        when(repository.findByCacheKey("k"))
                .thenReturn(Optional.of(row("k", NOW.minus(TTL), "{\"value\":\"cached\"}")));

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class, () -> {
            throw new AssertionError("loader must not run exactly at the TTL boundary");
        });

        assertThat(result.fromCache()).isTrue();
        assertThat(result.value().value()).isEqualTo("cached");
    }

    @Test
    void rowOneSecondPastTtlIsReloaded() {
        when(repository.findByCacheKey("k")).thenReturn(
                Optional.of(row("k", NOW.minus(TTL).minusSeconds(1), "{\"value\":\"stale\"}")));

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class,
                () -> ApiCacheService.Loaded.of(new Payload("fresh")));

        assertThat(result.value().value()).isEqualTo("fresh");
        assertThat(result.fromCache()).isFalse();
    }

    @Test
    void expiredRowIsUpdatedInPlaceWithNewTimestampAndPayload() {
        ApiCache existing = row("k", NOW.minus(Duration.ofHours(2)), "{\"value\":\"stale\"}");
        when(repository.findByCacheKey("k")).thenReturn(Optional.of(existing));

        service.get("k", Payload.class, () -> ApiCacheService.Loaded.of(new Payload("fresh")));

        ArgumentCaptor<ApiCache> saved = ArgumentCaptor.forClass(ApiCache.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(saved.getValue().getFetchedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getPayload()).isEqualTo("{\"value\":\"fresh\"}");
    }

    @Test
    void newRowIsSavedWithTheRequestedKey() {
        when(repository.findByCacheKey("languages:octocat")).thenReturn(Optional.empty());

        service.get("languages:octocat", Payload.class,
                () -> ApiCacheService.Loaded.of(new Payload("v")));

        ArgumentCaptor<ApiCache> saved = ArgumentCaptor.forClass(ApiCache.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCacheKey()).isEqualTo("languages:octocat");
    }

    @Test
    void corruptStoredPayloadFailsLoudly() {
        when(repository.findByCacheKey("k"))
                .thenReturn(Optional.of(row("k", NOW, "not-json")));

        assertThatThrownBy(() -> service.get("k", Payload.class,
                () -> ApiCacheService.Loaded.of(new Payload("fresh"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Corrupt api_cache payload");
    }

    @Test
    void unserializableValueFailsLoudlyAndIsNotStored() {
        when(repository.findByCacheKey("k")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("k", Object.class,
                () -> ApiCacheService.Loaded.of(new Object())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unserializable api_cache payload");
        verify(repository, never()).save(any(ApiCache.class));
    }

    @Test
    void nonCacheableResultLeavesAnExpiredRowUntouched() {
        ApiCache existing = row("k", NOW.minus(Duration.ofHours(3)), "{\"value\":\"stale\"}");
        when(repository.findByCacheKey("k")).thenReturn(Optional.of(existing));

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class,
                () -> new ApiCacheService.Loaded<>(new Payload("partial"), false));

        assertThat(result.value().value()).isEqualTo("partial");
        assertThat(result.fromCache()).isFalse();
        verify(repository, never()).save(any(ApiCache.class));
        assertThat(existing.getPayload()).isEqualTo("{\"value\":\"stale\"}");
    }

    @Test
    void nonCacheableResultReloadsOnEveryCall() {
        when(repository.findByCacheKey("k")).thenReturn(Optional.empty());
        AtomicInteger loads = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            service.get("k", Payload.class, () -> {
                loads.incrementAndGet();
                return new ApiCacheService.Loaded<>(new Payload("partial"), false);
            });
        }

        assertThat(loads.get()).isEqualTo(3);
        verify(repository, never()).save(any(ApiCache.class));
    }

    @Test
    void rowTimestampedInTheFutureIsTreatedAsFresh() {
        when(repository.findByCacheKey("k")).thenReturn(
                Optional.of(row("k", NOW.plus(Duration.ofHours(5)), "{\"value\":\"skewed\"}")));

        ApiCacheService.Cached<Payload> result = service.get("k", Payload.class, () -> {
            throw new AssertionError("loader must not run for a future-dated row");
        });

        assertThat(result.fromCache()).isTrue();
        assertThat(result.value().value()).isEqualTo("skewed");
    }
}
