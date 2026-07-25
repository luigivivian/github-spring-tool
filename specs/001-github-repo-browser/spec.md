# Feature Specification: GitHub Repo Browser

**Feature Directory**: `specs/001-github-repo-browser`
**Created**: 2026-07-24
**Status**: Draft
**Input**: User description: "Spring Boot web application that integrates with the GitHub REST API (version 2026-03-10). User enters a GitHub username in a search form; the app fetches that user's public repositories and renders a customized server-side page listing the repositories with useful options: sort by stars/forks/name/last-updated, filter by language, hide forks/archived repos, copy clone URL, links to repo and homepage, plus a profile summary card (avatar, name, bio, followers, total stars). Optional GITHUB_TOKEN env var raises rate limits. Handles user-not-found and rate-limit errors gracefully."

## User Scenarios & Testing *(mandatory)*

### Primary User Story

A developer wants to quickly explore another GitHub user's public work. They open the app, type a GitHub username into a search box, and receive a single page showing that user's profile summary and a browsable list of their public repositories. They can reorder the list (most-starred first, most-forked, alphabetical, or most recently updated), narrow it by programming language, hide forks or archived projects, and copy a repository's clone URL with one click to start working with it locally.

### Acceptance Scenarios

1. **Given** the app landing page, **When** the visitor submits a valid GitHub username that exists, **Then** the page shows that user's profile summary (avatar, display name, bio, follower count, public repository count, combined star count) and a list of their public repositories.
2. **Given** a repository listing is displayed, **When** the visitor selects "sort by stars", **Then** the list reorders with the highest-starred repository first.
3. **Given** a repository listing is displayed, **When** the visitor selects a language from the language filter, **Then** only repositories whose primary language matches are shown, and the filter shows only languages actually present in that user's repositories.
4. **Given** a repository listing containing forks and archived repositories, **When** the visitor enables "hide forks" and/or "hide archived", **Then** those repositories are removed from the visible list and the visible count updates.
5. **Given** a repository row, **When** the visitor clicks the copy action, **Then** the repository's clone URL is placed on their clipboard and the UI confirms the copy.
6. **Given** a repository row, **When** the visitor clicks the repository name or the homepage link (when one exists), **Then** the corresponding external page opens.
7. **Given** the search form, **When** the visitor submits a username that does not exist on GitHub, **Then** the page shows a friendly "user not found" message with the searched name and lets them search again — no technical error is exposed.
8. **Given** the upstream API quota is exhausted, **When** the visitor performs a search, **Then** the page explains that the request limit was reached, indicates when they can retry, and suggests the operator configure an access token — no technical error is exposed.
9. **Given** a user was successfully searched less than 10 minutes ago, **When** any visitor searches that user again, **Then** results come from the local snapshot (no GitHub call), the page shows the fetch time, and a force-refresh action fetches live data on demand.
10. **Given** previous successful searches exist, **When** a visitor opens the landing page, **Then** the most recent searches (up to 10, deduplicated) are listed and clicking one re-runs that search.

### Edge Cases

