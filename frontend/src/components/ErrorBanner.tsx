import type { ApiProblem } from '../types'

interface Props {
  problem: ApiProblem
}

export function ErrorBanner({ problem }: Props) {
  return (
    <div className="error-banner" role="alert">
      <strong>{problem.title}</strong>
      <p>{problem.detail}</p>
    </div>
  )
}
