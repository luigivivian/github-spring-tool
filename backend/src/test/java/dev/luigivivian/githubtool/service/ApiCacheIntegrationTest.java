package dev.luigivivian.githubtool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.luigivivian.githubtool.dto.ContributorInfo;
import dev.luigivivian.githubtool.dto.LanguageShare;
import dev.luigivivian.githubtool.dto.ReleaseInfo;
import dev.luigivivian.githubtool.entity.ApiCache;
import dev.luigivivian.githubtool.repository.ApiCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * ApiCacheService against a real (in-memory) database: payload round-trip with Instants, the
 * unique-key constraint and the concurrent-insert swallow path.
 */
@DataJpaTest
class ApiCacheIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final Instant PUBLISHED = Instant.parse("2026-07-01T00:00:00Z");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Autowired
    private ApiCacheRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private ApiCacheService serviceWith(ApiCacheRepository repo) {
        return new ApiCacheService(repo, MAPPER, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(1));
    }

    @Test
    void detailPayloadWithInstantsSurvivesAStoreAndReloadCycle() {
        ApiCacheService service = serviceWith(repository);
        RepoDetailService.DetailData original = new RepoDetailService.DetailData(
                "# Hello",
                List.of(new ReleaseInfo("v1.0", PUBLISHED, "https://github.com/r/1")),
                List.of(new ContributorInfo("alice", "https://a", 42, "https://github.com/alice")),
                List.of(new LanguageShare("Java", 99.5, 9950L)));

        service.get("repo:octocat/hello", RepoDetailService.DetailData.class,
                () -> ApiCacheService.Loaded.of(original));
        entityManager.flush();
        entityManager.clear();

        ApiCacheService.Cached<RepoDetailService.DetailData> reloaded =
                service.get("repo:octocat/hello", RepoDetailService.DetailData.class, () -> {
                    throw new AssertionError("loader must not run on a fresh row");
                });

        assertThat(reloaded.fromCache()).isTrue();
        assertThat(reloaded.value()).isEqualTo(original);
        assertThat(reloaded.value().releases().getFirst().publishedAt()).isEqualTo(PUBLISHED);
    }

    @Test
    void onlyOneRowPerCacheKeyIsKeptAcrossRefreshes() {
        ApiCacheService service = serviceWith(repository);
        AtomicInteger loads = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            new ApiCacheService(repository, MAPPER,
                    Clock.fixed(NOW.plus(Duration.ofHours(2L * i)), ZoneOffset.UTC),
                    Duration.ofHours(1))
                    .get("languages:octocat", InsightsService.LanguagesData.class, () -> {
                        loads.incrementAndGet();
                        return ApiCacheService.Loaded.of(new InsightsService.LanguagesData(
                                List.of(new LanguageShare("Java", 100.0, 10L))));
                    });
            entityManager.flush();
        }

        assertThat(loads.get()).isEqualTo(3);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(service).isNotNull();
    }

    @Test
    void concurrentInsertForTheSameKeyIsSwallowedByTheService() {
        ApiCache existing = new ApiCache();
        existing.setCacheKey("languages:octocat");
        existing.setFetchedAt(NOW);
        existing.setPayload("{\"languages\":[]}");
        entityManager.persistAndFlush(existing);

        // simulate the race: the read missed, so the service tries to INSERT a duplicate key.
        // save() still delegates to the real repository, so H2's unique index really fires.
        ApiCacheRepository racing = mock(ApiCacheRepository.class);
        when(racing.findByCacheKey(anyString())).thenReturn(Optional.empty());
        when(racing.save(any(ApiCache.class)))
                .thenAnswer(invocation -> repository.saveAndFlush(invocation.getArgument(0)));

        ApiCacheService.Cached<InsightsService.LanguagesData> result =
                serviceWith(racing).get("languages:octocat", InsightsService.LanguagesData.class,
                        () -> ApiCacheService.Loaded.of(new InsightsService.LanguagesData(
                                List.of(new LanguageShare("Rust", 100.0, 2048L)))));

        assertThat(result.fromCache()).isFalse();
        assertThat(result.value().languages()).singleElement()
                .extracting(LanguageShare::language).isEqualTo("Rust");
    }

    @Test
    void largePayloadsFitInTheLobColumn() {
        ApiCacheService service = serviceWith(repository);
        List<LanguageShare> many = java.util.stream.IntStream.range(0, 4000)
                .mapToObj(i -> new LanguageShare("language-with-a-long-name-" + i, 0.1, i))
                .toList();

        service.get("languages:whale", InsightsService.LanguagesData.class,
                () -> ApiCacheService.Loaded.of(new InsightsService.LanguagesData(many)));
        entityManager.flush();
        entityManager.clear();

        ApiCacheService.Cached<InsightsService.LanguagesData> reloaded =
                service.get("languages:whale", InsightsService.LanguagesData.class, () -> {
                    throw new AssertionError("loader must not run on a fresh row");
                });

        assertThat(reloaded.value().languages()).hasSize(4000);
    }

    @Test
    void repoDetailKeyAtItsMaximumLengthIsStorable() {
        ApiCacheService service = serviceWith(repository);
        String key = "repo:" + "o".repeat(39) + "/" + "r".repeat(100);

        service.get(key, InsightsService.LanguagesData.class,
                () -> ApiCacheService.Loaded.of(new InsightsService.LanguagesData(List.of())));
        entityManager.flush();

        assertThat(repository.findByCacheKey(key)).isPresent();
        assertThat(key.length()).isLessThanOrEqualTo(200);
    }
}
