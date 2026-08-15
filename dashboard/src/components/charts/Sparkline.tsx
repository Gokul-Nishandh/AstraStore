import { useId } from 'react'
import { linePath, linearScale } from './scale'
import { cn } from '../../lib/cn'

export interface SparklineProps {
  values: (number | null)[]
  /** Series slot, 1-indexed to match the `--chart-N` tokens. */
  series?: number
  width?: number
  height?: number
  /** Washes the area under the line at 10% — for tiles that carry no axis. */
  area?: boolean
  /** Marks the final point, which is the one a reader looks for. */
  showEndDot?: boolean
  className?: string
  'aria-label': string
}

/**
 * A trend, not a chart: no axes, no ticks, no tooltip.
 *
 * It answers "which way is this going" beside a number that answers "how
 * much". Anything a reader needs an exact value from belongs in a real
 * chart — this one deliberately cannot be read precisely.
 */
export function Sparkline({
  values,
  series = 1,
  width = 120,
  height = 32,
  area = false,
  showEndDot = true,
  className,
  ...props
}: SparklineProps) {
  const gradientId = useId()
  const color = `var(--chart-${series})`

  const finite = values.filter((v): v is number => v !== null && Number.isFinite(v))
  if (finite.length < 2) {
    return (
      <div
        role="img"
        aria-label={`${props['aria-label']} — not enough data`}
        className={cn('flex items-center', className)}
        style={{ width, height }}
      >
        <span className="h-px w-full bg-line" />
      </div>
    )
  }

  const min = Math.min(...finite)
  const max = Math.max(...finite)
  // A flat series would collapse to zero height and vanish; pad it so it
  // draws as a level line through the middle instead.
  const pad = min === max ? Math.abs(min || 1) * 0.5 : (max - min) * 0.15

  const x = linearScale([0, values.length - 1], [1.5, width - 1.5])
  const y = linearScale([min - pad, max + pad], [height - 2.5, 2.5])

  const points = values.map((value, index) => ({
    x: x(index),
    y: value === null ? null : y(value),
  }))

  const path = linePath(points)
  const lastIndex = values.findLastIndex((v) => v !== null && Number.isFinite(v))
  const last = lastIndex >= 0 ? { x: x(lastIndex), y: y(values[lastIndex] as number) } : null

  return (
    <svg
      role="img"
      aria-label={props['aria-label']}
      viewBox={`0 0 ${width} ${height}`}
      width={width}
      height={height}
      preserveAspectRatio="none"
      className={cn('overflow-visible', className)}
    >
      {area && (
        <>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity="0.18" />
              <stop offset="100%" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path
            d={`${path} L${x(values.length - 1).toFixed(2)} ${height} L${x(0).toFixed(2)} ${height} Z`}
            fill={`url(#${gradientId})`}
          />
        </>
      )}

      <path d={path} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />

      {showEndDot && last && (
        // The 2px surface ring keeps the dot legible where it sits on top of
        // the line or against a busy tile background.
        <circle cx={last.x} cy={last.y} r="4" fill={color} stroke="var(--surface)" strokeWidth="2" />
      )}
    </svg>
  )
}
