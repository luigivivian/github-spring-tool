# GitHub Repo Browser

Search any GitHub user and browse their public repos with sort/filter options, snapshot caching, and recent-search history.

## Stack

- Backend: Spring Boot 3.5 (Java 21), Spring Web + Data JPA + Validation, H2 (file)
- Frontend: React 19 + Vite 7 + TypeScript, Vitest
- Integration: GitHub REST API, version header `2026-03-10`, via Spring RestClient

## Commands

```
# backend (needs JAVA_HOME=/opt/homebrew/opt/openjdk@21)
cd backend && mvn spring-boot:run    # API on :8080
cd backend && mvn test

# frontend
cd frontend && npm run dev           # SPA on :5173, proxies /api -> :8080
cd frontend && npm test
```

## Structure

```
backend/src/main/java/dev/luigivivian/githubtool/   # Spring layered layout
  config/      # AppProperties, CORS (dev)
  controller/  # SearchController, RecentSearchesController
  service/     # SnapshotService (cache TTL logic)
  repository/  # Spring Data interfaces
  entity/      # Snapshot, SearchRecord (JPA)
  dto/         # API response records (Profile, Repo, SnapshotResponse, ...)
  client/      # GithubClient (RestClient) + upstream DTOs
  exception/   # Github* exceptions + ApiExceptionHandler (problem+json)
frontend/src/
  api/       # typed fetch client
  lib/       # repoView.ts — pure sort/filter functions (unit-tested)
  components/
specs/001-github-repo-browser/   # spec, plan, contracts, quickstart
```

## Architecture

SPA calls `GET /api/users/{username}`; backend serves from H2 snapshot when fresher than 10 min (`refresh=true` bypasses), otherwise fetches profile + up to 300 repos (3×100 pages) from GitHub and upserts the snapshot. Successful searches append to `search_record`; `GET /api/searches/recent` feeds the landing page. All sorting/filtering is client-side over the returned dataset.

## Conventions

- Upstream errors map to RFC 7807 problem+json: 400 invalid username, 404 unknown user, 429 rate-limited (`retryAfterSeconds`), 502 upstream failure. Raw GitHub errors never reach the SPA.
- Usernames normalized to lowercase before cache lookup; validated against GitHub username regex at the API boundary.
- Snapshot payload is a JSON CLOB (profile + repos) — no normalized repo tables (research.md R4).
- `GITHUB_TOKEN` optional, env-only, never logged or exposed to the SPA.

## Key Context

- Spec-kit feature: see spec/plan links below; cap 300 repos, TTL 10 min (`app.github.cache-ttl`).
- Machine has Java 8 as system default — always set `JAVA_HOME` for Maven.
- H2 file lives in `backend/data/` (gitignored).

## Environment

`.env` (not committed) may define:
- `GITHUB_TOKEN` — optional GitHub PAT; raises rate limit 60 → 5000 req/h
- `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` — optional OAuth app for "Sign in with GitHub"; app boots without them (dummy placeholders), only the login flow needs real values

## Feature 002 additions

- `api_cache` table: generic 1h cache (`ApiCacheService`) for insights (`/languages`, `/activity`) and repo detail (`/api/repos/{o}/{r}`); pending (202) activity results are never stored.
- GitHub auth is per-request via `GithubTokenResolver` interceptor: logged-in user's OAuth token > `app.github.token` > anonymous.
- `SecurityConfig` is permit-all; OAuth2 login only activates when a `ClientRegistrationRepository` bean exists — `@WebMvcTest` slices must `@Import(SecurityConfig.class)`.
- Chart palette in `frontend/src/lib/chartTheme.ts` is dataviz-validated (light+dark); fixed slot order, >6 slices fold into "Other" (`lib/slices.ts`).

<!-- SPECKIT START -->
Active feature: `specs/002-portfolio-features`
Plan: [specs/002-portfolio-features/plan.md](specs/002-portfolio-features/plan.md)
Spec: [specs/002-portfolio-features/spec.md](specs/002-portfolio-features/spec.md)
Previous: `specs/001-github-repo-browser`
<!-- SPECKIT END -->
