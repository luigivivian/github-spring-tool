import { useEffect, useState } from 'react'

// Validated categorical palette (dataviz six-checks, light & dark surfaces).
// Fixed slot order — never cycled; >6 slices are folded into "Other" upstream.
export const CHART_COLORS_LIGHT = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300']
export const CHART_COLORS_DARK = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300']

export const SURFACE_LIGHT = '#fcfcfb'
export const SURFACE_DARK = '#1a1a19'

export const MAX_SLICES = 6

export function usePrefersDark(): boolean {
  const [dark, setDark] = useState(
    () => window.matchMedia('(prefers-color-scheme: dark)').matches,
  )
  useEffect(() => {
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const listener = (event: MediaQueryListEvent) => setDark(event.matches)
    query.addEventListener('change', listener)
    return () => query.removeEventListener('change', listener)
  }, [])
  return dark
}

export function useChartTheme() {
  const dark = usePrefersDark()
  return {
    colors: dark ? CHART_COLORS_DARK : CHART_COLORS_LIGHT,
    surface: dark ? SURFACE_DARK : SURFACE_LIGHT,
    grid: dark ? '#30363d' : '#e6e4e0',
  }
}
