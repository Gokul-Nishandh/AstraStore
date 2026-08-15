import { useMemo, useRef, useState } from 'react'
import { linePath, linearScale, niceDomain, niceTicks, seriesColor } from './scale'
import { cn } from '../../lib/cn'

export interface LineSeries {
  id: string
  label: string
  /** Same length and order as `labels`. Null renders a gap, not a zero. */
  values: (number | null)[]
}

export interface LineChartProps {
  series: LineSeries[]
  /** X labels, one per index. Only a few are drawn as ticks. */
  labels: string[]
  height?: number
  formatValue?: (value: number) => string
  /** Axis unit shown once beside the top tick rather than on every label. */
  unit?: string
  className?: string
  'aria-label': string
}

const PAD = { top: 10, right: 12, bottom: 22, left: 46 }

/**
 * Multi-series line chart with a crosshair readout.
 *
 * One y-axis, always. Two measures on different scales get two charts — a
 * second axis lets the author place the crossing point wherever the story
 * needs it, which makes the chart an argument rather than a measurement.
 */
export function LineChart({
  series,
  labels,
  height = 200,
  formatValue = (v) => v.toLocaleString(),
  unit,
  className,
  ...props
}: LineChartProps) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null)
  const svgRef = useRef<SVGSVGElement>(null)

  // A fixed viewBox with non-uniform scaling would distort stroke widths, so
  // the chart draws into a nominal width and stretches via preserveAspectRatio
  // = none only on the x-axis-free sparkline. Here we keep it uniform.
  const width = 640

  const domain = useMemo(
    () => niceDomain(series.flatMap((s) => s.values.filter((v): v is number => v !== null))),
    [series],
  )
  const ticks = useMemo(() => niceTicks(domain[0], domain[1], 4), [domain])

  const x = linearScale([0, Math.max(1, labels.length - 1)], [PAD.left, width - PAD.right])
  const y = linearScale(domain, [height - PAD.bottom, PAD.top])

  const hasData = series.some((s) => s.values.some((v) => v !== null))

  if (!hasData) {
    return (
      <div
        className={cn('flex items-center justify-center rounded-lg border border-dashed border-line', className)}
        style={{ height }}
      >
        <p className="text-[12.5px] text-ink-4">Not enough data for this window yet</p>
      </div>
    )
  }

  const onPointerMove = (event: React.PointerEvent<SVGSVGElement>) => {
    const rect = svgRef.current?.getBoundingClientRect()
    if (!rect) return
    const ratio = (event.clientX - rect.left) / rect.width
    const plotStart = PAD.left / width
    const plotWidth = (width - PAD.left - PAD.right) / width
    const t = (ratio - plotStart) / plotWidth
    const index = Math.round(t * (labels.length - 1))
    setHoverIndex(index >= 0 && index < labels.length ? index : null)
  }

  // Label every tick would crowd; four reference points read cleanly.
  const xTickIndices = labels.length <= 4
    ? labels.map((_, i) => i)
    : [0, Math.floor((labels.length - 1) / 3), Math.floor((2 * (labels.length - 1)) / 3), labels.length - 1]

  return (
    <div className={cn('relative', className)}>
      <svg
        ref={svgRef}
        role="img"
        aria-label={props['aria-label']}
        viewBox={`0 0 ${width} ${height}`}
        className="w-full"
        style={{ height }}
        onPointerMove={onPointerMove}
        onPointerLeave={() => setHoverIndex(null)}
      >
        {/* Gridlines: hairline, solid, one step off the surface. Dashed rules
            read as data; these must recede behind the marks. */}
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

        {xTickIndices.map((index) => (
          <text
            key={index}
            x={x(index)}
            y={height - 6}
            textAnchor={index === 0 ? 'start' : index === labels.length - 1 ? 'end' : 'middle'}
            className="fill-[var(--ink-4)] text-[10px]"
          >
            {labels[index]}
          </text>
        ))}

        {hoverIndex !== null && (
          <line
            x1={x(hoverIndex)}
            x2={x(hoverIndex)}
            y1={PAD.top}
            y2={height - PAD.bottom}
            stroke="var(--line-strong)"
            strokeWidth="1"
          />
        )}

        {series.map((s, seriesIndex) => (
          <path
            key={s.id}
            d={linePath(s.values.map((value, index) => ({ x: x(index), y: value === null ? null : y(value) })))}
            fill="none"
            stroke={seriesColor(seriesIndex)}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        ))}

        {hoverIndex !== null &&
          series.map((s, seriesIndex) => {
            const value = s.values[hoverIndex]
            if (value === null || value === undefined) return null
            return (
              <circle
                key={s.id}
                cx={x(hoverIndex)}
                cy={y(value)}
                r="4"
                fill={seriesColor(seriesIndex)}
                stroke="var(--surface)"
                strokeWidth="2"
              />
            )
          })}
      </svg>

      {hoverIndex !== null && (
        <div
          className={cn(
            'pointer-events-none absolute top-2 z-30 min-w-36 rounded-md border border-line',
            'bg-bg-elevated px-2.5 py-2 shadow-lg',
            hoverIndex > labels.length / 2 ? 'left-2' : 'right-2',
          )}
        >
          <p className="text-[11px] font-medium text-ink-3">{labels[hoverIndex]}</p>
          <ul className="mt-1.5 space-y-1">
            {series.map((s, seriesIndex) => (
              <li key={s.id} className="flex items-center justify-between gap-4 text-[11.5px]">
                <span className="flex items-center gap-1.5 text-ink-2">
                  <span
                    aria-hidden
                    className="size-2 shrink-0 rounded-[2px]"
                    style={{ background: seriesColor(seriesIndex) }}
                  />
                  {s.label}
                </span>
                <span className="tnum font-medium text-ink">
                  {s.values[hoverIndex] === null || s.values[hoverIndex] === undefined
                    ? '—'
                    : formatValue(s.values[hoverIndex] as number)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* A legend is present whenever identity matters — two or more series.
          One series is already named by the panel title. */}
      {series.length > 1 && (
        <ul className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5">
          {series.map((s, seriesIndex) => (
            <li key={s.id} className="flex items-center gap-1.5 text-[11.5px] text-ink-3">
              <span
                aria-hidden
                className="h-0.5 w-3 rounded-full"
                style={{ background: seriesColor(seriesIndex) }}
              />
              {s.label}
            </li>
          ))}
          {unit && <li className="ml-auto text-[11px] text-ink-4">{unit}</li>}
        </ul>
      )}
    </div>
  )
}
