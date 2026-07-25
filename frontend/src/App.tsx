import { useCallback, useEffect, useState } from 'react'
import { ApiError, getMe, getRecentSearches, getUserSnapshot } from './api/client'
import { applyView, defaultView, languagesOf, type ViewOptions } from './lib/repoView'
import type { ApiProblem, Me, RecentSearch, SnapshotResponse } from './types'
import { AuthHeader } from './components/AuthHeader'
import { ErrorBanner } from './components/ErrorBanner'
import { Insights } from './components/Insights'
import { ProfileCard } from './components/ProfileCard'
import { RecentSearches } from './components/RecentSearches'
import { RepoFilters } from './components/RepoFilters'
import { RepoRow } from './components/RepoRow'
import { SearchForm } from './components/SearchForm'

export default function App() {
  const [snapshot, setSnapshot] = useState<SnapshotResponse | null>(null)
  const [currentUser, setCurrentUser] = useState<string | null>(null)
  const [view, setView] = useState<ViewOptions>(defaultView)
  const [problem, setProblem] = useState<ApiProblem | null>(null)
  const [busy, setBusy] = useState(false)
  const [recent, setRecent] = useState<RecentSearch[]>([])
  const [expandedRepo, setExpandedRepo] = useState<string | null>(null)
  const [me, setMe] = useState<Me | null>(null)

  const loadRecent = useCallback(() => {
    getRecentSearches()
      .then(setRecent)
      .catch(() => setRecent([]))
  }, [])

  useEffect(loadRecent, [loadRecent])

  const search = useCallback(
    async (username: string, refresh = false) => {
      setBusy(true)
      setProblem(null)
      try {
        const result = await getUserSnapshot(username, refresh)
        setSnapshot(result)
        setCurrentUser(username)
        setView(defaultView)
        setExpandedRepo(null)
        loadRecent()
      } catch (error) {
        setSnapshot(null)
        setCurrentUser(null)
        setProblem(
          error instanceof ApiError
            ? error.problem
            : { title: 'Unexpected error', status: 0, detail: 'Something went wrong.' },
        )
      } finally {
        setBusy(false)
      }
    },
    [loadRecent],
  )

  // signed-in landing: load the visitor's own profile automatically
  useEffect(() => {
    getMe()
      .then((identity) => {
        setMe(identity)
        if (identity.authenticated && identity.login) {
          void search(identity.login)
        }
      })
      .catch(() => setMe(null))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const visibleRepos = snapshot ? applyView(snapshot.repos, view) : []
  const languages = snapshot ? languagesOf(snapshot.repos) : []

  return (
    <main className="app">
      <header className="app-header">
        <div>
          <h1>GitHub Repo Browser</h1>
          <p className="tagline">Search a user, explore their public repositories.</p>
        </div>
        <AuthHeader me={me} />
      </header>

      <SearchForm onSearch={(username) => void search(username)} busy={busy} />
      {!snapshot && <RecentSearches searches={recent} onSelect={(u) => void search(u)} />}

      {problem && <ErrorBanner problem={problem} />}

      {snapshot && currentUser && (
        <>
          <ProfileCard
            profile={snapshot.profile}
            repos={snapshot.repos}
            fetchedAt={snapshot.fetchedAt}
            fromCache={snapshot.fromCache}
            onRefresh={() => void search(currentUser, true)}
            busy={busy}
          />

          <Insights username={snapshot.profile.login} />

          {snapshot.truncated && (
            <p className="notice">
              This user has more than 300 repositories — showing the 300 most recently updated.
            </p>
          )}

          {snapshot.repos.length === 0 ? (
            <p className="empty-state">This user has no public repositories.</p>
          ) : (
            <>
              <RepoFilters
                view={view}
                languages={languages}
                visible={visibleRepos.length}
                total={snapshot.repos.length}
                onChange={setView}
              />
              {visibleRepos.length === 0 ? (
                <p className="empty-state">
                  No repositories match the current filters.{' '}
                  <button className="link" onClick={() => setView(defaultView)}>
                    Reset filters
                  </button>
                </p>
              ) : (
                <ul className="repo-list">
                  {visibleRepos.map((repo) => (
                    <RepoRow
                      key={repo.name}
                      repo={repo}
                      owner={snapshot.profile.login}
                      expanded={expandedRepo === repo.name}
                      onToggleDetail={() =>
                        setExpandedRepo(expandedRepo === repo.name ? null : repo.name)
                      }
                    />
                  ))}
                </ul>
              )}
            </>
          )}
        </>
      )}
    </main>
  )
}
