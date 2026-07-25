import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getRecentSearches, getUserSnapshot } from './client'
import type { RecentSearch, SnapshotResponse } from '../types'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

function stubFetch(impl: (url: string) => Promise<Response>) {
  const spy = vi.fn((url: string) => impl(url))
  vi.stubGlobal('fetch', spy)
  return spy
}

const snapshot: SnapshotResponse = {
  profile: {
    login: 'octocat',
    name: 'The Octocat',
    avatarUrl: 'https://a',
    bio: null,
    followers: 1,
    publicRepos: 0,
    htmlUrl: 'https://github.com/octocat',
  },
  repos: [],
  fetchedAt: '2026-07-24T12:00:00Z',
  fromCache: false,
  truncated: false,
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('getUserSnapshot', () => {
  it('calls the proxied endpoint and returns the parsed snapshot', async () => {
    const spy = stubFetch(() => Promise.resolve(jsonResponse(snapshot)))

    await expect(getUserSnapshot('octocat')).resolves.toEqual(snapshot)
    expect(spy).toHaveBeenCalledWith('/api/users/octocat')
  })

  it('omits the refresh parameter unless requested', async () => {
    const spy = stubFetch(() => Promise.resolve(jsonResponse(snapshot)))

    await getUserSnapshot('octocat', false)

    expect(spy).toHaveBeenCalledWith('/api/users/octocat')
  })

  it('appends refresh=true for a forced refresh', async () => {
    const spy = stubFetch(() => Promise.resolve(jsonResponse(snapshot)))

    await getUserSnapshot('octocat', true)

    expect(spy).toHaveBeenCalledWith('/api/users/octocat?refresh=true')
  })

  it('encodes unexpected characters in the username', async () => {
    const spy = stubFetch(() => Promise.resolve(jsonResponse(snapshot)))

    await getUserSnapshot('octo cat/../admin')

    expect(spy).toHaveBeenCalledWith('/api/users/octo%20cat%2F..%2Fadmin')
  })

  it('rejects with ApiError carrying the problem details on 404', async () => {
    stubFetch(() =>
      Promise.resolve(
        jsonResponse(
          {
            title: 'User not found',
            status: 404,
            detail: 'No GitHub user named "ghost".',
          },
          404,
        ),
      ),
    )

    const error = await getUserSnapshot('ghost').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.problem.title).toBe('User not found')
    expect(apiError.problem.status).toBe(404)
    expect(apiError.message).toBe('No GitHub user named "ghost".')
  })

  it('preserves retryAfterSeconds from a 429 problem', async () => {
    stubFetch(() =>
      Promise.resolve(
        jsonResponse(
          {
            title: 'GitHub rate limit reached',
            status: 429,
            detail: 'GitHub request limit was reached.',
            retryAfterSeconds: 45,
          },
          429,
        ),
      ),
    )

    const error = (await getUserSnapshot('octocat').catch((e: unknown) => e)) as ApiError

    expect(error.problem.retryAfterSeconds).toBe(45)
  })

  it('falls back to a friendly problem when an error body is not JSON', async () => {
    stubFetch(() =>
      Promise.resolve(
        new Response('<html>502 Bad Gateway</html>', {
          status: 502,
          headers: { 'content-type': 'text/html' },
        }),
      ),
    )

    const error = (await getUserSnapshot('octocat').catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.problem.title).toBe('Unexpected error')
    expect(error.problem.status).toBe(502)
    expect(error.problem.detail).not.toContain('html')
  })

  it('reports an unreachable backend when fetch itself fails', async () => {
    stubFetch(() => Promise.reject(new TypeError('fetch failed')))

    const error = (await getUserSnapshot('octocat').catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.problem.status).toBe(0)
    expect(error.problem.title).toBe('Backend unreachable')
    expect(error.problem.detail).toContain('8080')
  })

  it('rejects when a 200 response body is not JSON', async () => {
    stubFetch(() =>
      Promise.resolve(
        new Response('not json', { status: 200, headers: { 'content-type': 'text/plain' } }),
      ),
    )

    await expect(getUserSnapshot('octocat')).rejects.toThrow()
  })
})

describe('getRecentSearches', () => {
  it('returns the recent-search list', async () => {
    const recent: RecentSearch[] = [
      { username: 'octocat', searchedAt: '2026-07-24T12:00:00Z' },
      { username: 'torvalds', searchedAt: '2026-07-24T11:59:00Z' },
    ]
    const spy = stubFetch(() => Promise.resolve(jsonResponse(recent)))

    await expect(getRecentSearches()).resolves.toEqual(recent)
    expect(spy).toHaveBeenCalledWith('/api/searches/recent')
  })

  it('returns an empty list when no searches exist', async () => {
    stubFetch(() => Promise.resolve(jsonResponse([])))

    await expect(getRecentSearches()).resolves.toEqual([])
  })

  it('rejects with ApiError when the history endpoint fails', async () => {
    stubFetch(() =>
      Promise.resolve(
        jsonResponse({ title: 'Server error', status: 500, detail: 'boom' }, 500),
      ),
    )

    await expect(getRecentSearches()).rejects.toBeInstanceOf(ApiError)
  })
})
