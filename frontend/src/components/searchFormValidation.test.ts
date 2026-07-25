import { describe, expect, it } from 'vitest'
import searchFormSource from './SearchForm.tsx?raw'

/**
 * FR-001 requires the SPA to validate the username *before* any lookup, using GitHub's rules
 * (letters, digits, single interior hyphens, max 39 characters). The rule is duplicated in two
 * places, so this suite checks the SPA copy against the API contract copy:
 *
 *   - SPA:      USERNAME_REGEX in src/components/SearchForm.tsx
 *   - contract: specs/001-github-repo-browser/contracts/api.yaml  (= SearchController.USERNAME_REGEX)
 *
 * SearchForm does not export its regex (it is component-private), so it is read from source
 * rather than imported - importing the .tsx would drag React rendering into a node-environment
 * suite with no DOM.
 */
const CONTRACT_PATTERN = /^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$/

function spaUsernameRegex(): RegExp {
  const match = searchFormSource.match(/const USERNAME_REGEX = \/(.*)\/\s*$/m)
  if (!match) {
    throw new Error('USERNAME_REGEX literal not found in SearchForm.tsx')
  }
  return new RegExp(match[1])
}

const hyphenated = (alnums: number) => Array.from({ length: alnums }, () => 'a').join('-')

const samples: string[] = [
  'a',
  '9',
  'octocat',
  'a-b',
  'A1-b2-C3',
  'a123456789b123456789c123456789d12345678', // 39 chars - allowed
  'a123456789b123456789c123456789d123456789', // 40 chars - too long
  '-octocat',
  'octocat-',
  'octo--cat',
  '-',
  'octo_cat',
  'octo.cat',
  'octo cat',
  '',
  hyphenated(20), // 39 chars: a-a-...-a
  hyphenated(21), // 41 chars: over the limit but hyphen-separated
  hyphenated(39), // 77 chars: 39 alphanumerics joined by 38 hyphens
]

describe('SearchForm username validation', () => {
  const spaPattern = spaUsernameRegex()

  it('never accepts an input longer than 39 characters', () => {
    const wronglyAccepted = samples.filter((s) => s.length > 39 && spaPattern.test(s))

    expect(wronglyAccepted).toEqual([])
  })

  it('agrees with the API contract pattern for every sample', () => {
    const disagreements = samples
      .filter((s) => spaPattern.test(s) !== CONTRACT_PATTERN.test(s))
      .map((s) => ({ input: s.length > 45 ? `${s.slice(0, 20)}... (${s.length} chars)` : s, spa: spaPattern.test(s), contract: CONTRACT_PATTERN.test(s) }))

    expect(disagreements).toEqual([])
  })

  it('accepts the plain valid usernames', () => {
    for (const valid of ['a', '9', 'octocat', 'a-b', 'A1-b2-C3', hyphenated(20)]) {
      expect(spaPattern.test(valid), valid).toBe(true)
    }
  })

  it('rejects empty, hyphen-edged, double-hyphen and illegal-character inputs', () => {
    for (const invalid of ['', '-', '-octocat', 'octocat-', 'octo--cat', 'octo_cat', 'octo.cat', 'octo cat']) {
      expect(spaPattern.test(invalid), invalid).toBe(false)
    }
  })
})
