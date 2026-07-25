import { useCallback, useEffect, useState } from 'react'
import { getActivity, getLanguages } from '../api/client'
import type { ActivityResponse, LanguagesResponse } from '../types'
import { ActivityChart } from './ActivityChart'
import { LanguageChart } from './LanguageChart'

interface Props {
  username: string
}

type Slot<T> = { state: 'loading' } | { state: 'error' } | { state: 'ready'; data: T }

export function Insights({ username }: Props) {
  const [languages, setLanguages] = useState<Slot<LanguagesResponse>>({ state: 'loading' })
  const [activity, setActivity] = useState<Slot<ActivityResponse>>({ state: 'loading' })
  const [generation, setGeneration] = useState(0)

  const retry = useCallback(() => setGeneration((g) => g + 1), [])

  useEffect(() => {
    let cancelled = false
    setLanguages({ state: 'loading' })
    setActivity({ state: 'loading' })
    getLanguages(username)
      .then((data) => !cancelled && setLanguages({ state: 'ready', data }))
      .catch(() => !cancelled && setLanguages({ state: 'error' }))
    getActivity(username)
      .then((data) => !cancelled && setActivity({ state: 'ready', data }))
      .catch(() => !cancelled && setActivity({ state: 'error' }))
    return () => {
      cancelled = true
    }
  }, [username, generation])

  return (
    <div className="insights">
      {languages.state === 'ready' ? (
        <LanguageChart languages={languages.data.languages} />
      ) : languages.state === 'error' ? (
        <div className="chart-card">
          <p className="notice small">
            Language chart unavailable.{' '}
            <button className="link" onClick={retry}>Try again</button>
          </p>
        </div>
      ) : (
        <div className="chart-card loading">Loading languages…</div>
      )}
      {activity.state === 'ready' ? (
        <ActivityChart
          weeks={activity.data.weeks}
          pending={activity.data.pending}
          onRetry={retry}
        />
      ) : activity.state === 'error' ? (
        <div className="chart-card">
          <p className="notice small">
            Activity chart unavailable.{' '}
            <button className="link" onClick={retry}>Try again</button>
          </p>
        </div>
      ) : (
        <div className="chart-card loading">Loading activity…</div>
      )}
    </div>
  )
}
