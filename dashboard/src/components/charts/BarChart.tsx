import { useMemo, useState } from 'react'
import { linearScale, niceDomain, niceTicks } from './scale'
import { cn } from '../../lib/cn'

export interface Bar {
  label: string
  value: number
  /** Series slot for this bar. Omit to paint every bar in slot 1. */
  series?: number
}

export interface BarChartProps {
  bars: Bar[]
  height?: number
  formatValue?: (value: number) => string
  className?: string
  'aria-label': string
}

const PAD = { top: 10, right: 8, bottom: 24, left: 46 }
const MAX_BAR = 24

/**
 * Vertical bars for magnitude comparison.
 *
 * Bars default to a single series colour. Colouring nominal bars by their own
 * value spends the identity channel re-encoding what bar height already
 * shows — pass `series` only when the bars really are different entities.
 */
export function BarChart({
  bars,
  height = 200,
  formatValue = (v) => v.toLocaleString(),
  className,
  ...props
}: BarChartProps) {
  const [hover, setHover] = useState<number | null>(null)
  const width = 640

  const domain = useMemo(() => niceDomain(bars.map((b) => b.value)), [bars])
  const ticks = useMemo(() => niceTicks(domain[0], domain[1], 4), [domain])

  if (bars.length === 0) {
    return (
      <div
        className={cn('flex items-center justify-center rounded-lg border border-dashed border-line', className)}
        style={{ height }}
      >
        <p className="text-[12.5px] text-ink-4">Nothing recorded in this window</p>
      </div>
    )
  }

  const y = linearScale(domain, [height - PAD.bottom, PAD.top])
  const band = (width - PAD.left - PAD.right) / bars.length
  // Cap the bar and let the leftover band be air — a bar that fills its slot
  // turns the gaps between categories into a competing shape.
  const barWidth = Math.min(MAX_BAR, band * 0.62)
  const baseline = y(Math.max(0, domain[0]))

  return (
    <div className={cn('relative', className)}>
      <svg
        role="img"
        aria-label={props['aria-label']}
        viewBox={`0 0 ${width} ${height}`}
        className="w-full"
        style={{ height }}
        onPointerLeave={() => setHover(null)}
      >
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              x1={PAD.left}
              x2={width - PAD.right}
              y1={y(tick)}
              y2={y(tick)}
              stroke="var(--line)"
              strokeWidth="1"
            />
            <text
              x={PAD.left - 8}
              y={y(tick)}
              textAnchor="end"
              dominantBaseline="middle"
              className="tnum fill-[var(--ink-4)] text-[10px]"
            >
              {formatValue(tick)}
            </text>
          </g>
        ))}

        {bars.map((bar, index) => {
          const cx = PAD.left + band * (index + 0.5)
          const top = y(bar.value)
          const barHeight = Math.max(1, baseline - top)
          const radius = Math.min(4, barWidth / 2, barHeight)
          const color = `var(--chart-${bar.series ?? 1})`

          return (
            <g key={`${bar.label}-${index}`} onPointerEnter={() => setHover(index)}>
              {/* Full-height hit area: a 2px-tall bar is impossible to hover. */}
              <rect
                x={cx - band / 2}
                y={PAD.top}
                width={band}
                height={height - PAD.top - PAD.bottom}
                fill="transparent"
              />
              <path
                // Rounded at the data end, square at the baseline, so the bar
                // reads as growing from the axis rather than floating.
                d={`M${cx - barWidth / 2} ${baseline}
                    L${cx - barWidth / 2} ${top + radius}
                    Q${cx - barWidth / 2} ${top} ${cx - barWidth / 2 + radius} ${top}
                    L${cx + barWidth / 2 - radius} ${top}
                    Q${cx + barWidth / 2} ${top} ${cx + barWidth / 2} ${top + radius}
                    L${cx + barWidth / 2} ${baseline} Z`}
                fill={color}
                opacity={hover === null || hover === index ? 1 : 0.45}
              />
            </g>
          )
        })}

        {bars.length <= 12 &&
          bars.map((bar, index) => (
            <text
              key={`label-${index}`}
              x={PAD.left + band * (index + 0.5)}
              y={height - 7}
              textAnchor="middle"
              className="fill-[var(--ink-4)] text-[10px]"
            >
              {bar.label}
            </text>
          ))}
      </svg>

      {hover !== null && (
        <div
          className={cn(
            'pointer-events-none absolute top-2 z-30 rounded-md border border-line bg-bg-elevated',
            'px-2.5 py-1.5 shadow-lg',
            hover > bars.length / 2 ? 'left-2' : 'right-2',
          )}
        >
          <p className="text-[11px] text-ink-3">{bars[hover].label}</p>
          <p className="tnum text-[13px] font-semibold text-ink">{formatValue(bars[hover].value)}</p>
        </div>
      )}
    </div>
  )
}
