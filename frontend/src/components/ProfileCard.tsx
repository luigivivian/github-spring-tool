import type { Profile, Repo } from '../types'

interface Props {
  profile: Profile
  repos: Repo[]
  fetchedAt: string
  fromCache: boolean
  onRefresh: () => void
  busy: boolean
}

function minutesAgo(iso: string): string {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  if (minutes === 0) return 'just now'
  if (minutes === 1) return '1 minute ago'
  return `${minutes} minutes ago`
}

export function ProfileCard({ profile, repos, fetchedAt, fromCache, onRefresh, busy }: Props) {
  const totalStars = repos.reduce((sum, repo) => sum + repo.stars, 0)

  return (
    <section className="profile-card">
      <img className="avatar" src={profile.avatarUrl} alt={`${profile.login} avatar`} />
      <div className="profile-info">
        <h2>
          <a href={profile.htmlUrl} target="_blank" rel="noreferrer">
            {profile.name ?? profile.login}
          </a>
          <span className="login">@{profile.login}</span>
        </h2>
        {profile.bio && <p className="bio">{profile.bio}</p>}
        <ul className="profile-stats">
          <li><strong>{profile.followers.toLocaleString()}</strong> followers</li>
          <li><strong>{profile.publicRepos.toLocaleString()}</strong> public repos</li>
          <li><strong>{totalStars.toLocaleString()}</strong> total stars</li>
        </ul>
        <p className="fetch-meta">
          Fetched {minutesAgo(fetchedAt)}
          {fromCache && <span className="badge badge-cache">cached</span>}
          <button className="refresh" onClick={onRefresh} disabled={busy}>
            Refresh
          </button>
        </p>
      </div>
    </section>
  )
}
