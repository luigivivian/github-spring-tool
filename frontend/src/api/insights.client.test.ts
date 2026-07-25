import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getActivity, getLanguages, getRepoDetail } from './client'
import type { ActivityResponse, LanguagesResponse, RepoDetailResponse } from '../types'

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

const languages: LanguagesResponse = {
  languages: [
    { language: 'Java', percent: 90, bytes: 900 },
    { language: 'Other', percent: 10, bytes: 100 },
  ],
  fetchedAt: '2026-07-25T12:00:00Z',
  fromCache: false,
}

const activity: ActivityResponse = {
  weeks: Array.from({ length: 52 }, () => 0),
  pending: true,
  fetchedAt: '2026-07-25T12:00:00Z',
  fromCache: false,
}

const detail: RepoDetailResponse = {
  readme: '# Hello',
  releases: [{ name: 'v1.0', publishedAt: '2026-07-01T00:00:00Z', htmlUrl: 'https://r' }],
  contributors: [
    { login: 'alice', avatarUrl: 'https://a', contributions: 42, htmlUrl: 'https://gh/alice' },
  ],
  languages: [{ language: 'Java', percent: 100, bytes: 1000 }],
  fetchedAt: '2026-07-25T12:00:00Z',
  fromCache: true,
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('getLanguages', () => {
  it('calls the languages endpoint and returns the parsed shares', async () => {
    const spy = stubFetch(async () => jsonResponse(languages))

    await expect(getLanguages('octocat')).resolves.toEqual(languages)
    expect(spy).toHaveBeenCalledWith('/api/users/octocat/languages')
  })

  it('encodes unexpected characters in the username', async () => {
    const spy = stubFetch(async () => jsonResponse(languages))

    await getLanguages('oct/cat?x=1')

    expect(spy).toHaveBeenCalledWith('/api/users/oct%2Fcat%3Fx%3D1/languages')
  })

  it('rejects with ApiError carrying retryAfterSeconds on 429', async () => {
    stubFetch(async () =>
      jsonResponse(
        {
          title: 'GitHub rate limit reached',
          status: 429,
          detail: 'Try again in about 2 minutes.',
          retryAfterSeconds: 92,
        },
        429,
      ),
    )

    await expect(getLanguages('octocat')).rejects.toMatchObject({
      problem: { status: 429, retryAfterSeconds: 92 },
    })
  })

  it('rejects with ApiError on a 502 upstream problem', async () => {
    stubFetch(async () =>
      jsonResponse(
        { title: 'GitHub unavailable', status: 502, detail: 'GitHub could not be reached.' },
        502,
      ),
    )

    const error = await getLanguages('octocat').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem.title).toBe('GitHub unavailable')
  })
})

describe('getActivity', () => {
  it('calls the activity endpoint and preserves the pending flag', async () => {
    const spy = stubFetch(async () => jsonResponse(activity))

    const result = await getActivity('octocat')

    expect(spy).toHaveBeenCalledWith('/api/users/octocat/activity')
    expect(result.pending).toBe(true)
    expect(result.weeks).toHaveLength(52)
  })

  it('falls back to a friendly problem when the error body is not JSON', async () => {
    stubFetch(
      async () => new Response('<html>gateway</html>', { status: 502, headers: { 'content-type': 'text/html' } }),
    )

    const error = await getActivity('octocat').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem).toEqual({
      title: 'Unexpected error',
      status: 502,
      detail: 'Something went wrong. Please try again.',
    })
  })

  it('reports an unreachable backend when fetch itself fails', async () => {
    stubFetch(async () => {
      throw new TypeError('Failed to fetch')
    })

    const error = await getActivity('octocat').catch((e: unknown) => e)

    expect((error as ApiError).problem.status).toBe(0)
    expect((error as ApiError).problem.title).toBe('Backend unreachable')
  })
})

describe('getRepoDetail', () => {
  it('calls the repo endpoint with owner and repo', async () => {
    const spy = stubFetch(async () => jsonResponse(detail))

    await expect(getRepoDetail('octocat', 'hello-world')).resolves.toEqual(detail)
    expect(spy).toHaveBeenCalledWith('/api/repos/octocat/hello-world')
  })

  it('encodes both path segments', async () => {
    const spy = stubFetch(async () => jsonResponse(detail))

    await getRepoDetail('octo cat', 'hello/world')

    expect(spy).toHaveBeenCalledWith('/api/repos/octo%20cat/hello%2Fworld')
  })

  it('surfaces a 404 problem for an unknown repo', async () => {
    stubFetch(async () =>
      jsonResponse({ title: 'Not found', status: 404, detail: '"octocat/gone" wasn\'t found.' }, 404),
    )

    const error = await getRepoDetail('octocat', 'gone').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem.status).toBe(404)
  })

  it('rejects when a 200 body is not JSON', async () => {
    stubFetch(async () => new Response('not json', { status: 200 }))

    await expect(getRepoDetail('octocat', 'hello')).rejects.toBeInstanceOf(SyntaxError)
  })

  it('accepts a null readme', async () => {
    stubFetch(async () => jsonResponse({ ...detail, readme: null }))

    await expect(getRepoDetail('octocat', 'hello')).resolves.toMatchObject({ readme: null })
  })
})
