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

describe('applyView edge cases', () => {
  it('returns an empty array for no repositories', () => {
    for (const sortKey of ['stars', 'forks', 'name', 'updated'] as const) {
      expect(applyView([], { ...defaultView, sortKey })).toEqual([])
    }
  })

  it('returns a single repository unchanged', () => {
    const only = [repo({ name: 'solo', language: 'Go' })]

    expect(applyView(only, defaultView)).toEqual(only)
    expect(applyView(only, { ...defaultView, language: 'Go' })).toEqual(only)
  })

  it('always returns a new array instance', () => {
    const input = [repo({ name: 'a' })]

    expect(applyView(input, defaultView)).not.toBe(input)
  })

  it('does not mutate a frozen input array', () => {
    const input = Object.freeze([
      repo({ name: 'b', stars: 1 }),
      repo({ name: 'a', stars: 9 }),
    ]) as unknown as Repo[]

    const result = applyView(input, { ...defaultView, sortKey: 'stars' })

    expect(result.map((r) => r.name)).toEqual(['a', 'b'])
    expect(input.map((r) => r.name)).toEqual(['b', 'a'])
  })

  it('keeps the original order for equal sort values (stable sort)', () => {
    const tied = [
      repo({ name: 'first', stars: 5, forks: 2 }),
      repo({ name: 'second', stars: 5, forks: 2 }),
      repo({ name: 'third', stars: 5, forks: 2 }),
    ]

    expect(applyView(tied, { ...defaultView, sortKey: 'stars' }).map((r) => r.name)).toEqual([
      'first',
      'second',
      'third',
    ])
    expect(applyView(tied, { ...defaultView, sortKey: 'forks' }).map((r) => r.name)).toEqual([
      'first',
      'second',
      'third',
    ])
  })

  it('sorts names case-insensitively for a human A-Z order', () => {
    const mixed = [repo({ name: 'Zebra' }), repo({ name: 'apple' }), repo({ name: 'Banana' })]

    expect(applyView(mixed, { ...defaultView, sortKey: 'name' }).map((r) => r.name)).toEqual([
      'apple',
      'Banana',
      'Zebra',
    ])
  })

  it('orders identical timestamps deterministically and newest first', () => {
    const byDate = [
      repo({ name: 'old', updatedAt: '2025-12-31T23:59:59Z' }),
      repo({ name: 'newest', updatedAt: '2026-07-24T12:00:00Z' }),
      repo({ name: 'tie-a', updatedAt: '2026-07-10T00:00:00Z' }),
      repo({ name: 'tie-b', updatedAt: '2026-07-10T00:00:00Z' }),
    ]

    expect(applyView(byDate, defaultView).map((r) => r.name)).toEqual([
      'newest',
      'tie-a',
      'tie-b',
      'old',
    ])
  })

  it('treats large star and fork counts numerically, not lexicographically', () => {
    const counts = [
      repo({ name: 'nine', stars: 9, forks: 9 }),
      repo({ name: 'hundred', stars: 100, forks: 100 }),
      repo({ name: 'eighty', stars: 80, forks: 80 }),
    ]

    expect(applyView(counts, { ...defaultView, sortKey: 'stars' }).map((r) => r.name)).toEqual([
      'hundred',
      'eighty',
      'nine',
    ])
    expect(applyView(counts, { ...defaultView, sortKey: 'forks' }).map((r) => r.name)).toEqual([
      'hundred',
      'eighty',
      'nine',
    ])
  })
})

describe('applyView filtering edge cases', () => {
  const repos = [
    repo({ name: 'java-lib', language: 'Java' }),
    repo({ name: 'ts-app', language: 'TypeScript' }),
    repo({ name: 'no-lang', language: null }),
    repo({ name: 'forked-java', language: 'Java', fork: true }),
    repo({ name: 'archived-java', language: 'Java', archived: true }),
    repo({ name: 'forked-archived', language: 'Java', fork: true, archived: true }),
  ]

  it('language "all" keeps every repository including ones without a language', () => {
    expect(applyView(repos, defaultView)).toHaveLength(repos.length)
  })

  it('a specific language filter excludes repositories with no language', () => {
    const names = applyView(repos, { ...defaultView, language: 'Java' }).map((r) => r.name)

    expect(names).not.toContain('no-lang')
    expect(names).toHaveLength(4)
  })

  it('is case sensitive on language, matching the GitHub values verbatim', () => {
    expect(applyView(repos, { ...defaultView, language: 'java' })).toEqual([])
  })

  it('returns an empty list for a language nobody uses', () => {
    expect(applyView(repos, { ...defaultView, language: 'COBOL' })).toEqual([])
  })

  it('hides repositories that are both forked and archived only once', () => {
    const names = applyView(repos, {
      ...defaultView,
      hideForks: true,
      hideArchived: true,
    }).map((r) => r.name)

    expect(names).toEqual(['java-lib', 'ts-app', 'no-lang'])
  })

  it('combines language filter with both toggles', () => {
    const names = applyView(repos, {
      sortKey: 'name',
      language: 'Java',
      hideForks: true,
      hideArchived: true,
    }).map((r) => r.name)

    expect(names).toEqual(['java-lib'])
  })
})

describe('languagesOf edge cases', () => {
  it('ignores empty-string languages', () => {
    expect(languagesOf([repo({ language: '' }), repo({ language: 'Go' })])).toEqual(['Go'])
  })

  it('deduplicates repeated languages', () => {
    expect(
      languagesOf([repo({ language: 'Go' }), repo({ language: 'Go' }), repo({ language: 'C' })]),
    ).toEqual(['C', 'Go'])
  })

  it('keeps distinct casings as distinct entries', () => {
    expect(languagesOf([repo({ language: 'Go' }), repo({ language: 'go' })])).toHaveLength(2)
  })

  it('handles languages with punctuation and digits', () => {
    expect(
      languagesOf([
        repo({ language: 'C++' }),
        repo({ language: 'C#' }),
        repo({ language: 'F*' }),
        repo({ language: 'Vim Script' }),
      ]),
    ).toHaveLength(4)
  })

  it('returns an empty list for no repositories', () => {
    expect(languagesOf([])).toEqual([])
  })

  it('does not mutate the input', () => {
    const input = [repo({ language: 'Go' }), repo({ language: 'C' })]
    const copy = input.map((r) => r.language)

    languagesOf(input)

    expect(input.map((r) => r.language)).toEqual(copy)
  })
})
