# Project Dev Log

## Working State
**Session:** 2 | **Date:** 2026-07-25

### Active Task
GitHub Repo Browser — full spec-kit cycle (specify → plan → tasks → implement)
- [x] Spec (rev 2: SPA + DB layer added on user request)
- [x] Plan + research + data-model + OpenAPI contract + quickstart
- [x] 28/28 tasks done, including T028 validation gate
- [x] Review fixes applied (timeouts, tx boundary, race, catch-all handler, XSS homepage, copy confirmation)
- [x] Final suites: backend 88/88, frontend 47/47, tsc clean

DONE. Optional follow-ups only (see Technical Debt).

### Key Files (current shape)
**`backend/src/main/java/dev/luigivivian/githubtool/snapshot/SnapshotService.java`** (NEW, ~130 lines)
Core service: TTL cache read (10 min, `refresh` bypass), GitHub fetch + JSON CLOB upsert, search history append. All FR-015/016 logic lives here.

**`backend/src/main/java/dev/luigivivian/githubtool/github/GithubClient.java`** (NEW, ~120 lines)
RestClient wrapper: profile + repos (3×100 pages max, `sort=updated`), maps 404 → UserNotFound, 403/429+quota → RateLimited(retryAfter), rest → Unavailable. Headers set in constructor (no separate config class — @RestClientTest-friendly).

**`backend/src/main/java/dev/luigivivian/githubtool/api/ApiExceptionHandler.java`** (NEW, ~70 lines)
RFC 7807 problem+json: 400 invalid username, 404, 429 with `retryAfterSeconds`, 502. Friendly messages only.

**`frontend/src/App.tsx`** (NEW, ~120 lines)
Single-view SPA: search → snapshot state → client-side view options (pure fns in `lib/repoView.ts`), recent-search chips, error banner, cache badge + Refresh.

**`frontend/src/lib/repoView.ts`** (NEW, ~70 lines)
Pure sort/filter functions (4 sort keys, language, hide forks/archived) — the only frontend logic with unit tests (11 Vitest cases).

### Decisions (active)
- Snapshot = JSON CLOB per username, not normalized tables — backend never queries repo fields (research.md R4)
- Sort/filter 100% client-side (≤300 rows already in browser) — API stays 2 GET endpoints (R5)
- H2 file mode `backend/data/` — survives restarts, zero external services (R3)
- JAVA_HOME must point to `/opt/homebrew/opt/openjdk@21` — system default is Java 8

### Next Steps
1. None required — feature complete and gated
2. Optional: browser E2E via playwright-skill (clipboard/link scenarios have no DOM coverage)

### Blockers
- None

### Watch Out
- `mvn ... | tail` masks Maven exit code — check `${pipestatus[1]}` or surefire reports
- GitHub unauthenticated quota = 60 req/h; smoke tests burn it fast, set GITHUB_TOKEN
- `@Lob` String on H2 = CLOB; if migrating to Postgres, becomes `oid`/`text` — revisit mapping

---
---

## Session Archive

### Session 3 — 2026-07-25: Feature 002 — portfolio features (brainstorm → spec → implement → gate)
**What we did:** Brainstormed portfolio direction (AskUserQuestion), approved design (docs/plans/2026-07-25-portfolio-features-design.md), spec 002, implemented all 3 phases: language donut + 52-week activity chart (Recharts, dataviz-validated palette), inline repo detail panel (react-markdown README, releases, contributors), OAuth2 "Sign in with GitHub" (per-request token resolver, conditional SecurityConfig). Generic api_cache table, 1h TTL, 202-pending never cached. Gate: review found quota risk + 5 majors (all fixed: per-repo tolerance, pending-forever, 401 auth-expired, Insights race + per-chart errors, detail ordering); QA added 105 tests and caught foldToPalette dropping the tail when "Other" pre-exists (fixed). Final: backend 193, frontend 71, tsc clean, live smoke green.
**Files:** backend entity/repository/service/controller/client/config (+13 main, +6 test), frontend charts/panel/auth (+8), application.yml, pom.xml, docs.
**Decisions:** 202 body is `{}` not an array — read stats as String before parsing; SecurityConfig activates oauth2Login only when registration bean exists so slices/credential-less boots stay permit-all.

### Session 2 — 2026-07-25: README + Spring layered package reorg
**What we did:** Created README.md (run guide, API, troubleshooting). Reorganized backend from mixed feature packages (api/github/snapshot) into Spring layered layout: config/controller/service/repository/entity/dto/client/exception — 30 files moved via script, imports rewritten. 88/88 tests green after.
**Files:** README.md, all backend/src/**/*.java (packages), CLAUDE.md, DEVLOG.md
**Decisions:** Layered layout over feature packages — user asked for a recognizable standard; frontend untouched (already api/lib/components).

