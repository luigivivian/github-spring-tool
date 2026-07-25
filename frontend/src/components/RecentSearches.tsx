import type { RecentSearch } from '../types'

interface Props {
  searches: RecentSearch[]
  onSelect: (username: string) => void
}

export function RecentSearches({ searches, onSelect }: Props) {
  if (searches.length === 0) {
    return null
  }
  return (
    <div className="recent-searches">
      <span className="recent-label">Recent:</span>
      {searches.map((search) => (
        <button
          key={search.username}
          className="recent-chip"
          onClick={() => onSelect(search.username)}
        >
          {search.username}
        </button>
      ))}
    </div>
  )
}
