import { useId, useState } from 'react'
import { cn } from '../../lib/cn'
import { seriesColor } from './scale'

export interface DonutSlice {
  id: string
  label: string
  value: number
}

export interface DonutProps {
  slices: DonutSlice[]
  /** Rendered in the hole — the one figure the chart is really about. */
  centerValue: string
  centerLabel: string
  formatValue: (value: number) => string
  size?: number
  className?: string
  'aria-label': string
}

const THICKNESS = 18
const GAP_DEGREES = 2

/**
 * Proportions of a whole, with the total in the middle.
 *
 * A donut is the right form here and a bar chart is not: the question is
 * "what share of my storage is images", and the hole gives the total a home
 * so the reader does not have to add the segments up themselves.
 *
 * Segments are separated by a real gap in the surface colour rather than a
 * stroke — a stroke around each arc adds ink that is not data and makes thin
 * slices read as thicker than they are.
 */
export function Donut({
  slices,
  centerValue,
  centerLabel,
  formatValue,
  size = 168,
  className,
  ...props
}: DonutProps) {
  const titleId = useId()
  const [active, setActive] = useState<string | null>(null)

  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  const radius = (size - THICKNESS) / 2
  const circumference = 2 * Math.PI * radius

  if (total <= 0) {
    return (
      <div
        role="img"
        aria-label={`${props['aria-label']} — nothing stored yet`}
        className={cn('grid place-items-center', className)}
        style={{ width: size, height: size }}
      >
        <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size}>
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke="var(--surface-3)"
            strokeWidth={THICKNESS}
          />
        </svg>
      </div>
    )
  }

  let offset = 0

  return (
    <div className={cn('relative shrink-0', className)} style={{ width: size, height: size }}>
      <svg
        role="img"
        aria-labelledby={titleId}
        viewBox={`0 0 ${size} ${size}`}
        width={size}
        height={size}
        // -90deg puts the first segment at twelve o'clock, where a reader
        // starts, rather than at three.
        style={{ transform: 'rotate(-90deg)' }}
      >
        <title id={titleId}>{props['aria-label']}</title>

        {slices.map((slice, index) => {
          const fraction = slice.value / total
          const arc = Math.max(0, fraction * circumference - (GAP_DEGREES / 360) * circumference)
          const dash = `${arc} ${circumference - arc}`
          const rotation = offset
          offset += fraction * 360

          const dimmed = active !== null && active !== slice.id

          return (
            <circle
              key={slice.id}
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke={seriesColor(index)}
              strokeWidth={THICKNESS}
              strokeDasharray={dash}
              strokeDashoffset={0}
              style={{
                transform: `rotate(${rotation}deg)`,
                transformOrigin: 'center',
                opacity: dimmed ? 0.28 : 1,
                transition: 'opacity 150ms var(--ease-out)',
              }}
              onPointerEnter={() => setActive(slice.id)}
              onPointerLeave={() => setActive(null)}
            />
          )
        })}
      </svg>

      {/* Text never wears a series colour — identity comes from the arc. */}
      <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
        {active ? (
          <div>
            <p className="tnum font-sans text-[15px] font-semibold text-ink">
              {formatValue(slices.find((s) => s.id === active)?.value ?? 0)}
            </p>
            <p className="mt-0.5 max-w-24 text-[11px] leading-tight text-ink-4">
              {slices.find((s) => s.id === active)?.label}
            </p>
          </div>
        ) : (
          <div>
            <p className="tnum font-sans text-[19px] font-semibold text-ink">{centerValue}</p>
            <p className="mt-0.5 text-[11px] text-ink-4">{centerLabel}</p>
          </div>
        )}
      </div>
    </div>
  )
}

/** The legend is the dependable identity channel; the arcs supplement it. */
export function DonutLegend({
  slices,
  formatValue,
  className,
}: {
  slices: DonutSlice[]
  formatValue: (value: number) => string
  className?: string
}) {
  const total = slices.reduce((sum, slice) => sum + slice.value, 0)

  return (
    <ul className={cn('min-w-0 flex-1 space-y-2', className)}>
      {slices.map((slice, index) => (
        <li key={slice.id} className="flex items-center gap-2.5 text-[12.5px]">
          <span
            aria-hidden
            className="size-2.5 shrink-0 rounded-[3px]"
            style={{ background: seriesColor(index) }}
          />
          <span className="min-w-0 flex-1 truncate text-ink-2">{slice.label}</span>
          <span className="tnum shrink-0 text-ink-3">{formatValue(slice.value)}</span>
          <span className="tnum w-10 shrink-0 text-right text-ink-4">
            {total > 0 ? `${Math.round((slice.value / total) * 100)}%` : '—'}
          </span>
        </li>
      ))}
    </ul>
  )
}
