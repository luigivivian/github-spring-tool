import type { SortKey, ViewOptions } from '../lib/repoView'

interface Props {
  view: ViewOptions
  languages: string[]
  visible: number
  total: number
  onChange: (view: ViewOptions) => void
}

export function RepoFilters({ view, languages, visible, total, onChange }: Props) {
  return (
    <div className="repo-filters">
      <label>
        Sort
        <select
          value={view.sortKey}
          onChange={(e) => onChange({ ...view, sortKey: e.target.value as SortKey })}
        >
          <option value="updated">Last updated</option>
          <option value="stars">Stars</option>
          <option value="forks">Forks</option>
          <option value="name">Name</option>
        </select>
      </label>
      <label>
        Language
        <select
          value={view.language}
          onChange={(e) => onChange({ ...view, language: e.target.value })}
        >
          <option value="all">All</option>
          {languages.map((language) => (
            <option key={language} value={language}>
              {language}
            </option>
          ))}
        </select>
      </label>
      <label className="toggle">
        <input
          type="checkbox"
          checked={view.hideForks}
          onChange={(e) => onChange({ ...view, hideForks: e.target.checked })}
        />
        Hide forks
      </label>
      <label className="toggle">
        <input
          type="checkbox"
          checked={view.hideArchived}
          onChange={(e) => onChange({ ...view, hideArchived: e.target.checked })}
        />
        Hide archived
      </label>
      <span className="count">
        {visible} of {total} repositories
      </span>
    </div>
  )
}
