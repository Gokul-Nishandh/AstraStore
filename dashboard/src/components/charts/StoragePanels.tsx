import { useMemo } from 'react'
import { ChartPie, TrendingUp } from 'lucide-react'
import { CardSection } from '../ui/Card'
import { EmptyState, ErrorState } from '../ui/EmptyState'
import { Skeleton } from '../ui/Skeleton'
import { Donut, DonutLegend, type DonutSlice } from './Donut'
import { LineChart } from './LineChart'
import { formatBytes, formatCount } from '../../lib/format'
import type { StorageBreakdown } from '../../types/api'
import type { AsyncState } from '../../lib/hooks'

const CATEGORY_LABELS: Record<string, string> = {
  image: 'Images',
  video: 'Video',
  audio: 'Audio',
  pdf: 'PDFs',
  document: 'Documents',
  spreadsheet: 'Spreadsheets',
  presentation: 'Presentations',
  archive: 'Archives',
  other: 'Other',
}

/**
 * Slots beyond the palette fold into "Other" rather than generating a seventh
 * hue. The categorical palette is fixed at six for colour-blind separation,
 * and inventing a colour to fit the data defeats the check that validated it.
 */
const MAX_SLICES = 6

export function StorageByType({ state }: { state: AsyncState<StorageBreakdown> }) {
  const slices: DonutSlice[] = useMemo(() => {
    const rows = state.data?.byCategory ?? []
    const named = rows.map((row) => ({
      id: row.category,
      label: CATEGORY_LABELS[row.category] ?? row.category,
      value: row.totalBytes,
    }))

    if (named.length <= MAX_SLICES) return named

    const head = named.slice(0, MAX_SLICES - 1)
    const tail = named.slice(MAX_SLICES - 1)
    return [
      ...head,
      {
        id: 'other',
        label: `Other (${tail.length} types)`,
        value: tail.reduce((sum, slice) => sum + slice.value, 0),
      },
    ]
  }, [state.data])

  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  const objectCount = (state.data?.byCategory ?? []).reduce((sum, row) => sum + row.objectCount, 0)

  return (
    <CardSection
      icon={<ChartPie />}
      title="What you are storing"
      description="Bytes by file type across every bucket you own."
    >
      {state.error && !state.data ? (
        <ErrorState description={state.error} onRetry={state.refresh} />
      ) : state.loading && !state.data ? (
        <div className="flex items-center gap-6">
          <Skeleton className="size-[168px] rounded-full" />
          <div className="flex-1 space-y-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-4 w-full" />
            ))}
          </div>
        </div>
      ) : slices.length === 0 ? (
        <EmptyState
          size="sm"
          icon={<ChartPie />}
          title="Nothing stored yet"
          description="Upload an object and this fills in with a breakdown by file type."
        />
      ) : (
        <div className="flex flex-col items-center gap-6 sm:flex-row sm:items-start">
          <Donut
            aria-label="Stored bytes by file type"
            slices={slices}
            centerValue={formatBytes(total)}
            centerLabel={`${formatCount(objectCount)} objects`}
            formatValue={formatBytes}
          />
          <DonutLegend slices={slices} formatValue={formatBytes} />
        </div>
      )}
    </CardSection>
  )
}

/**
 * Uploads per day.
 *
 * The backend omits days on which nothing happened, so the gaps are filled
 * here with explicit zeros: on this chart a quiet day genuinely is zero
 * uploads, and leaving a hole would imply the data is missing instead.
 */
export function UploadActivity({
  state,
  days = 30,
}: {
  state: AsyncState<StorageBreakdown>
  days?: number
}) {
  const { labels, values, totalObjects } = useMemo(() => {
    const byDate = new Map((state.data?.daily ?? []).map((point) => [point.date, point]))

    const labels: string[] = []
    const values: number[] = []
    let totalObjects = 0

    for (let offset = days - 1; offset >= 0; offset--) {
      const date = new Date()
      date.setUTCHours(0, 0, 0, 0)
      date.setUTCDate(date.getUTCDate() - offset)
      const key = date.toISOString().slice(0, 10)

      const point = byDate.get(key)
      labels.push(date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' }))
      values.push(point?.objectCount ?? 0)
      totalObjects += point?.objectCount ?? 0
    }

    return { labels, values, totalObjects }
  }, [state.data, days])

  return (
    <CardSection
      icon={<TrendingUp />}
      title="Upload activity"
      description={`Objects written per day over the last ${days} days.`}
    >
      {state.error && !state.data ? (
        <ErrorState description={state.error} onRetry={state.refresh} />
      ) : state.loading && !state.data ? (
        <Skeleton className="h-40 w-full" />
      ) : totalObjects === 0 ? (
        <EmptyState
          size="sm"
          icon={<TrendingUp />}
          title="No uploads in this period"
          description="Activity appears here as soon as you write an object."
        />
      ) : (
        <LineChart
          aria-label="Objects uploaded per day"
          labels={labels}
          series={[{ id: 'uploads', label: 'Objects', values }]}
          formatValue={(value) => formatCount(value)}
          height={168}
        />
      )}
    </CardSection>
  )
}
