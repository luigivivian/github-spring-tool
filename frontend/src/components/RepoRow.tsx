import { useState } from 'react'
import type { Repo } from '../types'
import { RepoDetailPanel } from './RepoDetailPanel'

interface Props {
  repo: Repo
  owner: string
  expanded: boolean
  onToggleDetail: () => void
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

// homepage is free text set by the repo owner — only render absolute http(s) links
function safeHomepage(url: string | null): string | null {
  if (!url) return null
  try {
    const protocol = new URL(url).protocol
    return protocol === 'http:' || protocol === 'https:' ? url : null
  } catch {
    return null
  }
}

type CopyState = 'idle' | 'copied' | 'failed'

export function RepoRow({ repo, owner, expanded, onToggleDetail }: Props) {
  const [copyState, setCopyState] = useState<CopyState>('idle')
  const homepage = safeHomepage(repo.homepage)

  async function copyCloneUrl() {
    let ok = false
    try {
      await navigator.clipboard.writeText(repo.cloneUrl)
      ok = true
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = repo.cloneUrl
      document.body.appendChild(textarea)
      textarea.select()
      try {
        ok = document.execCommand('copy')
      } catch {
        ok = false
      }
      textarea.remove()
    }
    setCopyState(ok ? 'copied' : 'failed')
    setTimeout(() => setCopyState('idle'), 2000)
  }

  const copyLabel =
    copyState === 'copied' ? 'Copied!' : copyState === 'failed' ? 'Copy failed' : 'Copy clone URL'

  return (
    <li className={expanded ? 'repo-row expanded' : 'repo-row'}>
      <div className="repo-main">
        <h3>
          <a href={repo.htmlUrl} target="_blank" rel="noreferrer">
            {repo.name}
          </a>
          {repo.fork && <span className="badge">fork</span>}
          {repo.archived && <span className="badge badge-archived">archived</span>}
        </h3>
        {repo.description && <p className="description">{repo.description}</p>}
        <p className="repo-meta">
          {repo.language && <span className="language">{repo.language}</span>}
          <span>★ {repo.stars.toLocaleString()}</span>
          <span>⑂ {repo.forks.toLocaleString()}</span>
          <span>Updated {formatDate(repo.updatedAt)}</span>
        </p>
      </div>
      <div className="repo-actions">
        {homepage && (
          <a className="action" href={homepage} target="_blank" rel="noreferrer">
            Homepage
          </a>
        )}
        <button className="action" onClick={copyCloneUrl}>
          {copyLabel}
        </button>
        <button className="action" onClick={onToggleDetail}>
          {expanded ? 'Hide details' : 'Details'}
        </button>
      </div>
      {expanded && <RepoDetailPanel owner={owner} repo={repo.name} />}
    </li>
  )
}
