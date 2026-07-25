# Feature Specification: Portfolio Features

**Feature Directory**: `specs/002-portfolio-features`
**Created**: 2026-07-25
**Status**: Draft
**Design**: [docs/plans/2026-07-25-portfolio-features-design.md](../../docs/plans/2026-07-25-portfolio-features-design.md) (approved)
**Input**: User description: "Portfolio features for the GitHub Repo Browser: (1) language breakdown chart and commit-activity heatmap; (2) inline repo detail panel with rendered README, releases, contributors, per-repo languages; (3) Sign in with GitHub so logged-in visitors use their own request quota; all new data cached server-side for 1 hour."

## User Scenarios & Testing *(mandatory)*

### Primary User Story

A visitor exploring a GitHub user's profile now gets a richer picture at a glance: a chart of which languages dominate that user's work and a week-by-week activity graph of their most popular repositories. Clicking any repository opens an inline detail panel with its rendered README, latest releases, and top contributors — no need to leave the app. Visitors who sign in with their own GitHub account browse under their personal request allowance and see their identity in the header; everyone else keeps using the app exactly as before.

### Acceptance Scenarios

1. **Given** a searched user's profile is displayed, **When** the language view loads, **Then** a chart shows the distribution (as percentages) of programming languages across that user's top 25 non-fork repositories ranked by stars.
2. **Given** a searched user's profile is displayed, **When** the activity view loads, **Then** a graph shows total weekly commit counts for the last 52 weeks, summed across the user's top 10 repositories.
3. **Given** the upstream provider is still preparing activity statistics, **When** the activity view loads, **Then** the page shows a "still computing — try again shortly" state (with any partial data it already has) instead of an error or blank area.
4. **Given** a repository list, **When** the visitor clicks a repository row, **Then** an inline detail panel opens showing the rendered README, up to the 5 most recent releases, up to the 10 top contributors, and that repository's own language distribution.
5. **Given** a repository lacking a README, releases, or contributor data, **When** its detail panel opens, **Then** the panel renders cleanly with those sections omitted or placeholdered — never broken or empty-looking.
6. **Given** an anonymous visitor, **When** they choose "Sign in with GitHub" and complete authorization, **Then** their name/avatar appears in the header, a sign-out action becomes available, and subsequent searches consume their personal request allowance instead of the shared one.
7. **Given** a signed-in visitor, **When** they sign out, **Then** the app returns to the anonymous behavior with no residual access to their identity.
8. **Given** any of the new views fails (provider quota, provider outage), **When** the visitor triggers it, **Then** they see the same style of friendly, human-readable message used elsewhere in the app — never a technical error.
9. **Given** any new view was loaded less than 1 hour ago for the same user or repository, **When** any visitor requests it again, **Then** it is served from the local copy without consuming external request quota.

### Edge Cases

- User whose top repositories have no detectable language → language chart shows an explicit "no language data" state.
- User with zero non-fork repositories → language chart and activity graph show empty states, profile still renders.
- README with embedded scripts or active content → rendered safely; no embedded content may execute in the visitor's browser.
- Very large README → panel renders scrollably without freezing the page.
- Sign-in flow cancelled or refused by the visitor → app returns to anonymous state with a neutral message, no error screen.
- Signed-in visitor's authorization expires or is revoked upstream → next request degrades gracefully to anonymous behavior with a prompt to sign in again.
- Repository renamed or deleted between listing and detail click → detail panel shows the standard not-found message.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST show, on a searched user's profile, a language-distribution chart aggregating the user's top 25 non-fork repositories ranked by stars, expressed as percentages of code volume; languages below 2% MAY be grouped as "Other".
- **FR-002**: System MUST show a weekly activity graph covering the most recent 52 weeks, summing commit counts across the searched user's top 10 repositories (same ranking).
- **FR-003**: When upstream statistics are still being prepared, the system MUST present a distinct "still computing" state with any partial data available, and a way to retry; this state is informational, not an error.
- **FR-004**: Clicking a repository row MUST open an inline detail panel containing: the repository's README rendered as formatted text, up to 5 most recent releases (name, date), up to 10 top contributors (name, contribution count), and the repository's own language distribution.
- **FR-005**: README content MUST be rendered safely — no script or active content originating from the README may execute in the visitor's browser.
- **FR-006**: Detail panels MUST adapt when data is missing (no README, no releases, no contributors) with per-section empty states.
- **FR-007**: Visitors MUST be able to sign in with their GitHub account and sign out; while signed in, their display name/avatar appears in the header.
- **FR-008**: Requests made on behalf of a signed-in visitor MUST consume that visitor's personal upstream request allowance; anonymous visitors continue on the shared allowance, unchanged.
- **FR-009**: Sign-in MUST request only public-identity permission; the system MUST NOT request access to, fetch, or display private repositories.
- **FR-010**: All data for the new views MUST be kept in a server-side local copy for 1 hour; repeat requests within that window MUST NOT consume external request quota.
- **FR-011**: All failure situations in the new views MUST produce the same friendly, human-readable error treatment already established (unknown user/repo, quota exhausted, provider unavailable).

### Key Entities

- **Language Aggregate**: per searched user — list of (language, share%) computed across top 25 non-fork repos; fetched-at timestamp.
- **Activity Series**: per searched user — 52 weekly commit totals across top 10 repos; completeness flag ("still computing").
- **Repository Detail**: per repository — rendered README text, recent releases, top contributors, per-repo language shares.
- **Visitor Identity**: present only while signed in — display name, avatar, and the visitor's personal request allowance; never persisted beyond the session.
- **Cached View Data**: server-side copy of any of the above, keyed by user/repository, expiring after 1 hour.

## Success Criteria *(mandatory)*

- **SC-001**: For a typical account, the language chart and activity graph appear within 5 seconds of the profile loading (first fetch) and instantly on repeats within the hour.
- **SC-002**: Repeat views of any new data within 1 hour consume zero external request quota.
- **SC-003**: A repository detail panel opens with a single click and renders within 3 seconds on first fetch.
- **SC-004**: A signed-in visitor can perform at least 100 searches per hour without ever seeing a shared-quota limit message.
- **SC-005**: 100% of failure situations in the new views produce a human-readable message; embedded README content never executes in the browser (0 tolerated occurrences).
- **SC-006**: Sign-in completes in at most 3 visitor actions from the header; sign-out in 1.

## Assumptions

- Ranking caps (top 25 non-fork for languages, top 10 for activity, 5 releases, 10 contributors) come from the approved design and bound external request cost per view.
- The 1-hour freshness window is a deliberate trade-off: these datasets change slowly and are expensive to assemble.
- Delivery is phased and independently shippable: (1) charts, (2) repo detail, (3) sign-in — per the approved design.
- Signed-in state is session-scoped; no visitor accounts or preferences are stored beyond the session.
- Existing behavior (search, listing, view options, snapshot cache, recent searches) is unchanged for anonymous visitors.
- UI language remains English.
