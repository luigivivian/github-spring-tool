# Research: GitHub Repo Browser

**Date**: 2026-07-24 | **Plan**: [plan.md](plan.md)

## R1 — Backend HTTP client

- **Decision**: Spring `RestClient` (synchronous, Spring 6.1+).
- **Rationale**: Modern replacement for `RestTemplate` with fluent API and `onStatus` error hooks; no reactive stack needed for a request-per-search proxy. Blocking is fine — each page load is one upstream call at most.
- **Alternatives**: `WebClient` (drags in Reactor for no benefit here), `RestTemplate` (maintenance mode), OpenFeign (extra dependency, no gain for 2 endpoints).

## R2 — GitHub API usage

- **Decision**: `GET /users/{username}` for profile; `GET /users/{username}/repos?per_page=100&page=N&sort=updated` looped max 3 pages (300-repo cap, FR-014). Headers: `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2026-03-10`, `Authorization: Bearer ${GITHUB_TOKEN}` only when the env var is set.
- **Rationale**: `sort=updated` matches the spec default ordering and makes the 300 kept repos "the most recently updated" ones. GitHub sorts server-side only by created/updated/pushed/full_name — stars/forks ordering is a client-side concern anyway.
- **Error mapping**: HTTP 404 → user not found; 403/429 with `x-ratelimit-remaining: 0` → rate-limited (read `x-ratelimit-reset` epoch for retry time); other 4xx/5xx/IO → upstream unavailable.
- **Alternatives**: GraphQL API (single round-trip but needs a token always — breaks tokenless default); `/search/repositories` (different quota bucket, worse fit).

## R3 — Persistence stack

- **Decision**: Spring Data JPA (Hibernate) on H2 in file mode (`jdbc:h2:file:./data/githubtool`).
- **Rationale**: User asked "JPA or something more recent"; JPA chosen for maturity and zero-friction Spring Boot auto-config. H2 file mode survives restarts (recent searches persist) with no external service to install. Swapping to Postgres later is a config + driver change only.
- **Alternatives**: Spring Data JDBC (leaner, but weaker tooling for this team's stack); SQLite via JDBC (driver + dialect friction with Hibernate); Postgres now (overkill for a local tool).

## R4 — Snapshot storage shape

- **Decision**: One `snapshot` row per username: unique lowercased `username`, `fetched_at`, `truncated`, and the full profile+repos payload as a JSON `CLOB` (serialized/deserialized with Jackson).
- **Rationale**: The backend never queries individual repo fields — it serves the whole snapshot to the SPA, which does all sorting/filtering. Normalized `repo` tables would add mapping code and migrations with zero query benefit. TTL check is a single `fetched_at` comparison.
- **Alternatives**: normalized entities (rejected: no server-side queries need them), Spring Cache + Caffeine (rejected: in-memory only, dies on restart, and user explicitly asked for a database layer).

## R5 — Sort/filter placement

- **Decision**: 100% client-side in React, as pure functions in `lib/repoView.ts`.
- **Rationale**: Dataset is ≤300 rows already in the browser; instant interaction (SC-003) without round-trips; keeps API surface to 2 GET endpoints; pure functions are trivially unit-testable with Vitest.
- **Alternatives**: server-side query params (more API surface, slower UX, and would push view state into URLs of a cache-backed endpoint).

## R6 — Frontend ↔ backend wiring in dev

- **Decision**: Vite dev-server proxy `/api` → `http://localhost:8080`; permissive CORS for `http://localhost:5173` kept in the backend dev profile as a fallback.
- **Rationale**: Proxy means the SPA calls same-origin `/api/...` — no CORS complexity in code paths, and the production story (any reverse proxy or serving the built SPA behind the same origin) stays identical.
- **Alternatives**: direct cross-origin calls + CORS config (works, but bakes an origin into config), copying `dist/` into Spring static resources (couples builds; out of scope for dev-focused tool).

## R7 — Frontend state management

- **Decision**: React built-ins only — `useState`/`useEffect` in `App`, view options as local state, no router (single view), no data library.
- **Rationale**: One page, one fetch, derived lists via pure functions. Anything more is dead weight.
- **Alternatives**: TanStack Query (nice caching, but the backend already caches), Redux/Zustand (no shared state to justify).

## R8 — Token handling

- **Decision**: `GITHUB_TOKEN` read from environment into `app.github.token` (Spring relaxed binding); never logged, never serialized, never sent to the SPA. `.env.example` documents it; local `.env` is gitignored and loaded by the shell, not committed.
- **Rationale**: FR-012 requires operator-level config with zero exposure; env var is the standard mechanism and matches the user's global convention of `.env` for keys.

## R9 — Testing approach

- **Decision**: Backend — `@RestClientTest` + `MockRestServiceServer` for GithubClient (parsing, pagination, error mapping); plain JUnit + Mockito for SnapshotService TTL logic; `@WebMvcTest` for controller status codes / problem+json shape. Frontend — Vitest on `repoView.ts` (sort, filter, toggles, counts).
- **Rationale**: Covers every FR-mapped behavior without spinning real HTTP to GitHub in CI; component/E2E testing deferred (playwright-skill available later if wanted).
