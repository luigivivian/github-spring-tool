# Implementation Plan: GitHub Repo Browser

**Feature**: `specs/001-github-repo-browser` | **Date**: 2026-07-24 | **Spec**: [spec.md](spec.md)
**Input**: User-decided stack: Spring Boot 3.5 (Java 21) REST API only — no server-side templates; separate `frontend/` React 19 + Vite SPA; Spring Data JPA on H2 (file) for snapshot cache + recent searches; GitHub REST API version 2026-03-10 via Spring `RestClient`; optional `GITHUB_TOKEN`.

## Summary

Two-part app. `backend/`: Spring Boot REST API that proxies GitHub (user profile + up to 300 repos), caches each fetched snapshot in H2 for 10 minutes (FR-015), records successful searches (FR-016), and maps upstream failures to friendly, typed errors (FR-010/011). `frontend/`: React + Vite SPA with the search form, profile card, repo list, and all sort/filter/toggle interactions done client-side over the fetched dataset (≤300 rows), plus copy-clone-URL and recent-searches UI.

## Technical Context

- **Language/Version**: Java 21 (backend); TypeScript 5 + React 19 (frontend)
- **Primary Dependencies**: Spring Boot 3.5.x (`web`, `data-jpa`, `validation`), H2 (file mode), Vite 7, React 19
- **Storage**: H2 file DB via Spring Data JPA — `snapshot` (JSON payload per username) + `search_record` tables
- **External API**: GitHub REST, headers `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2026-03-10`, optional `Authorization: Bearer ${GITHUB_TOKEN}`
- **Testing**: JUnit 5 + `@RestClientTest`/`MockRestServiceServer` + `@DataJpaTest` + MockMvc (backend); Vitest for sort/filter utils (frontend)
- **Target Platform**: local dev, macOS; frontend dev server proxies `/api` to backend :8080
- **Project Type**: web (backend + frontend folders)
- **Performance Goals**: SC-001 first result < 15 s; SC-003 sort/filter < 2 s (client-side, trivially met); SC-007 cached repeat = zero GitHub calls
- **Constraints**: 300-repo cap (3 × `per_page=100`), 10-min TTL (configurable `app.github.cache-ttl`)
- **Scale/Scope**: single deployment, no auth, shared cache/history

## Constitution Check

No `.specify/memory/constitution.md` in this project — no gates to evaluate. N/A.

## Project Structure

### Documentation (this feature)

```
specs/001-github-repo-browser/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── api.yaml         # Phase 1 (OpenAPI)
└── checklists/requirements.md
```

### Source Code (repository root)

```
backend/
├── pom.xml
└── src/
    ├── main/java/dev/luigivivian/githubtool/
    │   ├── GithubToolApplication.java
    │   ├── config/          # RestClient bean, CORS (dev), app properties
    │   ├── github/          # GithubClient + upstream DTOs, upstream exceptions
    │   ├── snapshot/        # Snapshot & SearchRecord entities, repos, SnapshotService (TTL logic)
    │   └── api/             # SearchController, RecentSearchesController, ApiExceptionHandler
    ├── main/resources/application.yml
    └── test/java/dev/luigivivian/githubtool/

frontend/
├── package.json
├── vite.config.ts           # dev proxy /api -> http://localhost:8080
└── src/
    ├── main.tsx / App.tsx
    ├── api/client.ts        # fetch wrapper + typed responses
    ├── lib/repoView.ts      # sort/filter/toggle pure functions (unit-tested)
    └── components/          # SearchForm, ProfileCard, RepoFilters, RepoList, RepoRow, RecentSearches, ErrorBanner
```

**Structure Decision**: Web-app split (`backend/` + `frontend/`) per user instruction. All view options (FR-005..008) are client-side pure functions over the snapshot returned by the API — the backend never re-sorts or filters.

## Phase 0 → [research.md](research.md)

All technical unknowns resolved; no NEEDS CLARIFICATION outstanding.

## Phase 1 → [data-model.md](data-model.md), [contracts/api.yaml](contracts/api.yaml), [quickstart.md](quickstart.md)

- Data model: `Snapshot` (unique lowercased username, `fetched_at`, `truncated`, JSON payload) and `SearchRecord` (username, `searched_at`).
- Contract: 3 endpoints — `GET /api/users/{username}` (with `refresh` query flag), `GET /api/searches/recent`, plus RFC 7807 `application/problem+json` errors (400 invalid username, 404 unknown user, 429 rate-limited with `retryAfter`, 502 upstream failure).
- Agent context: root `CLAUDE.md` carries the SPECKIT plan pointer.

## Complexity Tracking

No constitution violations; no complexity deviations to justify. Snapshot stored as a JSON blob instead of normalized repo tables — deliberate simplification, rationale in research.md R4.
