import { useState } from 'react'
import type { UptimePoint } from '../../types/api'
import { formatMillis } from '../../lib/format'
import { cn } from '../../lib/cn'

export interface UptimeStripProps {
  points: UptimePoint[]
  /** Bucket count the backend was asked for, so gaps render as gaps. */
  expected?: number
  className?: string
  'aria-label': string
}

function formatStamp(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * The availability timeline — one bar per bucket, oldest to newest.
 *
 * Status, not series: it uses the reserved success/danger tokens rather than
 * the categorical ramp, and every bar pairs its colour with text in the
 * tooltip, so the state is never carried by hue alone.
 *
 * A bucket with no samples is drawn in the line colour rather than as
 * "up" — a monitoring gap and a healthy period must not look the same, which
 * is the failure mode that lets an outage hide inside a green strip.
 */
export function UptimeStrip({ points, expected, className, ...props }: UptimeStripProps) {
  const [hover, setHover] = useState<number | null>(null)

  const missing = expected && expected > points.length ? expected - points.length : 0
  const slots: (UptimePoint | null)[] = [...Array<null>(missing).fill(null), ...points]

  if (slots.length === 0) {
    return (
      <div className={cn('flex h-7 items-center rounded-md bg-surface-2 px-2', className)}>
        <span className="text-[11px] text-ink-4">Awaiting data</span>
      </div>
    )
  }

  const active = hover !== null ? slots[hover] : null

  return (
    <div className={cn('relative', className)}>
      <div
        role="img"
        aria-label={props['aria-label']}
        className="flex h-7 items-stretch gap-[2px]"
        onPointerLeave={() => setHover(null)}
      >
        {slots.map((point, index) => (
          <div
            key={index}
            onPointerEnter={() => setHover(index)}
            className={cn(
              'min-w-0 flex-1 rounded-[2px] transition-opacity duration-100',
              point === null
                ? 'bg-line'
                : point.up
                  ? 'bg-success'
                  : 'bg-danger',
              hover !== null && hover !== index && 'opacity-45',
            )}
          />
        ))}
      </div>

      {active !== undefined && hover !== null && (
        <div
          className={cn(
            'pointer-events-none absolute bottom-full z-30 mb-2 -translate-x-1/2 whitespace-nowrap',
            'rounded-md border border-line bg-bg-elevated px-2.5 py-1.5 shadow-lg',
          )}
          style={{ left: `${((hover + 0.5) / slots.length) * 100}%` }}
        >
          {active === null ? (
            <p className="text-[11.5px] text-ink-3">No data collected</p>
          ) : (
            <>
              <p className="text-[11.5px] font-medium text-ink">
                {active.up ? 'Operational' : 'Outage'}
              </p>
              <p className="tnum mt-0.5 text-[11px] text-ink-3">
                {formatStamp(active.t)}
                {active.ms !== null && ` · ${formatMillis(active.ms)}`}
              </p>
            </>
          )}
        </div>
      )}
    </div>
  )
}
