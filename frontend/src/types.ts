export interface Profile {
  login: string
  name: string | null
  avatarUrl: string
  bio: string | null
  followers: number
  publicRepos: number
  htmlUrl: string
}

export interface Repo {
  name: string
  description: string | null
  language: string | null
  stars: number
  forks: number
  updatedAt: string
  fork: boolean
  archived: boolean
  htmlUrl: string
  homepage: string | null
  cloneUrl: string
}

export interface SnapshotResponse {
  profile: Profile
  repos: Repo[]
  fetchedAt: string
  fromCache: boolean
  truncated: boolean
}

export interface RecentSearch {
  username: string
  searchedAt: string
}

export interface ApiProblem {
  title: string
  status: number
  detail: string
  retryAfterSeconds?: number | null
}

export interface LanguageShare {
  language: string
  percent: number
  bytes: number
}

export interface LanguagesResponse {
  languages: LanguageShare[]
  fetchedAt: string
  fromCache: boolean
}

export interface ActivityResponse {
  weeks: number[]
  pending: boolean
  fetchedAt: string
  fromCache: boolean
}

export interface ReleaseInfo {
  name: string
  publishedAt: string | null
  htmlUrl: string
}

export interface ContributorInfo {
  login: string
  avatarUrl: string
  contributions: number
  htmlUrl: string
}

export interface RepoDetailResponse {
  readme: string | null
  releases: ReleaseInfo[]
  contributors: ContributorInfo[]
  languages: LanguageShare[]
  fetchedAt: string
  fromCache: boolean
}

export interface Me {
  authenticated: boolean
  login: string | null
  name: string | null
  avatarUrl: string | null
}
