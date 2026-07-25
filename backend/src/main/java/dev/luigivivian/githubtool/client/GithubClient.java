package dev.luigivivian.githubtool.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.luigivivian.githubtool.config.AppProperties;
import dev.luigivivian.githubtool.exception.GithubAuthExpiredException;
import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.exception.GithubResourceNotFoundException;
import dev.luigivivian.githubtool.exception.GithubUnavailableException;
import dev.luigivivian.githubtool.exception.GithubUserNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GithubClient {

    public static final int PER_PAGE = 100;
    public static final int MAX_PAGES = 3;

    private static final ParameterizedTypeReference<Map<String, Long>> LANGUAGES_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;
    private final Clock clock;
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final ObjectProvider<GithubTokenResolver> tokenResolver;

    public GithubClient(RestClient.Builder builder, AppProperties props, Clock clock,
            ObjectMapper mapper, ObjectProvider<GithubTokenResolver> tokenResolver) {
        this.clock = clock;
        this.props = props;
        this.mapper = mapper;
        this.tokenResolver = tokenResolver;
        this.client = builder
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2026-03-10")
                .requestInterceptor((request, body, execution) -> {
                    currentToken().ifPresent(token -> request.getHeaders().setBearerAuth(token));
                    return execution.execute(request, body);
                })
                .build();
    }

    private Optional<String> currentToken() {
        GithubTokenResolver resolver = tokenResolver.getIfAvailable();
        if (resolver != null) {
            Optional<String> token = resolver.currentToken();
            if (token.isPresent()) {
                return token;
            }
        }
        return props.token() != null && !props.token().isBlank()
                ? Optional.of(props.token())
                : Optional.empty();
    }

    public GithubUserDto fetchUser(String username) {
        return executeForUser(username, () -> client.get()
                .uri("/users/{username}", username)
                .retrieve()
                .body(GithubUserDto.class));
    }

    public List<GithubRepoDto> fetchRepos(String username) {
        List<GithubRepoDto> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            int currentPage = page;
            GithubRepoDto[] batch = executeForUser(username, () -> client.get()
                    .uri("/users/{username}/repos?per_page={perPage}&page={page}&sort=updated",
                            username, PER_PAGE, currentPage)
                    .retrieve()
                    .body(GithubRepoDto[].class));
            if (batch == null || batch.length == 0) {
                break;
            }
            all.addAll(Arrays.asList(batch));
            if (batch.length < PER_PAGE) {
                break;
            }
        }
        return all;
    }

    /** Bytes of code per language for one repo. */
    public Map<String, Long> fetchRepoLanguages(String owner, String repo) {
        Map<String, Long> languages = executeForRepo(owner, repo, () -> client.get()
                .uri("/repos/{owner}/{repo}/languages", owner, repo)
                .retrieve()
                .body(LANGUAGES_TYPE));
        return languages != null ? languages : Map.of();
    }

    /**
     * Weekly commit totals (oldest → newest, up to 52 weeks). Returns null while GitHub is still
     * computing the statistics (HTTP 202).
     */
    public int[] fetchCommitActivity(String owner, String repo) {
        // read as text first: a 202 (still computing) carries "{}" which is not the array shape
        ResponseEntity<String> response = executeForRepo(owner, repo, () -> client.get()
                .uri("/repos/{owner}/{repo}/stats/commit_activity", owner, repo)
                .retrieve()
                .toEntity(String.class));
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            return null; // still computing — the ONLY case that means "pending"
        }
        String body = response.getBody();
        if (body == null || !body.stripLeading().startsWith("[")) {
            return new int[0]; // 200 without array data = no history, never "pending forever"
        }
        try {
            CommitWeekDto[] weeks = mapper.readValue(body, CommitWeekDto[].class);
            return Arrays.stream(weeks).mapToInt(CommitWeekDto::total).toArray();
        } catch (JsonProcessingException e) {
            throw new GithubUnavailableException(e);
        }
    }

    /** Decoded README markdown, or null when the repo has none. */
    public String fetchReadme(String owner, String repo) {
        try {
            ReadmeDto readme = executeRaw(() -> client.get()
                    .uri("/repos/{owner}/{repo}/readme", owner, repo)
                    .retrieve()
                    .body(ReadmeDto.class));
            if (readme == null || readme.content() == null) {
                return null;
            }
            return new String(Base64.getMimeDecoder().decode(readme.content()),
                    StandardCharsets.UTF_8);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public List<GithubReleaseDto> fetchReleases(String owner, String repo, int limit) {
        GithubReleaseDto[] releases = executeForRepo(owner, repo, () -> client.get()
                .uri("/repos/{owner}/{repo}/releases?per_page={limit}", owner, repo, limit)
                .retrieve()
                .body(GithubReleaseDto[].class));
        return releases != null ? List.of(releases) : List.of();
    }

    public List<GithubContributorDto> fetchContributors(String owner, String repo, int limit) {
        GithubContributorDto[] contributors = executeForRepo(owner, repo, () -> client.get()
                .uri("/repos/{owner}/{repo}/contributors?per_page={limit}", owner, repo, limit)
                .retrieve()
                .body(GithubContributorDto[].class));
        return contributors != null ? List.of(contributors) : List.of();
    }

    private <T> T executeForUser(String username, Supplier<T> call) {
        try {
            return executeRaw(call);
        } catch (HttpClientErrorException.NotFound e) {
            throw new GithubUserNotFoundException(username);
        }
    }

    private <T> T executeForRepo(String owner, String repo, Supplier<T> call) {
        try {
            return executeRaw(call);
        } catch (HttpClientErrorException.NotFound e) {
            throw new GithubResourceNotFoundException(owner + "/" + repo);
        }
    }

    /** Maps rate-limit and transport failures; lets 404 propagate for caller-specific handling. */
    private <T> T executeRaw(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.NotFound e) {
            throw e;
        } catch (HttpClientErrorException.Unauthorized e) {
            GithubTokenResolver resolver = tokenResolver.getIfAvailable();
            if (resolver != null && resolver.currentToken().isPresent()) {
                throw new GithubAuthExpiredException(); // signed-in visitor's token revoked
            }
            throw new GithubUnavailableException(e);
        } catch (RestClientResponseException e) {
            if (isRateLimited(e)) {
                throw new GithubRateLimitedException(retryAfterSeconds(e));
            }
            throw new GithubUnavailableException(e);
        } catch (RestClientException e) {
            throw new GithubUnavailableException(e);
        }
    }

    private boolean isRateLimited(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 429) {
            return true;
        }
        HttpHeaders headers = e.getResponseHeaders();
        return status == 403
                && headers != null
                && "0".equals(headers.getFirst("x-ratelimit-remaining"));
    }

    private Long retryAfterSeconds(RestClientResponseException e) {
        HttpHeaders headers = e.getResponseHeaders();
        if (headers == null) {
            return null;
        }
        String retryAfter = headers.getFirst("retry-after");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter);
            } catch (NumberFormatException ignored) {
                // fall through to x-ratelimit-reset
            }
        }
        String reset = headers.getFirst("x-ratelimit-reset");
        if (reset != null) {
            try {
                return Math.max(0, Long.parseLong(reset) - clock.instant().getEpochSecond());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReadmeDto(String content, String encoding) {
    }
}
