# Design: Portfolio Features — GitHub API

**Date**: 2026-07-25 | **Status**: Approved
**Goal**: evolve the GitHub Repo Browser into a balanced full-stack portfolio piece — visual data features up front, one substantial backend engineering feature (OAuth2) as the centerpiece.

## Decisions (from brainstorming)

| Question | Decision |
|----------|----------|
| Portfolio focus | Balanced full-stack (visual + engineering) |
| Engineering centerpiece | OAuth2 "Sign in with GitHub" (Spring Security) |
| Visual features | Language breakdown, commit-activity heatmap, repo detail with README |
| Delivery order | Phase 1 visual → Phase 2 repo detail → Phase 3 OAuth2 |
| Data strategy | Extend current backend-proxy + H2 cache pattern (no GraphQL, no direct-from-SPA calls) |

## Phase 1 — Language breakdown + activity heatmap

Backend, same pattern as existing snapshot flow:

- `GET /api/users/{u}/languages` — aggregates `GET /repos/{o}/{r}/languages` across the user's **top 25 non-fork repos by stars** → `[{language, bytes, percent}]`. The 25-repo cap keeps anonymous quota viable.
- `GET /api/users/{u}/activity` — sums `GET /repos/{o}/{r}/stats/commit_activity` across the **top 10 repos** → 52 weekly buckets. GitHub returns `202 Accepted` while computing stats: response carries `pending: true` and partial data; UI shows a "still computing" hint.
- New generic cache table `api_cache(cache_key UNIQUE, fetched_at, payload CLOB)`, **TTL 1h** (slow-moving data, expensive calls). One table serves all new features.

Frontend: Recharts — language donut on the profile card, weekly bar chart below it.

## Phase 2 — Repo detail panel

- `GET /api/repos/{owner}/{repo}` — aggregates README (base64 → markdown text), last 5 releases, top 10 contributors, per-repo languages. Cached 1h in `api_cache`. Missing README → null field, panel adapts.
- Frontend: clicking a repo row expands an inline detail panel (no router). Markdown rendered with `react-markdown` (no raw-HTML injection → XSS-safe by default).

## Phase 3 — OAuth2 login

- `spring-boot-starter-oauth2-client`, GitHub provider, cookie session (same-origin via Vite proxy — no CORS work).
- Logged in: GitHub calls use the **user's** OAuth token (5,000 req/h) via `OAuth2AuthorizedClientService`; anonymous flow unchanged (shared `GITHUB_TOKEN` or 60 req/h).
- `/api/me` returns the logged identity for the UI header.
- Minimal scope (public identity only). **Private repos are out of scope for v1** — halves the security surface.
- Config: `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` via `.env`.

## Errors & testing

- Existing problem+json pipeline covers new endpoints (400/404/429/502); `pending` flag is data, not an error.
- Tests follow current conventions: `@RestClientTest` fixtures (incl. 202 case), `@WebMvcTest` for controllers, Vitest for pure aggregation/percentage functions, MockMvc + SecurityFilterChain for OAuth config.

## Out of scope (deliberate)

User comparison, webhooks/real-time, private repos, GraphQL migration, deployment.

## Next step

Spec-kit flow for feature `002-portfolio-features`: `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`, one phase at a time.
