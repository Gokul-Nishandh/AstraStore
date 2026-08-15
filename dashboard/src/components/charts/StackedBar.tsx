import { useState } from 'react'
import { seriesColor } from './scale'
import { cn } from '../../lib/cn'

export interface StackedSegment {
  id: string
  label: string
  /**
   * Null means "not measured". The segment is dropped rather than drawn at
   * zero width, because a zero-width slice and an unmeasured one look
   * identical and only one of them is a fact.
   */
  value: number | null
}

export interface StackedBarProps {
  segments: StackedSegment[]
  /**
   * Track total. Anything above the summed segments is drawn as unallocated
   * space. Omit it and the bar shows the composition of the segments alone.
   */
  total?: number | null
  remainderLabel?: string
  formatValue?: (value: number) => string
  className?: string
  'aria-label': string
}

/**
 * One bar broken into its parts — capacity into stored, overhead and free.
 *
 * Colour comes from the categorical ramp in slot order, keyed to the position
 * a segment was declared in, so a segment keeps its hue when a neighbour goes
 * missing. The unallocated remainder is deliberately NOT a series: it is the
 * absence of a category, and painting it chart-3 would invent a third thing.
 */
export function StackedBar({
  segments,
  total = null,
  remainderLabel = 'Free',
  formatValue = (value) => value.toLocaleString(),
  className,
  ...props
}: StackedBarProps) {
  const [hover, setHover] = useState<string | null>(null)

  const measured = segments
    .map((segment, index) => ({ ...segment, color: seriesColor(index) }))
    .filter((segment) => segment.value !== null && Number.isFinite(segment.value) && segment.value > 0)

  const sum = measured.reduce((acc, segment) => acc + (segment.value as number), 0)
  const track = total !== null && Number.isFinite(total) && total > sum ? total : sum
  const remainder = track - sum

  if (track <= 0) {
    return (
      <div
        role="img"
        aria-label={`${props['aria-label']} — awaiting data`}
        className={cn('h-5 w-full rounded-md bg-surface-3', className)}
        style={{
          backgroundImage:
            'repeating-linear-gradient(135deg, var(--line-strong) 0 4px, transparent 4px 8px)',
        }}
      />
    )
  }

  // Cumulative offsets drive the tooltip anchor, so the readout appears over
  // the slice being pointed at rather than at a fixed corner.
  let cumulative = 0
  const slices = measured.map((segment) => {
    const value = segment.value as number
    const centerPercent = ((cumulative + value / 2) / track) * 100
    cumulative += value
    return { ...segment, value, centerPercent }
  })

  const active = slices.find((slice) => slice.id === hover)
  const remainderActive = hover === '__remainder' && remainder > 0
  const remainderCenter = ((cumulative + remainder / 2) / track) * 100

  return (
    <div className={cn('relative', className)}>
      <div
        role="img"
        aria-label={props['aria-label']}
        className="flex h-5 w-full gap-[2px] overflow-hidden rounded-md bg-surface"
        onPointerLeave={() => setHover(null)}
      >
        {slices.map((slice) => (
          <div
            key={slice.id}
            onPointerEnter={() => setHover(slice.id)}
            className="min-w-[3px] transition-opacity duration-150"
            style={{
              flexGrow: slice.value,
              flexBasis: 0,
              background: slice.color,
              opacity: hover === null || hover === slice.id ? 1 : 0.45,
            }}
          />
        ))}
        {remainder > 0 && (
          <div
            onPointerEnter={() => setHover('__remainder')}
            className="min-w-[3px] bg-surface-3 transition-opacity duration-150"
            style={{
              flexGrow: remainder,
              flexBasis: 0,
              opacity: hover === null || remainderActive ? 1 : 0.45,
            }}
          />
        )}
      </div>

      {(active || remainderActive) && (
        <div
          className={cn(
            'pointer-events-none absolute bottom-full z-30 mb-2 -translate-x-1/2 whitespace-nowrap',
            'rounded-md border border-line bg-bg-elevated px-2.5 py-1.5 shadow-lg',
          )}
          style={{ left: `${active ? active.centerPercent : remainderCenter}%` }}
        >
          <p className="text-[11.5px] text-ink-3">{active ? active.label : remainderLabel}</p>
          <p className="tnum text-[13px] font-semibold text-ink">
            {formatValue(active ? active.value : remainder)}
          </p>
        </div>
      )}

      {/* Identity lives in the swatch, never in the text colour. */}
      {(slices.length > 1 || remainder > 0) && (
        <ul className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-1.5">
          {slices.map((slice) => (
            <li key={slice.id} className="flex items-center gap-1.5 text-[11.5px] text-ink-3">
              <span
                aria-hidden
                className="size-2 shrink-0 rounded-[2px]"
                style={{ background: slice.color }}
              />
              {slice.label}
              <span className="tnum font-medium text-ink-2">{formatValue(slice.value)}</span>
            </li>
          ))}
          {remainder > 0 && (
            <li className="flex items-center gap-1.5 text-[11.5px] text-ink-3">
              <span aria-hidden className="size-2 shrink-0 rounded-[2px] bg-surface-3" />
              {remainderLabel}
              <span className="tnum font-medium text-ink-2">{formatValue(remainder)}</span>
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
