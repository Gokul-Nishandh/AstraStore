import { cn } from '../../lib/cn'

export type ProgressTone = 'accent' | 'success' | 'warning' | 'danger' | 'neutral'

const fills: Record<ProgressTone, string> = {
  accent: 'bg-accent',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  neutral: 'bg-ink-4',
}

const heights = {
  xs: 'h-1',
  sm: 'h-1.5',
  md: 'h-2.5',
} as const

export interface ProgressBarProps {
  /** 0–100. Ignored when `indeterminate`. */
  percent?: number | null
  /**
   * For work with no measurable progress — a request in flight, a rebuild
   * queued server-side. Drops `aria-valuenow` so assistive tech announces
   * "busy" instead of a fabricated 0%.
   */
  indeterminate?: boolean
  tone?: ProgressTone
  size?: keyof typeof heights
  /** Accessible name. Required in practice — "progressbar" alone says nothing. */
  label?: string
  /** Renders the percentage as text beside the bar. */
  showValue?: boolean
  /** Classes for the filled portion. */
  className?: string
  /** Classes for the track. */
  trackClassName?: string
}

export function ProgressBar({
  percent = 0,
  indeterminate = false,
  tone = 'accent',
  size = 'sm',
  label,
  showValue = false,
  className,
  trackClassName,
}: ProgressBarProps) {
  const raw = percent ?? 0
  const clamped = Number.isFinite(raw) ? Math.max(0, Math.min(100, raw)) : 0
  const rounded = Math.round(clamped)

  const bar = (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={indeterminate ? undefined : rounded}
      aria-valuetext={indeterminate ? undefined : `${rounded}%`}
      aria-busy={indeterminate || undefined}
      className={cn(
        'relative w-full overflow-hidden rounded-full bg-surface-3',
        heights[size],
        trackClassName,
      )}
    >
      {indeterminate ? (
        // A short travelling segment rather than a full-width pulse, so it
        // never reads as "almost done".
        <div
          className={cn(
            'absolute inset-y-0 left-0 w-1/3 rounded-full motion-safe:animate-sweep',
            fills[tone],
            className,
          )}
        />
      ) : (
        <div
          className={cn(
            'h-full rounded-full transition-[width] duration-300 ease-[var(--ease-out)]',
            fills[tone],
            className,
          )}
          style={{ width: `${clamped}%` }}
        />
      )}
    </div>
  )

  if (!showValue) return bar

  return (
    <div className="flex items-center gap-3">
      {bar}
      <span className="tnum shrink-0 text-[12px] font-medium text-ink-2">
        {indeterminate ? '—' : `${rounded}%`}
      </span>
    </div>
  )
}
