package dev.luigivivian.githubtool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.luigivivian.githubtool.client.GithubClient;
import dev.luigivivian.githubtool.client.GithubContributorDto;
import dev.luigivivian.githubtool.client.GithubReleaseDto;
import dev.luigivivian.githubtool.dto.ContributorInfo;
import dev.luigivivian.githubtool.dto.LanguageShare;
import dev.luigivivian.githubtool.dto.ReleaseInfo;
import dev.luigivivian.githubtool.dto.RepoDetailResponse;
import dev.luigivivian.githubtool.entity.ApiCache;
import dev.luigivivian.githubtool.exception.GithubResourceNotFoundException;
import dev.luigivivian.githubtool.repository.ApiCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Release-name fallback, ungrouped languages and the lowercased cache key. */
class RepoDetailServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final Instant PUBLISHED = Instant.parse("2026-07-01T00:00:00Z");

    private final GithubClient github = mock(GithubClient.class);
    private final ApiCacheRepository cacheRepo = mock(ApiCacheRepository.class);

    private RepoDetailService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        service = new RepoDetailService(github,
                new ApiCacheService(cacheRepo, mapper, Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofHours(1)));
        when(cacheRepo.findByCacheKey(any())).thenReturn(Optional.empty());
        when(cacheRepo.save(any(ApiCache.class))).thenAnswer(inv -> inv.getArgument(0));
        when(github.fetchReadme(anyString(), anyString())).thenReturn(null);
        when(github.fetchReleases(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(github.fetchContributors(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(github.fetchRepoLanguages(anyString(), anyString())).thenReturn(Map.of());
    }

    private static GithubReleaseDto release(String name, String tagName) {
        return new GithubReleaseDto(name, tagName, PUBLISHED, "https://github.com/r/1");
    }

    @Test
    void releaseNameFallsBackToTagNameWhenNull() {
        when(github.fetchReleases("octocat", "hello", 5))
                .thenReturn(List.of(release(null, "v2.3.1")));

        RepoDetailResponse response = service.detail("octocat", "hello");

        assertThat(response.releases()).singleElement()
                .extracting(ReleaseInfo::name).isEqualTo("v2.3.1");
    }

    @Test
    void releaseNameFallsBackToTagNameWhenBlank() {
        when(github.fetchReleases("octocat", "hello", 5))
                .thenReturn(List.of(release("   ", "v9.0")));

        assertThat(service.detail("octocat", "hello").releases())
                .singleElement().extracting(ReleaseInfo::name).isEqualTo("v9.0");
    }

    @Test
    void releaseNameIsUsedWhenPresent() {
        when(github.fetchReleases("octocat", "hello", 5))
                .thenReturn(List.of(release("Summer release", "v9.0")));

        assertThat(service.detail("octocat", "hello").releases())
                .singleElement().extracting(ReleaseInfo::name).isEqualTo("Summer release");
    }

    @Test
    void releaseWithNeitherNameNorTagKeepsNullName() {
        when(github.fetchReleases("octocat", "hello", 5))
                .thenReturn(List.of(release(null, null)));

        assertThat(service.detail("octocat", "hello").releases())
                .singleElement().extracting(ReleaseInfo::name).isNull();
    }

    @Test
    void missingReadmeIsPreservedAsNull() {
        assertThat(service.detail("octocat", "hello").readme()).isNull();
    }

    @Test
    void languagesAreNotGroupedIntoAnOtherBucket() {
        when(github.fetchRepoLanguages("octocat", "hello"))
                .thenReturn(Map.of("Java", 9900L, "Shell", 50L, "Dockerfile", 50L));

        List<LanguageShare> languages = service.detail("octocat", "hello").languages();

        assertThat(languages).extracting(LanguageShare::language)
                .containsExactly("Java", "Dockerfile", "Shell")
                .doesNotContain("Other");
        assertThat(languages.getLast().percent()).isEqualTo(0.5);
    }

    @Test
    void contributorsAreMappedThrough() {
        when(github.fetchContributors("octocat", "hello", 10)).thenReturn(List.of(
                new GithubContributorDto("alice", "https://a", 42, "https://github.com/alice"),
                new GithubContributorDto("bob", "https://b", 7, "https://github.com/bob")));

        assertThat(service.detail("octocat", "hello").contributors())
                .extracting(ContributorInfo::login).containsExactly("alice", "bob");
    }

    @Test
    void releaseAndContributorCapsArePassedUpstream() {
        service.detail("octocat", "hello");

        verify(github).fetchReleases("octocat", "hello", 5);
        verify(github).fetchContributors("octocat", "hello", 10);
    }

    @Test
    void cacheKeyIsLowercasedForMixedCaseOwnerAndRepo() {
        service.detail("OctoCat", "Hello-World");

        verify(cacheRepo).findByCacheKey("repo:octocat/hello-world");
        ArgumentCaptor<ApiCache> saved = ArgumentCaptor.forClass(ApiCache.class);
        verify(cacheRepo).save(saved.capture());
        assertThat(saved.getValue().getCacheKey()).isEqualTo("repo:octocat/hello-world");
    }

    @Test
    void upstreamCallsKeepTheOriginalCasing() {
        service.detail("OctoCat", "Hello-World");

        verify(github).fetchReadme("OctoCat", "Hello-World");
        verify(github).fetchRepoLanguages("OctoCat", "Hello-World");
    }

    @Test
    void emptyUpstreamSectionsProduceEmptyLists() {
        RepoDetailResponse response = service.detail("octocat", "empty");

        assertThat(response.releases()).isEmpty();
        assertThat(response.contributors()).isEmpty();
        assertThat(response.languages()).isEmpty();
        assertThat(response.fromCache()).isFalse();
        assertThat(response.fetchedAt()).isEqualTo(NOW);
    }

    @Test
    void deletedRepoSurfacesNotFoundInsteadOfAnEmptyPanel() {
        when(github.fetchRepoLanguages("octocat", "gone"))
                .thenThrow(new GithubResourceNotFoundException("octocat/gone"));

        assertThatThrownBy(() -> service.detail("octocat", "gone"))
                .isInstanceOf(GithubResourceNotFoundException.class);
        verify(github, never()).fetchReadme("octocat", "gone");
        verify(cacheRepo, never()).save(any(ApiCache.class));
    }

    @Test
    void freshCachedDetailIsServedWithoutUpstreamCalls() {
        ApiCache row = new ApiCache();
        row.setCacheKey("repo:octocat/hello");
        row.setFetchedAt(NOW.minus(Duration.ofMinutes(30)));
        row.setPayload("{\"readme\":\"# cached\",\"releases\":[{\"name\":\"v1\","
                + "\"publishedAt\":\"2026-07-01T00:00:00Z\",\"htmlUrl\":\"https://r\"}],"
                + "\"contributors\":[],\"languages\":[]}");
        when(cacheRepo.findByCacheKey("repo:octocat/hello")).thenReturn(Optional.of(row));

        RepoDetailResponse response = service.detail("octocat", "hello");

        assertThat(response.fromCache()).isTrue();
        assertThat(response.readme()).isEqualTo("# cached");
        assertThat(response.releases()).singleElement()
                .extracting(ReleaseInfo::publishedAt).isEqualTo(PUBLISHED);
        verifyNoInteractions(github);
    }

    @Test
    void detailPayloadWithInstantsRoundTripsThroughTheCache() {
        when(github.fetchReadme("octocat", "hello")).thenReturn("# Hello");
        when(github.fetchReleases("octocat", "hello", 5))
                .thenReturn(List.of(release("v1.0", "1.0")));

        service.detail("octocat", "hello");

        ArgumentCaptor<ApiCache> saved = ArgumentCaptor.forClass(ApiCache.class);
        verify(cacheRepo).save(saved.capture());
        assertThat(saved.getValue().getPayload()).contains("2026-07-01T00:00:00Z");
    }
}
