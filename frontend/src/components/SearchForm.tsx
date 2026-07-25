import { useState, type FormEvent } from 'react'

const USERNAME_REGEX = /^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$/

interface Props {
  onSearch: (username: string) => void
  busy: boolean
}

export function SearchForm({ onSearch, busy }: Props) {
  const [value, setValue] = useState('')
  const [hint, setHint] = useState<string | null>(null)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const username = value.trim()
    if (!username) {
      setHint('Type a GitHub username to search.')
      return
    }
    if (!USERNAME_REGEX.test(username)) {
      setHint('Usernames use letters, numbers and single hyphens (max 39 characters).')
      return
    }
    setHint(null)
    onSearch(username)
  }

  return (
    <form className="search-form" onSubmit={handleSubmit}>
      <input
        type="text"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="GitHub username, e.g. octocat"
        aria-label="GitHub username"
        autoFocus
      />
      <button type="submit" disabled={busy}>
        {busy ? 'Searching…' : 'Search'}
      </button>
      {hint && <p className="hint">{hint}</p>}
    </form>
  )
}
