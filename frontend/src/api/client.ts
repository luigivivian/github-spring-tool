import type {
  ActivityResponse,
  ApiProblem,
  LanguagesResponse,
  Me,
  RecentSearch,
  RepoDetailResponse,
  SnapshotResponse,
} from '../types'

export class ApiError extends Error {
  readonly problem: ApiProblem

  constructor(problem: ApiProblem) {
    super(problem.detail)
    this.problem = problem
  }
}

async function request<T>(url: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(url)
  } catch {
    throw new ApiError({
      title: 'Backend unreachable',
      status: 0,
      detail: 'Could not reach the API. Is the backend running on port 8080?',
    })
  }
  if (!response.ok) {
    let problem: ApiProblem
    try {
      problem = (await response.json()) as ApiProblem
    } catch {
      problem = {
        title: 'Unexpected error',
        status: response.status,
        detail: 'Something went wrong. Please try again.',
      }
    }
    throw new ApiError(problem)
  }
  return (await response.json()) as T
}

export function getUserSnapshot(username: string, refresh = false): Promise<SnapshotResponse> {
  const params = refresh ? '?refresh=true' : ''
  return request<SnapshotResponse>(`/api/users/${encodeURIComponent(username)}${params}`)
}

export function getRecentSearches(): Promise<RecentSearch[]> {
  return request<RecentSearch[]>('/api/searches/recent')
}

export function getLanguages(username: string): Promise<LanguagesResponse> {
  return request<LanguagesResponse>(`/api/users/${encodeURIComponent(username)}/languages`)
}

export function getActivity(username: string): Promise<ActivityResponse> {
  return request<ActivityResponse>(`/api/users/${encodeURIComponent(username)}/activity`)
}

export function getRepoDetail(owner: string, repo: string): Promise<RepoDetailResponse> {
  return request<RepoDetailResponse>(
    `/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`,
  )
}

export function getMe(): Promise<Me> {
  return request<Me>('/api/me')
}

export async function logout(): Promise<void> {
  await fetch('/logout', { method: 'POST' })
  window.location.reload()
}
