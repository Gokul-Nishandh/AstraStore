import { cn } from '../../lib/cn'

export interface MeterProps {
  /** 0–1. Null renders the "no data yet" track rather than an empty bar. */
  ratio: number | null
  /** Ratios at which the fill escalates. */
  thresholds?: { warning: number; danger: number }
  size?: 'sm' | 'md'
  className?: string
  'aria-label': string
}

/**
 * A capacity bar whose fill carries severity.
 *
 * A null ratio is drawn as a hatched track, visibly different from 0% — on a
 * storage dashboard "nothing stored" and "we have not measured" are opposite
 * conclusions, and a plain empty bar reads as the first.
 */
export function Meter({
  ratio,
  thresholds = { warning: 0.75, danger: 0.9 },
  size = 'md',
  className,
  ...props
}: MeterProps) {
  const height = size === 'sm' ? 'h-1.5' : 'h-2'

  if (ratio === null || !Number.isFinite(ratio)) {
    return (
      <div
        role="img"
        aria-label={`${props['aria-label']} — awaiting data`}
        className={cn(
          'w-full overflow-hidden rounded-full bg-surface-3',
          height,
          className,
        )}
        style={{
          backgroundImage:
            'repeating-linear-gradient(135deg, var(--line-strong) 0 4px, transparent 4px 8px)',
        }}
      />
    )
  }

  const clamped = Math.min(1, Math.max(0, ratio))
  const tone =
    clamped >= thresholds.danger ? 'bg-danger' : clamped >= thresholds.warning ? 'bg-warning' : 'bg-accent'

  return (
    <div
      role="progressbar"
      aria-label={props['aria-label']}
      aria-valuenow={Math.round(clamped * 100)}
      aria-valuemin={0}
      aria-valuemax={100}
      className={cn('w-full overflow-hidden rounded-full bg-surface-3', height, className)}
    >
      <div
        className={cn('h-full rounded-full transition-[width] duration-500 ease-[var(--ease-out)]', tone)}
        style={{ width: `${clamped * 100}%` }}
      />
    </div>
  )
}
