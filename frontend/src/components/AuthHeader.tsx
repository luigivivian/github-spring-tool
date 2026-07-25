import { logout } from '../api/client'
import type { Me } from '../types'

interface Props {
  me: Me | null
}

export function AuthHeader({ me }: Props) {
  if (!me || !me.authenticated) {
    return (
      <a className="auth-link" href="/oauth2/authorization/github">
        Sign in with GitHub
      </a>
    )
  }

  return (
    <div className="auth-user">
      {me.avatarUrl && <img src={me.avatarUrl} alt="" width={24} height={24} />}
      <span>{me.name ?? me.login}</span>
      <button className="link" onClick={() => void logout()}>
        Sign out
      </button>
    </div>
  )
}