- Username exists but has zero public repositories → show the profile summary plus an explicit empty state, not a blank page.
- Filters combine to exclude every repository → show "no repositories match the current filters" with an obvious way to reset filters.
- Repository missing optional data (no description, no primary language, no homepage) → row renders cleanly with those elements omitted or placeholdered.
- Username submitted with invalid characters, leading/trailing spaces, or empty → input is trimmed and validated before any external call; invalid input produces inline guidance.
- User with a very large number of repositories → listing covers at least the cap defined in FR-014 and clearly states when results are truncated.
- Upstream service unreachable or slow → visitor sees a friendly "GitHub is unavailable, try again" message rather than a hung or broken page.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a search form where a visitor enters a GitHub username; input is trimmed and validated against GitHub's username rules (alphanumerics and hyphens, max 39 chars) before any lookup.
- **FR-002**: System MUST fetch the searched user's public profile and public repositories live from GitHub at search time (no stale local copy).
- **FR-003**: System MUST display a profile summary card containing avatar, display name (or username when absent), bio, follower count, public repository count, and the combined star count across listed repositories.
- **FR-004**: System MUST list repositories showing, per repository: name, description, primary language, star count, fork count, last-updated date, and badges for fork/archived status.
- **FR-005**: Visitors MUST be able to sort the listing by stars, forks, name (A→Z), or last-updated (most recent first); default order is last-updated.
- **FR-006**: Visitors MUST be able to filter the listing to a single primary language chosen from the set of languages present in the fetched repositories.
- **FR-007**: Visitors MUST be able to hide forked repositories and/or archived repositories via independent toggles.
- **FR-008**: Sort, language filter, and toggles MUST be combinable, and the page MUST show the visible count vs. total fetched (e.g., "12 of 47 repositories").
- **FR-009**: Each repository row MUST offer: a link to the repository on GitHub, a link to its homepage when one is set, and a one-click copy of its clone URL with visible confirmation.
- **FR-010**: When the searched username does not exist, the system MUST show a friendly not-found message naming the searched user and allowing a new search.
- **FR-011**: When the upstream request quota is exhausted, the system MUST show a friendly rate-limit message including retry timing when available; raw upstream errors and stack traces MUST never reach the visitor.
- **FR-012**: Operators MUST be able to configure an optional GitHub access token via environment configuration; when present it is used for upstream calls to raise quota limits, and it MUST never be exposed to visitors.
- **FR-013**: When a user has zero public repositories, or active filters exclude all repositories, the system MUST show an explicit empty state (distinct messages for each case) with a way to reset.
- **FR-014**: System MUST fetch up to 300 repositories per user (most recently updated first); when a user has more, the page MUST state that the list is truncated and show the fetched subset.
- **FR-015**: System MUST keep a short-lived local copy of each fetched profile-and-repositories snapshot (freshness window: 10 minutes). Repeat searches for the same user within the window MUST be served from the local copy without contacting GitHub; the page MUST show when the data was fetched and offer a one-click force-refresh that bypasses the window.
- **FR-016**: Landing page MUST list the most recent successful searches (up to 10, most recent first, deduplicated by username) so a visitor can re-run any of them with one click.

### Key Entities

- **GitHub User Profile**: the searched account's public identity — username, display name, avatar, bio, follower count, public repository count.
- **Repository**: one public repository — name, description, primary language, star count, fork count, last-updated timestamp, fork flag, archived flag, repository URL, homepage URL, clone URL.
- **View Options**: the visitor's current sort key, language filter, and hide-forks / hide-archived toggles; applied per search, not persisted between visits.
- **Cached Snapshot**: a stored copy of one user's profile and repository list — searched username, fetch timestamp, and the data itself; expires after the freshness window (FR-015).
- **Search Record**: one successful search — username and time of search; feeds the recent-searches list (FR-016).

## Success Criteria *(mandatory)*

- **SC-001**: A first-time visitor can go from landing page to viewing a user's repository list in under 15 seconds, with no instructions.
- **SC-002**: For accounts with up to 300 public repositories, 100% of them appear in the listing; beyond that, truncation is always disclosed.
- **SC-003**: Applying any sort, filter, or toggle updates the visible list in under 2 seconds.
- **SC-004**: 100% of failure situations (unknown user, quota exhausted, upstream unavailable, invalid input) produce a human-readable message; a visitor never sees a raw technical error.
- **SC-005**: Copying a clone URL takes exactly one click and produces visible confirmation.
- **SC-006**: Repository data shown is never older than the 10-minute freshness window, the fetch time is always visible, and a visitor can force fresh data with one click.
- **SC-007**: A repeat search for the same user within the freshness window renders without consuming any GitHub request quota.

## Assumptions

- Public data only: no visitor login or account system; anyone can search any GitHub username.
- Single-user lookup per search; comparing multiple users side-by-side is out of scope.
- The 300-repository cap (FR-014) is a deliberate scope bound to keep pages fast; industry-typical accounts fall well under it.
- View options reset on each new search; persisting visitor preferences is out of scope.
- UI language is English.
- Fetched GitHub data is persisted locally only as short-lived snapshots (FR-015) plus a recent-searches history (FR-016); snapshots older than the freshness window are refreshed on next search. The 10-minute window is a default the operator can tune.
- Recent searches and snapshots are deployment-wide (shared by all visitors), consistent with the no-login model.
- The optional access token is operator-level configuration shared by the whole deployment, not per-visitor.
