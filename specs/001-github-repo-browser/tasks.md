# Tasks: GitHub Repo Browser

**Input**: [plan.md](plan.md), [spec.md](spec.md), [data-model.md](data-model.md), [contracts/api.yaml](contracts/api.yaml), [research.md](research.md)
**Tests**: included per research.md R9 (targeted, not full TDD)

## User stories (derived from spec, priority order)

- **US1 (P1)** — Search a user, see profile card + repo list, friendly errors (scenarios 1, 7, 8; FR-001..004, 010..014). **MVP.**
- **US2 (P2)** — Sort/filter/toggle view options with visible counts (scenarios 2-4; FR-005..008, FR-013).
- **US3 (P2)** — Repo actions: GitHub/homepage links, one-click copy clone URL (scenarios 5-6; FR-009).
- **US4 (P3)** — Snapshot cache, 10-min TTL, force refresh, fetch-time display (scenario 9; FR-015, SC-007).
- **US5 (P3)** — Recent searches on landing page (scenario 10; FR-016).

## Phase 1: Setup

- [x] T001 Create Maven backend skeleton: `backend/pom.xml` (Spring Boot 3.5.x parent; starters web, data-jpa, validation; h2; test), `backend/src/main/resources/application.yml` (H2 file URL `jdbc:h2:file:./data/githubtool`, `app.github.*` props), `backend/.gitignore` (target/, data/)
- [x] T002 Main class + typed config: `backend/src/main/java/dev/luigivivian/githubtool/GithubToolApplication.java`, `config/AppProperties.java` (`app.github.token`, `app.github.cache-ttl=10m`, `app.github.base-url`)
- [x] T003 [P] Scaffold Vite SPA in `frontend/`: `package.json` (react 19, typescript, vitest), `vite.config.ts` (proxy `/api` → `http://localhost:8080`), `tsconfig.json`, `index.html`, `src/main.tsx`, empty `src/App.tsx`
- [x] T004 [P] Root hygiene: `.gitignore` (node_modules, dist, target, data/, .env), `.env.example` (`GITHUB_TOKEN=`)

## Phase 2: Foundational (blocks all stories)

- [x] T005 RestClient bean in `backend/.../config/GithubClientConfig.java`: base URL, `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2026-03-10`, conditional `Authorization: Bearer` when token present
- [x] T006 DTOs/records in `backend/.../github/`: upstream `GithubUserDto`, `GithubRepoDto` (snake_case mapping, ignore unknowns) and API payload records `Profile`, `Repo`, `SnapshotPayload`, `SnapshotResponse` per contracts/api.yaml
- [x] T007 Error pipeline: `backend/.../github/` exceptions (`GithubUserNotFoundException`, `GithubRateLimitedException(retryAfterSeconds)`, `GithubUnavailableException`) + `backend/.../api/ApiExceptionHandler.java` mapping to problem+json 404/429/502 and 400 for `ConstraintViolationException`
- [x] T008 [P] Persistence: `backend/.../snapshot/Snapshot.java`, `SearchRecord.java` entities per data-model.md + `SnapshotRepository`, `SearchRecordRepository` (Spring Data)

## Phase 3: US1 — Search & view (P1, MVP)

**Independent test**: `curl localhost:8080/api/users/octocat` returns snapshot JSON; SPA search shows profile + repos; bad/unknown user shows friendly message.

- [x] T009 [US1] `backend/.../github/GithubClient.java`: `fetchUser`, `fetchRepos` (loop `per_page=100&sort=updated` max 3 pages, set `truncated` when page 3 full and profile `public_repos` > 300), map 404/rate-limit/other per research.md R2
- [x] T010 [US1] `backend/.../snapshot/SnapshotService.java`: fetch live, build payload (Jackson to JSON CLOB), upsert Snapshot, append SearchRecord, return `SnapshotResponse` (fromCache=false for now — TTL read path lands in US4)
- [x] T011 [US1] `backend/.../api/SearchController.java`: `GET /api/users/{username}?refresh=` with `@Pattern` username validation (GitHub regex), lowercase normalization
- [x] T012 [P] [US1] `@RestClientTest` in `backend/src/test/.../github/GithubClientTest.java`: parse fixture JSON, 3-page pagination stop, 404 → UserNotFound, 403+`x-ratelimit-remaining: 0` → RateLimited with reset seconds
- [x] T013 [P] [US1] `@WebMvcTest` in `backend/src/test/.../api/SearchControllerTest.java`: 200 shape, 400 invalid username, 404/429/502 problem+json bodies (mock SnapshotService)
- [x] T014 [US1] `frontend/src/api/client.ts` + `frontend/src/types.ts`: typed `getUserSnapshot(username, refresh)` matching contract, problem+json error parsing
- [x] T015 [US1] Components: `SearchForm.tsx` (trim + client-side regex hint), `ProfileCard.tsx` (avatar, name/login, bio, followers, repo count, total stars), `RepoList.tsx`/`RepoRow.tsx` (name, description, language, stars, forks, updated date, fork/archived badges), `ErrorBanner.tsx`, empty state for zero repos
- [x] T016 [US1] Wire `frontend/src/App.tsx`: search flow, loading state, error rendering, truncation notice ("showing 300 most recently updated")

