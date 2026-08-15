import { type ReactNode } from 'react'
import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { Sparkline } from './Sparkline'
import { cn } from '../../lib/cn'

export interface StatTileProps {
  label: string
  /** Pre-formatted. Pass "—" for genuinely absent data, never "0". */
  value: ReactNode
  /** Qualifier under the value — "of 4.2 TB", "across 3 nodes". */
  detail?: ReactNode
  delta?: {
    value: string
    direction: 'up' | 'down'
    /** Whether a rise is good here. Error rate up is bad; throughput up is good. */
    goodDirection?: 'up' | 'down'
    /** Names the comparison period. A delta without one is meaningless. */
    versus: string
  }
  trend?: (number | null)[]
  series?: number
  icon?: ReactNode
  /** Renders the whole tile as a button. */
  onClick?: () => void
  className?: string
}

/**
 * The headline figure unit.
 *
 * The value uses the sans face, not the display face: a large number set in
 * Space Grotesk reads as decoration rather than as data, and these tiles sit
 * next to real charts that must feel like the same instrument.
 */
export function StatTile({
  label,
  value,
  detail,
  delta,
  trend,
  series = 1,
  icon,
  onClick,
  className,
}: StatTileProps) {
  const goodDirection = delta?.goodDirection ?? 'up'
  const isGood = delta ? delta.direction === goodDirection : true

  const content = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="text-[11.5px] font-medium text-ink-3">{label}</p>
        {icon && (
          <span aria-hidden className="shrink-0 text-ink-4 [&>svg]:size-4">
            {icon}
          </span>
        )}
      </div>

      <p className="tnum mt-2 font-sans text-[26px] leading-none font-semibold tracking-tight text-ink">
        {value}
      </p>

      {detail && <p className="mt-1.5 text-[11.5px] text-ink-4">{detail}</p>}

      {(delta || trend) && (
        <div className="mt-3 flex items-end justify-between gap-3">
          {delta ? (
            <span
              className={cn(
                'inline-flex items-center gap-1 text-[11.5px] font-medium',
                isGood ? 'text-success-text' : 'text-danger-text',
              )}
            >
              {delta.direction === 'up' ? (
                <ArrowUpRight aria-hidden className="size-3.5" />
              ) : (
                <ArrowDownRight aria-hidden className="size-3.5" />
              )}
              <span className="tnum">{delta.value}</span>
              <span className="font-normal text-ink-4">{delta.versus}</span>
            </span>
          ) : (
            <span />
          )}

          {trend && trend.length > 1 && (
            <Sparkline
              values={trend}
              series={series}
              width={72}
              height={24}
              showEndDot={false}
              aria-label={`${label} trend`}
            />
          )}
        </div>
      )}
    </>
  )

  const shell = 'flex flex-col rounded-xl border border-line bg-surface p-4 text-left shadow-xs'

  if (onClick) {
    return (
      <button
        type="button"
        onClick={onClick}
        className={cn(shell, 'transition-colors duration-150 hover:border-line-strong hover:bg-surface-2', className)}
      >
        {content}
      </button>
    )
  }

  return <div className={cn(shell, className)}>{content}</div>
}
