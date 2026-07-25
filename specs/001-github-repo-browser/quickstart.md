# Quickstart: GitHub Repo Browser

Validation guide — proves the feature end-to-end. Contracts in [contracts/api.yaml](contracts/api.yaml); entities in [data-model.md](data-model.md).

## Prerequisites

- Java 21 (`brew install openjdk@21`) — export `JAVA_HOME=/opt/homebrew/opt/openjdk@21`
- Maven 3.9+ (`brew install maven`)
- Node 20+ / npm
- Optional: `GITHUB_TOKEN` env var (raises GitHub quota 60 → 5000 req/h). Copy `.env.example` → `.env`, fill token, `source` it or export manually.

## Run

```bash
# terminal 1 — backend on :8080
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn spring-boot:run

# terminal 2 — frontend on :5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

## Validation scenarios (map to spec acceptance scenarios)

| # | Do | Expect |
|---|----|--------|
| 1 | Search `octocat` | Profile card (avatar, name, bio, followers, repo count, total stars) + repo list |
| 2 | Sort by stars | Highest-starred repo first |
| 3 | Pick a language in filter | Only repos of that language; dropdown only lists languages present |
| 4 | Toggle hide forks / hide archived | Rows disappear; "X of Y repositories" count updates |
| 5 | Click copy icon on a repo | Clone URL on clipboard + visual confirmation |
| 6 | Click repo name / homepage link | Opens GitHub page / homepage in new tab |
| 7 | Search `no-such-user-xyz-00000` | Friendly not-found message naming the user; can search again |
| 8 | Exhaust quota (60 unauth. searches) or revoke token | Friendly rate-limit message with retry timing |
| 9 | Re-search same user within 10 min | Instant result, "fetched N min ago" + `fromCache: true`; Refresh button forces live fetch |
| 10 | Reload landing page after searches | Recent searches list (≤10, deduped); click re-runs search |

## API smoke (backend alone)

```bash
curl -s localhost:8080/api/users/octocat | head -c 400        # 200 snapshot JSON
curl -s localhost:8080/api/users/bad--name -i | head -1        # 400
curl -s localhost:8080/api/users/no-such-user-xyz-00000 -i | head -1  # 404
curl -s localhost:8080/api/searches/recent                     # recent list
```

## Tests

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn test   # JUnit: client parsing, TTL, error mapping, controller
cd frontend && npm test                                          # Vitest: repoView sort/filter/toggles
```

Expected: all green. H2 data file lands in `backend/data/` (gitignored).
