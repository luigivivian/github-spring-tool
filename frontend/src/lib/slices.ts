import type { LanguageShare } from '../types'
import { MAX_SLICES } from './chartTheme'

/** Folds shares beyond the palette size into a single "Other" slice (never cycle hues). */
export function foldToPalette(shares: LanguageShare[]): LanguageShare[] {
  if (shares.length <= MAX_SLICES) {
    return shares
  }
  const kept = shares.slice(0, MAX_SLICES - 1)
  const rest = shares.slice(MAX_SLICES - 1)
  const percent = Math.round(rest.reduce((sum, s) => sum + s.percent, 0) * 10) / 10
  const bytes = rest.reduce((sum, s) => sum + s.bytes, 0)
  const existingOther = kept.find((s) => s.language === 'Other')
  if (existingOther) {
    return kept.map((s) =>
      s.language === 'Other'
        ? {
            ...s,
            percent: Math.round((s.percent + percent) * 10) / 10,
            bytes: s.bytes + bytes,
          }
        : s,
    )
  }
  return [...kept, { language: 'Other', percent, bytes }]
}