**Checkpoint**: MVP demoable.

## Phase 4: US2 — View options (P2)

**Independent test**: with a fetched list, each sort/filter/toggle updates rows and "X of Y" instantly; language dropdown only shows present languages; no-match state offers reset.

- [x] T017 [P] [US2] `frontend/src/lib/repoView.ts`: pure functions — sort by stars/forks/name/updated, language set extraction, language filter, hideForks/hideArchived, visible/total counts
- [x] T018 [P] [US2] `frontend/src/lib/repoView.test.ts`: Vitest cases per function incl. combined filters + empty result
- [x] T019 [US2] `frontend/src/components/RepoFilters.tsx` + integrate in `App.tsx`: controls, counts display, "no repositories match" empty state with reset button; view state resets on new search

## Phase 5: US3 — Repo actions (P2)

**Independent test**: repo name and homepage open in new tab; copy button puts clone URL on clipboard with visible confirmation.

- [x] T020 [US3] Extend `frontend/src/components/RepoRow.tsx`: external links (`target=_blank rel=noreferrer`), homepage link only when set, copy-clone-URL via `navigator.clipboard` with fallback + "Copied!" confirmation state

## Phase 6: US4 — Snapshot cache & refresh (P3)

**Independent test**: second search of same user within 10 min returns `fromCache: true` without GitHub call (verify via logs/quota); Refresh button forces live fetch.

- [x] T021 [US4] TTL read path in `backend/.../snapshot/SnapshotService.java`: serve stored payload when `now - fetchedAt <= ttl` and `!refresh`; set `fromCache`/`fetchedAt`; still append SearchRecord on cache hits
- [x] T022 [P] [US4] Unit tests `backend/src/test/.../snapshot/SnapshotServiceTest.java`: fresh hit (no client call), expired → refetch, `refresh=true` bypass, upsert semantics (Mockito, fixed Clock)
- [x] T023 [US4] Frontend cache indicator in `App.tsx`/`ProfileCard.tsx`: "fetched N min ago" + cached badge + Refresh button calling `getUserSnapshot(username, true)`

## Phase 7: US5 — Recent searches (P3)

**Independent test**: landing page lists last searches (≤10, deduped, newest first); clicking one re-runs the search.

- [x] T024 [US5] `backend/.../api/RecentSearchesController.java` + query in `SearchRecordRepository` (latest 10 distinct usernames) + `@DataJpaTest` for dedup/order in `backend/src/test/.../snapshot/SearchRecordRepositoryTest.java`
- [x] T025 [US5] `frontend/src/components/RecentSearches.tsx` + `client.ts` `getRecentSearches()`: render on landing/empty state, click triggers search

## Phase 8: Polish & cross-cutting

- [x] T026 [P] Styling pass on all components (`frontend/src/index.css` + component classes): clean layout, light/dark via `prefers-color-scheme`, responsive list
- [x] T027 [P] Docs: `DEVLOG.md` (session journal), verify root `CLAUDE.md` matches final shape, finalize `.env.example`
- [x] T028 Validation gate: `mvn test` green, `npm test` green, quickstart API smoke (curl 200/400/404), then code-reviewer + qa subagent pass and fix findings

## Dependencies

```
Phase 1 → Phase 2 → US1 → { US2, US3, US4, US5 } → Polish
```

- US2/US3 touch only frontend files; US4 mostly backend; US5 both — all four independent of each other after US1.
- T010 already writes SearchRecord, so US5 needs no backend write path.

## Parallel opportunities

- Setup: T003, T004 alongside T001–T002.
- Foundation: T008 parallel with T005–T007.
- US1: T012, T013 parallel after T009–T011; frontend T014–T016 parallel with backend tests.
- After US1: US2, US3, US4, US5 fully parallelizable (disjoint files); within US2, T017/T018 parallel.
- Polish: T026, T027 parallel; T028 last.

## Implementation strategy

MVP = Phases 1–3 (US1): demoable search → list. Then US2+US3 (interaction value), then US4+US5 (persistence value). Each checkpoint independently testable per criteria above.

**Total**: 28 tasks — Setup 4, Foundational 4, US1 8, US2 3, US3 1, US4 3, US5 2, Polish 3.
