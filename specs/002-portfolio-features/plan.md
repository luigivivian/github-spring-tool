# Implementation Plan: Portfolio Features

**Feature**: `specs/002-portfolio-features` | **Date**: 2026-07-25 | **Spec**: [spec.md](spec.md)
**Design**: [docs/plans/2026-07-25-portfolio-features-design.md](../../docs/plans/2026-07-25-portfolio-features-design.md) (approved — rationale lives there)

## Summary

Three phases on top of feature 001's stack. Phase 1: language + activity insights endpoints aggregating GitHub per-repo data, cached 1h in a new generic `api_cache` table; Recharts visualizations. Phase 2: repo detail endpoint (README/releases/contributors/languages) + inline panel with `react-markdown`. Phase 3: OAuth2 GitHub login (spring-boot-starter-oauth2-client); logged-in visitors' GitHub calls use their own token via a token-resolver hook in `GithubClient`.

## Technical Context

- **New backend deps**: `spring-boot-starter-oauth2-client` (phase 3 only)
- **New frontend deps**: `recharts`, `react-markdown`
- **Cache**: `api_cache(cache_key UNIQUE, fetched_at, payload CLOB)` — one table for all new views, TTL `app.github.insights-ttl=1h`; pending (202) activity results are NOT cached
- **GitHub endpoints used**: `/repos/{o}/{r}/languages`, `/repos/{o}/{r}/stats/commit_activity` (202 = still computing → pending; a 200 without array data = empty history, never pending), `/repos/{o}/{r}/readme` (base64), `/repos/{o}/{r}/releases`, `/repos/{o}/{r}/contributors`
- **Repo owner** for all per-repo calls = the searched username (repos come from the user's own snapshot)
- **Token strategy (phase 3)**: `GithubTokenResolver` interface; `GithubClient` applies `Authorization: Bearer` per request — resolver absent/empty → fall back to `app.github.token` (existing behavior, existing tests unaffected). Default resolver reads the logged-in user's OAuth2 token from `OAuth2AuthorizedClientService`.
- **OAuth2 boot safety**: GitHub registration configured with non-empty placeholders (`${GITHUB_CLIENT_ID:dummy-client-id}`) so the app boots without credentials; login only works once real creds are set. CSRF disabled (stateless-style JSON API behind dev proxy; documented trade-off). Vite proxy gains `/oauth2` and `/login` routes.

## Structure (new files)

```
backend/.../entity/ApiCache.java            repository/ApiCacheRepository.java
backend/.../service/ApiCacheService.java    # get(key, supplier) with TTL
backend/.../service/InsightsService.java    # language aggregation + activity summation
backend/.../service/RepoDetailService.java
backend/.../client/GithubClient.java        # +5 fetch methods, token interceptor
backend/.../client/GithubTokenResolver.java # interface + DefaultGithubTokenResolver
backend/.../controller/InsightsController.java   # GET /api/users/{u}/languages | /activity
backend/.../controller/RepoDetailController.java # GET /api/repos/{owner}/{repo}
backend/.../controller/MeController.java         # GET /api/me
backend/.../config/SecurityConfig.java
backend/.../dto/ LanguageShare, LanguagesResponse, ActivityResponse, RepoDetailResponse,
                 ReleaseInfo, ContributorInfo, MeResponse
frontend/src/components/ LanguageChart, ActivityChart, RepoDetailPanel, AuthHeader
frontend/src/api/client.ts                  # +getLanguages/getActivity/getRepoDetail/getMe
```

## Constitution Check

No constitution — N/A.

## Contracts

Documented inline in tasks + spec FRs; response shapes in dto records mirror spec entities (LanguageShare[], 52-int weeks + pending flag, RepoDetail sections nullable). Errors reuse feature 001 problem+json pipeline unchanged.

## Testing

Same conventions as 001: `@RestClientTest` for new GithubClient methods (incl. 202 and base64 README fixtures), plain JUnit for aggregation math (percent, <2% → Other, pending propagation) and ApiCacheService TTL, `@WebMvcTest` for new controllers, MockMvc smoke for SecurityConfig (app boots, /api/me anonymous). Frontend: tsc + existing suites (no new pure logic — aggregation is server-side).
