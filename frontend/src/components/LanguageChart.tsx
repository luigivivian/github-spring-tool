import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import type { LanguageShare } from '../types'
import { useChartTheme } from '../lib/chartTheme'
import { foldToPalette } from '../lib/slices'

interface Props {
  languages: LanguageShare[]
}

export function LanguageChart({ languages }: Props) {
  const theme = useChartTheme()
  const slices = foldToPalette(languages)

  if (slices.length === 0) {
    return <p className="empty-state small">No language data for this user's repositories.</p>
  }

  return (
    <div className="chart-card">
      <h3>Languages <span className="chart-subtitle">top repositories, % of code</span></h3>
      <div className="language-chart-body">
        <ResponsiveContainer width={180} height={180}>
          <PieChart>
            <Pie
              data={slices}
              dataKey="percent"
              nameKey="language"
              innerRadius={50}
              outerRadius={82}
              stroke={theme.surface}
              strokeWidth={2}
              isAnimationActive={false}
            >
              {slices.map((slice, index) => (
                <Cell key={slice.language} fill={theme.colors[index]} />
              ))}
            </Pie>
            <Tooltip
              formatter={(value) => [`${value}%`, undefined]}
              contentStyle={{ background: theme.surface, borderRadius: 6 }}
            />
          </PieChart>
        </ResponsiveContainer>
        <ul className="chart-legend">
          {slices.map((slice, index) => (
            <li key={slice.language}>
              <span className="swatch" style={{ background: theme.colors[index] }} />
              <span className="legend-label">{slice.language}</span>
              <span className="legend-value">{slice.percent}%</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
