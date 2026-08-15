/**
 * The small amount of maths every chart in this product needs.
 *
 * There is no charting library in the dependency list on purpose: the whole
 * visual language is driven by design tokens, and every library worth using
 * wants to own colour, spacing and typography. Six SVG primitives against
 * these helpers is less code than the configuration required to make a
 * library stop styling things.
 */

export interface Scale {
  (value: number): number
  domain: [number, number]
  range: [number, number]
}

export function linearScale(domain: [number, number], range: [number, number]): Scale {
  const [d0, d1] = domain
  const [r0, r1] = range
  const span = d1 - d0

  const scale = ((value: number) => {
    if (span === 0) return (r0 + r1) / 2
    const t = (value - d0) / span
    return r0 + t * (r1 - r0)
  }) as Scale

  scale.domain = domain
  scale.range = range
  return scale
}

/**
 * Axis ticks on round numbers — 0 / 1,000 / 2,000 rather than whatever the
 * data's extent happens to divide into. An axis labelled 1,247 / 2,494 makes
 * the reader do arithmetic to compare two bars.
 */
export function niceTicks(min: number, max: number, count = 4): number[] {
  if (!Number.isFinite(min) || !Number.isFinite(max)) return []
  if (min === max) return [min]

  const span = max - min
  const rawStep = span / Math.max(1, count)
  const magnitude = 10 ** Math.floor(Math.log10(rawStep))
  const normalised = rawStep / magnitude

  // 1 / 2 / 5 / 10 are the steps people read fluently.
  const step = (normalised >= 7.5 ? 10 : normalised >= 3 ? 5 : normalised >= 1.5 ? 2 : 1) * magnitude

  const start = Math.ceil(min / step) * step
  const ticks: number[] = []
  for (let v = start; v <= max + step * 0.001; v += step) {
    // Re-round each step: repeated addition of a fractional step accumulates
    // float error and produces ticks like 0.30000000000000004.
    ticks.push(Number(v.toFixed(10)))
  }
  return ticks
}

/**
 * A domain that ends on a round number and always includes zero for
 * magnitude data. A bar chart whose axis starts at 900 exaggerates a 3%
 * difference into a visual doubling.
 */
export function niceDomain(values: number[], { includeZero = true } = {}): [number, number] {
  const finite = values.filter((v) => Number.isFinite(v))
  if (finite.length === 0) return [0, 1]

  let min = Math.min(...finite)
  let max = Math.max(...finite)

  if (includeZero) {
    min = Math.min(0, min)
    max = Math.max(0, max)
  }
  if (min === max) {
    if (max === 0) return [0, 1]
    max = max > 0 ? max * 1.1 : 0
    min = min < 0 ? min * 1.1 : 0
  }

  const ticks = niceTicks(min, max, 4)
  const step = ticks.length > 1 ? ticks[1] - ticks[0] : (max - min) / 4
  return [Math.floor(min / step) * step, Math.ceil(max / step) * step]
}

/** An SVG path through points, skipping gaps where a value is missing. */
export function linePath(points: { x: number; y: number | null }[]): string {
  let path = ''
  let pendingMove = true

  for (const point of points) {
    if (point.y === null) {
      pendingMove = true
      continue
    }
    path += `${pendingMove ? 'M' : 'L'}${point.x.toFixed(2)} ${point.y.toFixed(2)} `
    pendingMove = false
  }
  return path.trim()
}

/**
 * Reduces a long series to at most `max` points by averaging within buckets.
 *
 * A 30-day window at 15-second probes is ~170k samples; drawing them into an
 * 800px-wide chart puts hundreds of points on every pixel. Averaging is
 * honest for latency; availability is downsampled by the backend instead,
 * where a single failure inside a bucket must survive rather than average
 * away.
 */
export function downsample<T>(items: T[], max: number, value: (item: T) => number | null): { item: T; value: number | null }[] {
  if (items.length <= max) return items.map((item) => ({ item, value: value(item) }))

  const bucketSize = items.length / max
  const out: { item: T; value: number | null }[] = []

  for (let i = 0; i < max; i++) {
    const slice = items.slice(Math.floor(i * bucketSize), Math.floor((i + 1) * bucketSize))
    if (slice.length === 0) continue
    const numbers = slice.map(value).filter((v): v is number => v !== null)
    out.push({
      item: slice[Math.floor(slice.length / 2)],
      value: numbers.length ? numbers.reduce((a, b) => a + b, 0) / numbers.length : null,
    })
  }
  return out
}

/** The fixed categorical order. Assign in sequence — never cherry-pick. */
export const SERIES_TOKENS = [
  'var(--chart-1)',
  'var(--chart-2)',
  'var(--chart-3)',
  'var(--chart-4)',
  'var(--chart-5)',
  'var(--chart-6)',
] as const

export function seriesColor(index: number): string {
  return SERIES_TOKENS[index % SERIES_TOKENS.length]
}
