import { describe, expect, it } from 'vitest'
import type { Repo } from '../types'
import { applyView, defaultView, languagesOf } from './repoView'

function repo(overrides: Partial<Repo>): Repo {
  return {
    name: 'repo',
    description: null,
    language: null,
    stars: 0,
    forks: 0,
    updatedAt: '2026-07-01T00:00:00Z',
    fork: false,
    archived: false,
    htmlUrl: 'https://github.com/u/repo',
    homepage: null,
    cloneUrl: 'https://github.com/u/repo.git',
    ...overrides,
  }
}

const repos: Repo[] = [
  repo({ name: 'alpha', language: 'Java', stars: 5, forks: 3, updatedAt: '2026-07-10T00:00:00Z' }),
  repo({ name: 'beta', language: 'TypeScript', stars: 20, forks: 1, updatedAt: '2026-07-20T00:00:00Z', fork: true }),
  repo({ name: 'gamma', language: 'Java', stars: 1, forks: 9, updatedAt: '2026-07-15T00:00:00Z', archived: true }),
  repo({ name: 'delta', language: null, stars: 10, forks: 0, updatedAt: '2026-07-01T00:00:00Z' }),
]

describe('languagesOf', () => {
  it('returns distinct sorted languages, skipping null', () => {
    expect(languagesOf(repos)).toEqual(['Java', 'TypeScript'])
  })

  it('returns empty for no languages', () => {
    expect(languagesOf([repo({}), repo({})])).toEqual([])
  })
})

describe('applyView sorting', () => {
  it('sorts by stars descending', () => {
    const names = applyView(repos, { ...defaultView, sortKey: 'stars' }).map((r) => r.name)
    expect(names).toEqual(['beta', 'delta', 'alpha', 'gamma'])
  })

  it('sorts by forks descending', () => {
    const names = applyView(repos, { ...defaultView, sortKey: 'forks' }).map((r) => r.name)
    expect(names).toEqual(['gamma', 'alpha', 'beta', 'delta'])
  })

  it('sorts by name ascending', () => {
    const names = applyView(repos, { ...defaultView, sortKey: 'name' }).map((r) => r.name)
    expect(names).toEqual(['alpha', 'beta', 'delta', 'gamma'])
  })

  it('sorts by updated, most recent first (default)', () => {
    const names = applyView(repos, defaultView).map((r) => r.name)
    expect(names).toEqual(['beta', 'gamma', 'alpha', 'delta'])
  })
})

describe('applyView filtering', () => {
  it('filters by language', () => {
    const names = applyView(repos, { ...defaultView, language: 'Java' }).map((r) => r.name)
    expect(names).toEqual(['gamma', 'alpha'])
  })

  it('hides forks', () => {
    const names = applyView(repos, { ...defaultView, hideForks: true }).map((r) => r.name)
    expect(names).not.toContain('beta')
  })

  it('hides archived', () => {
    const names = applyView(repos, { ...defaultView, hideArchived: true }).map((r) => r.name)
    expect(names).not.toContain('gamma')
  })

  it('combines filters down to empty result', () => {
    const result = applyView(repos, {
      sortKey: 'stars',
      language: 'TypeScript',
      hideForks: true,
      hideArchived: false,
    })
    expect(result).toEqual([])
  })

  it('does not mutate input', () => {
    const before = repos.map((r) => r.name)
    applyView(repos, { ...defaultView, sortKey: 'stars', hideForks: true })
    expect(repos.map((r) => r.name)).toEqual(before)
  })
})
