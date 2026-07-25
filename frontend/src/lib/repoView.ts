import type { Repo } from '../types'

export type SortKey = 'stars' | 'forks' | 'name' | 'updated'

export interface ViewOptions {
  sortKey: SortKey
  language: string
  hideForks: boolean
  hideArchived: boolean
}

export const defaultView: ViewOptions = {
  sortKey: 'updated',
  language: 'all',
  hideForks: false,
  hideArchived: false,
}

export function languagesOf(repos: Repo[]): string[] {
  const languages = new Set<string>()
  for (const repo of repos) {
    if (repo.language) {
      languages.add(repo.language)
    }
  }
  return [...languages].sort((a, b) => a.localeCompare(b))
}

export function applyView(repos: Repo[], view: ViewOptions): Repo[] {
  const filtered = repos.filter((repo) => {
    if (view.hideForks && repo.fork) return false
    if (view.hideArchived && repo.archived) return false
    if (view.language !== 'all' && repo.language !== view.language) return false
    return true
  })
  return sortRepos(filtered, view.sortKey)
}

function sortRepos(repos: Repo[], sortKey: SortKey): Repo[] {
  const sorted = [...repos]
  switch (sortKey) {
    case 'stars':
      sorted.sort((a, b) => b.stars - a.stars)
      break
    case 'forks':
      sorted.sort((a, b) => b.forks - a.forks)
      break
    case 'name':
      sorted.sort((a, b) => a.name.localeCompare(b.name))
      break
    case 'updated':
      sorted.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      break
  }
  return sorted
}
