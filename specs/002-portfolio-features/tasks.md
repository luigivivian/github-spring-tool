# Tasks: Portfolio Features

**Input**: [plan.md](plan.md), [spec.md](spec.md), design doc. Tests included (project convention).

## User stories

- **US1 (P1)** — Language chart + activity heatmap on profile (FR-001..003)
- **US2 (P2)** — Inline repo detail panel (FR-004..006)
- **US3 (P3)** — Sign in with GitHub, own quota (FR-007..009)
- Cross-cutting: 1h server cache (FR-010), friendly errors (FR-011)

## Phase 1: Foundational

- [x] T101 `ApiCache` entity + `ApiCacheRepository` (cache_key UNIQUE, fetched_at, payload CLOB) in `backend/.../entity/`, `repository/`
- [x] T102 `ApiCacheService.get(key, supplier)` with `app.github.insights-ttl=1h` (skip-store hook for pending results) in `backend/.../service/` + unit test
- [x] T103 `GithubClient`: add `fetchRepoLanguages`, `fetchCommitActivity` (202 → null), `fetchReadme` (base64 decode), `fetchReleases`, `fetchContributors`; auth header moves to per-request interceptor backed by `GithubTokenResolver` (fallback = props token) in `backend/.../client/`
- [x] T104 [P] `@RestClientTest` for new methods: languages map parse, 202 pending, README base64+missing(404→null), releases/contributors caps in `backend/src/test/.../client/GithubInsightsClientTest.java`

## Phase 2: US1 — Charts

- [x] T105 [US1] `InsightsService`: top-25 non-fork by stars → aggregate language bytes → percent, group <2% as "Other"; top-10 → sum 52 weekly buckets, `pending` if any repo 202; both via ApiCacheService. DTOs `LanguageShare`, `LanguagesResponse`, `ActivityResponse`
- [x] T106 [P] [US1] Unit tests: percentage math, Other-grouping, zero-language repos, pending propagation, empty user in `backend/src/test/.../service/InsightsServiceTest.java`
- [x] T107 [US1] `InsightsController` GET `/api/users/{username}/languages` + `/activity` (same username validation) + `@WebMvcTest`
- [x] T108 [US1] Frontend: `recharts` dep; `LanguageChart.tsx` (donut + legend), `ActivityChart.tsx` (52-week bars, "still computing" state + retry); wire into `App.tsx` below ProfileCard; `client.ts` getters
- [x] T109 [US1] Empty/error states: no-language-data, zero repos, problem+json banner reuse

## Phase 3: US2 — Repo detail

- [x] T110 [US2] `RepoDetailService` (readme/releases≤5/contributors≤10/languages via cache) + `RepoDetailResponse`, `ReleaseInfo`, `ContributorInfo` + `RepoDetailController` GET `/api/repos/{owner}/{repo}` + `@WebMvcTest`
- [x] T111 [US2] Frontend: `react-markdown` dep; `RepoDetailPanel.tsx` (README rendered, releases, contributors, language bars, per-section empty states, scrollable); RepoRow click toggles panel

## Phase 4: US3 — OAuth2

- [x] T112 [US3] `spring-boot-starter-oauth2-client`; registration in `application.yml` with `${GITHUB_CLIENT_ID:dummy-client-id}` placeholders; `SecurityConfig` (permitAll API, oauth2Login, logout, CSRF off); `DefaultGithubTokenResolver` (OAuth2AuthorizedClientService → user token, else props); `MeController` GET `/api/me` + `MeResponse`
- [x] T113 [P] [US3] Tests: MockMvc anonymous `/api/me`, resolver fallback unit test; verify existing client tests still green (resolver optional)
- [x] T114 [US3] Frontend: `AuthHeader.tsx` (Sign in link → `/oauth2/authorization/github`, avatar + Sign out when logged); `getMe()`/logout in client.ts; Vite proxy add `/oauth2`, `/login`; `.env.example` + README gain `GITHUB_CLIENT_ID/SECRET`

## Phase 5: Polish & gate

- [x] T115 [P] Docs sync: CLAUDE.md structure/env, DEVLOG session entry, README features/config
- [x] T116 Validation gate: `mvn test` + `npm test` + `tsc` green; live smoke (languages/activity/detail curl); code-reviewer + qa subagents on new files, fix findings

## Dependencies

```
T101→T102→{T103,T104}→ US1(T105..109) → US2(T110,T111) → US3(T112..114) → Polish
```
US2 independent of US1 backend-wise; sequenced for delivery order. US3 touches GithubClient auth path — last on purpose.
