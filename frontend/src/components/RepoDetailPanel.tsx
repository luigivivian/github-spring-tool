import { useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import { ApiError, getRepoDetail } from '../api/client'
import type { RepoDetailResponse } from '../types'
import { useChartTheme } from '../lib/chartTheme'
import { foldToPalette } from '../lib/slices'

interface Props {
  owner: string
  repo: string
}

export function RepoDetailPanel({ owner, repo }: Props) {
  const theme = useChartTheme()
  const [detail, setDetail] = useState<RepoDetailResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setDetail(null)
    setError(null)
    getRepoDetail(owner, repo)
      .then((result) => {
        if (!cancelled) setDetail(result)
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.problem.detail : 'Could not load repository details.')
        }
      })
    return () => {
      cancelled = true
    }
  }, [owner, repo])

  if (error) {
    return <div className="repo-detail"><p className="notice small">{error}</p></div>
  }
  if (!detail) {
    return <div className="repo-detail loading">Loading details…</div>
  }

  const languages = foldToPalette(detail.languages)

  return (
    <div className="repo-detail">
      {languages.length > 0 && (
        <div className="detail-section">
          <h4>Languages</h4>
          <div className="language-bar" role="img"
              aria-label={languages.map((l) => `${l.language} ${l.percent}%`).join(', ')}>
            {languages.map((share, index) => (
              <span
                key={share.language}
                style={{ width: `${share.percent}%`, background: theme.colors[index] }}
                title={`${share.language} ${share.percent}%`}
              />
            ))}
          </div>
          <ul className="chart-legend row">
            {languages.map((share, index) => (
              <li key={share.language}>
                <span className="swatch" style={{ background: theme.colors[index] }} />
                <span className="legend-label">{share.language}</span>
                <span className="legend-value">{share.percent}%</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="detail-columns">
        <div className="detail-section">
          <h4>Releases</h4>
          {detail.releases.length === 0 ? (
            <p className="muted">No releases published.</p>
          ) : (
            <ul className="release-list">
              {detail.releases.map((release) => (
                <li key={release.htmlUrl}>
                  <a href={release.htmlUrl} target="_blank" rel="noreferrer">{release.name}</a>
                  {release.publishedAt && (
                    <span className="muted"> — {new Date(release.publishedAt).toLocaleDateString('en-US')}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="detail-section">
          <h4>Top contributors</h4>
          {detail.contributors.length === 0 ? (
            <p className="muted">No contributor data.</p>
          ) : (
            <ul className="contributor-list">
              {detail.contributors.map((contributor) => (
                <li key={contributor.login}>
                  <img src={contributor.avatarUrl} alt="" width={20} height={20} />
                  <a href={contributor.htmlUrl} target="_blank" rel="noreferrer">
                    {contributor.login}
                  </a>
                  <span className="muted">{contributor.contributions.toLocaleString()}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="detail-section">
        <h4>README</h4>
        {detail.readme ? (
          <div className="readme">
            <ReactMarkdown>{detail.readme}</ReactMarkdown>
          </div>
        ) : (
          <p className="muted">This repository has no README.</p>
        )}
      </div>
    </div>
  )
}