### Session 1 — 2026-07-24: Full spec-to-implementation cycle
**What we did:** Spec-kit flow end to end: spec (rev 2 added persistence + SPA split), plan/research/contract, 28 tasks, implemented Spring Boot 3.5 API (snapshot cache in H2/JPA, problem+json errors) + React 19/Vite SPA (client-side view options, copy clone URL, recent searches). Backend 19 tests + frontend 11 tests green; live smoke test validated cache hit, 400/404, recent list.
**Files:** backend/* (25 files), frontend/* (17 files), specs/001-github-repo-browser/*, CLAUDE.md, .env.example, .gitignore
**Decisions:** JSON CLOB snapshot, client-side filtering, H2 file mode, RestClient over WebClient.

---

## Milestones
- [x] MVP: search user → profile + repo list with friendly errors
- [x] View options (sort/filter/toggles) client-side
- [x] Snapshot cache with TTL + force refresh
- [x] Recent searches
- [ ] Review + QA gate passed (T028)

## Mistakes & Lessons
### 2026-07-25 — GitHub 202 body is "{}", not an array
**What happened:** activity endpoint 502'd in live smoke; tests passed because the mock returned a bodyless 202.
**Root cause:** `/stats/commit_activity` returns `202` with `{}` while computing; Jackson failed deserializing to `CommitWeekDto[]` before the status check ran.
**How we fixed it:** read the body as String, gate on status 202 first, only parse when it starts with `[`; non-array 200 = empty history (never "pending forever").
**Lesson:** mock fixtures must mirror real upstream quirks — always live-smoke integrations at least once.

### 2026-07-24 — @TestConfiguration bean name collided with app bean
**What happened:** `FixedClockConfig.clock()` in a `@RestClientTest` slice broke all 6 tests with `BeanDefinitionOverrideException`.
**Root cause:** the `@SpringBootConfiguration` class's `@Bean` methods load in every slice; same bean name `clock`, overriding disabled by default.
**How we fixed it:** replaced with `@MockitoBean Clock` + `when(clock.instant()).thenReturn(NOW)`.
**Lesson:** in slice tests, mock beans instead of redefining ones the app class already provides; a bare rename isn't safe either (by-name injection would grab the real bean).

### 2026-07-24 — Regex quantifier bounded groups, not characters
**What happened:** SPA regex `^[a-zA-Z0-9](?:-?[a-zA-Z0-9]){0,38}$` accepted usernames up to 77 chars; backend rejected them → server error banner instead of inline hint.
**Root cause:** each repeated group matches up to 2 chars (`-a`), so `{0,38}` ≠ 39-char cap.
**How we fixed it:** used the contract pattern with lookahead `^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$` (1 char per repetition).
**Lesson:** when a length limit matters, make each quantified unit consume exactly one character — or add an explicit length check.

### 2026-07-24 — Pipeline exit code masked Maven failure
**What happened:** First `mvn test` reported exit 0 but had a compilation error (`PER_PAGE is not public`).
**Root cause:** `mvn ... | tail -60` — pipeline exit code comes from `tail`, not `mvn`.
**How we fixed it:** Made constants public; started checking `${pipestatus[1]}` (zsh) and surefire report files instead of trusting piped exit codes.
**Lesson:** Never trust exit code of a piped build command.

## Technical Debt & Future Ideas
- Insights quota (review 002, critical): uncached profile = up to 35 upstream calls; anonymous 60/h exhausts fast. Mitigated by 1h cache, per-repo failure tolerance, rate-limit abort + docs; real fix candidates: parallel fetch with cap, anonymous-mode lower caps, request coalescing in ApiCacheService (two concurrent misses both fetch today).
- Pending activity has no server-side backoff — refetch happens on every user-triggered request until GitHub finishes computing.
- `client.ts` fetch calls rely on same-origin default (no `credentials` option) — breaks session if SPA/API ever split origins.
- `api_cache` rows for once-viewed keys persist forever (no purge job).
- Serve stale snapshot with warning when rate-limited (graceful degradation)
- Postgres profile for deployment (config-only swap per R3); revisit `@Lob` CLOB mapping
- Browser E2E suite (playwright-skill) — clipboard copy, links, visible-count UI have zero DOM coverage (no jsdom/RTL deps)
- Export `safeHomepage` (RepoRow) and username regex (SearchForm) to `lib/` for cheap unit coverage; regex duplicated in contract + backend + SPA (keep in sync)
- `repoView.ts` `updated` sort uses `localeCompare` on ISO strings — misorders if timestamps ever gain fractional seconds (GitHub emits whole seconds today)
- `SnapshotService` race fallback (`DataIntegrityViolationException`) untested — needs true concurrency harness
- `search_record` grows unboundedly — prune job if deployment runs long
