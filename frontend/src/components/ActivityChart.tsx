import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useChartTheme } from '../lib/chartTheme'

interface Props {
  weeks: number[]
  pending: boolean
  onRetry: () => void
}

export function ActivityChart({ weeks, pending, onRetry }: Props) {
  const theme = useChartTheme()
  const data = weeks.map((total, index) => ({
    label: index === weeks.length - 1 ? 'now' : `${weeks.length - 1 - index}w ago`,
    total,
  }))
  const empty = !pending && weeks.every((total) => total === 0)

  return (
    <div className="chart-card">
      <h3>
        Commit activity <span className="chart-subtitle">last 52 weeks, top repositories</span>
      </h3>
      {pending && (
        <p className="notice small">
          GitHub is still computing these statistics.{' '}
          <button className="link" onClick={onRetry}>Try again</button>
        </p>
      )}
      {empty ? (
        <p className="empty-state small">No commits in the last year.</p>
      ) : (
        <ResponsiveContainer width="100%" height={160}>
          <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
            <CartesianGrid vertical={false} stroke={theme.grid} strokeDasharray="2 4" />
            <XAxis
              dataKey="label"
              tickLine={false}
              axisLine={false}
              interval={12}
              fontSize={11}
            />
            <YAxis tickLine={false} axisLine={false} fontSize={11} allowDecimals={false} />
            <Tooltip
              formatter={(value) => [`${value} commits`, undefined]}
              contentStyle={{ background: theme.surface, borderRadius: 6 }}
              cursor={{ fill: theme.grid, opacity: 0.4 }}
            />
            <Bar
              dataKey="total"
              fill={theme.colors[0]}
              radius={[2, 2, 0, 0]}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
