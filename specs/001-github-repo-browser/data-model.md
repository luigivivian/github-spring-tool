# Data Model: GitHub Repo Browser

**Date**: 2026-07-24 | **Plan**: [plan.md](plan.md)

## Persisted entities (H2 via Spring Data JPA)

### Snapshot

One row per searched username; the short-lived local copy required by FR-015.

| Column       | Type          | Constraints                     | Notes                                    |
|--------------|---------------|---------------------------------|------------------------------------------|
| `id`         | BIGINT        | PK, identity                    |                                          |
| `username`   | VARCHAR(39)   | NOT NULL, UNIQUE, lowercased    | GitHub usernames are case-insensitive    |
| `fetched_at` | TIMESTAMP UTC | NOT NULL                        | TTL comparison point (10-min window)     |
| `truncated`  | BOOLEAN       | NOT NULL default false          | true when user has >300 repos (FR-014)   |
| `payload`    | CLOB          | NOT NULL                        | JSON: profile + repos (shape below)      |

- **Lifecycle**: upsert on every live fetch (replace payload + `fetched_at`). Read path: if `now - fetched_at <= TTL` and `refresh` flag not set → serve from row without touching GitHub (SC-007). Expired rows are overwritten lazily on next search — no scheduled cleanup needed.
- **Validation**: `username` matches `^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$` (FR-001), normalized to lowercase before any lookup.

### SearchRecord

Append-only log of successful searches; feeds recent-searches list (FR-016).

| Column        | Type          | Constraints      | Notes                          |
|---------------|---------------|------------------|--------------------------------|
| `id`          | BIGINT        | PK, identity     |                                |
| `username`    | VARCHAR(39)   | NOT NULL, lower  | as searched (normalized)       |
| `searched_at` | TIMESTAMP UTC | NOT NULL         |                                |

- **Read path**: latest 10 distinct usernames ordered by most recent `searched_at` (dedup by username in query).
- **Written**: only on successful searches (found user), cached or live. Failed lookups never pollute history.

## Payload JSON shape (inside `Snapshot.payload`, also the API response body)

```jsonc
{
  "profile": {
    "login": "octocat",
    "name": "The Octocat",         // nullable
    "avatarUrl": "https://...",
    "bio": "...",                   // nullable
    "followers": 1234,
    "publicRepos": 47,
    "htmlUrl": "https://github.com/octocat"
  },
  "repos": [
    {
      "name": "hello-world",
      "description": "...",         // nullable
      "language": "Java",           // nullable
      "stars": 42,
      "forks": 7,
      "updatedAt": "2026-07-20T10:00:00Z",
      "fork": false,
      "archived": false,
      "htmlUrl": "https://github.com/octocat/hello-world",
      "homepage": "https://...",    // nullable
      "cloneUrl": "https://github.com/octocat/hello-world.git"
    }
  ]
}
```

Derived (never stored): total star count for the profile card = sum of `repos[].stars`.

## Client-side view state (not persisted — FR spec "View Options")

| Field          | Values                                   | Default    |
|----------------|------------------------------------------|------------|
| `sortKey`      | `stars` \| `forks` \| `name` \| `updated`| `updated`  |
| `language`     | one of languages present \| `all`        | `all`      |
| `hideForks`    | boolean                                  | false      |
| `hideArchived` | boolean                                  | false      |
| `visible/total`| derived counts (FR-008)                  | —          |

Resets on every new search (spec assumption).
