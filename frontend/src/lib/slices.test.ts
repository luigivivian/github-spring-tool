import { describe, expect, it } from 'vitest'
import { foldToPalette } from './slices'
import { MAX_SLICES } from './chartTheme'
import type { LanguageShare } from '../types'

function share(language: string, percent: number, bytes = Math.round(percent * 100)): LanguageShare {
  return { language, percent, bytes }
}

function shares(count: number, percent = 5): LanguageShare[] {
  return Array.from({ length: count }, (_, i) => share(`lang-${i}`, percent, (i + 1) * 10))
}

describe('foldToPalette', () => {
  it('returns an empty list untouched', () => {
    expect(foldToPalette([])).toEqual([])
  })

  it('leaves a single slice untouched', () => {
    const input = [share('Java', 100)]
    expect(foldToPalette(input)).toBe(input)
  })

  it('leaves exactly MAX_SLICES untouched', () => {
    const input = shares(MAX_SLICES)
    const result = foldToPalette(input)
    expect(result).toBe(input)
    expect(result).toHaveLength(6)
  })

  it('never returns more slices than the palette has colours', () => {
    for (const count of [7, 8, 12, 40]) {
      expect(foldToPalette(shares(count))).toHaveLength(MAX_SLICES)
    }
  })

  it('folds the tail of a 7-slice list into a single Other slice', () => {
    const input = [
      share('Java', 40, 4000),
      share('TypeScript', 20, 2000),
      share('Python', 15, 1500),
      share('Go', 10, 1000),
      share('Rust', 8, 800),
      share('Shell', 4, 400),
      share('Ruby', 3, 300),
    ]

    const result = foldToPalette(input)

    expect(result.map((s) => s.language)).toEqual([
      'Java',
      'TypeScript',
      'Python',
      'Go',
      'Rust',
      'Other',
    ])
    expect(result[5]).toEqual({ language: 'Other', percent: 7, bytes: 700 })
  })

  it('keeps the leading slices in their original order', () => {
    const input = shares(10)
    const result = foldToPalette(input)
    expect(result.slice(0, 5)).toEqual(input.slice(0, 5))
  })

  it('rounds the folded percentage to one decimal', () => {
    const input = [
      share('Java', 40),
      share('TypeScript', 20),
      share('Python', 15),
      share('Go', 10),
      share('Rust', 8),
      share('Shell', 3.33),
      share('Ruby', 2.22),
      share('Perl', 1.11),
    ]

    const other = foldToPalette(input)[5]

    expect(other.language).toBe('Other')
    expect(other.percent).toBe(6.7)
  })

  it('preserves the total percentage within rounding tolerance', () => {
    const input = [
      share('Java', 30.5),
      share('TypeScript', 20.4),
      share('Python', 15.3),
      share('Go', 12.2),
      share('Rust', 10.1),
      share('Shell', 6.4),
      share('Ruby', 5.1),
    ]

    const total = foldToPalette(input).reduce((sum, s) => sum + s.percent, 0)

    expect(total).toBeCloseTo(100, 1)
  })

  it('sums the folded byte counts', () => {
    const input = shares(9, 5)
    const other = foldToPalette(input)[5]
    // slices 5..8 carry 60 + 70 + 80 + 90 bytes
    expect(other.bytes).toBe(300)
  })

  it('does not mutate the input array', () => {
    const input = shares(9)
    const snapshot = structuredClone(input)
    foldToPalette(input)
    expect(input).toEqual(snapshot)
  })

  it('does not create a duplicate slice when an Other slice is already kept', () => {
    const input = [
      share('Java', 40, 4000),
      share('Other', 25, 2500),
      share('TypeScript', 15, 1500),
      share('Python', 10, 1000),
      share('Go', 5, 500),
      share('Rust', 3, 300),
      share('Shell', 2, 200),
    ]

    const result = foldToPalette(input)

    expect(result.filter((s) => s.language === 'Other')).toHaveLength(1)
  })

  it('keeps the tail accounted for when an Other slice is already kept', () => {
    const input = [
      share('Java', 40, 4000),
      share('Other', 25, 2500),
      share('TypeScript', 15, 1500),
      share('Python', 10, 1000),
      share('Go', 5, 500),
      share('Rust', 3, 300),
      share('Shell', 2, 200),
    ]

    const result = foldToPalette(input)
    const total = result.reduce((sum, s) => sum + s.percent, 0)
    const bytes = result.reduce((sum, s) => sum + s.bytes, 0)

    expect(total).toBeCloseTo(100, 1)
    expect(bytes).toBe(10000)
  })
})
