import { useMemo } from 'react'
import type { MonitoringWindow, ServiceUptime } from '../../types/api'
import { api } from '../../lib/api'
import { usePolling } from '../../lib/hooks'
import { ErrorState } from '../ui/EmptyState'
import { Skeleton } from '../ui/Skeleton'
import { Badge, serviceStatusTone } from '../ui/Badge'
import { UptimeStrip } from '../charts/UptimeStrip'
import { LineChart } from '../charts/LineChart'
import { IncidentTimeline } from './IncidentTimeline'
import { formatDate, formatDuration, formatMillis, formatUptime } from '../../lib/format'

function pointLabel(iso: string, window: MonitoringWindow): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return window === '1h' || window === '24h'
    ? date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2.5">
      <p className="text-[10.5px] font-medium text-ink-4">{label}</p>
      <p className="tnum mt-1 text-[15px] font-semibold text-ink">{value}</p>
      {hint && <p className="mt-0.5 text-[10.5px] text-ink-4">{hint}</p>}
    </div>
  )
}

/**
 * The drill-down behind a service row.
 *
 * It refetches the single-service endpoint rather than reusing the row's
 * summary, because only that response carries the full incident list — the
 * question someone opening a row is actually asking.
 */
export function ServiceDetail({
  serviceId,
  window,
  fallback,
}: {
  serviceId: string
  window: MonitoringWindow
  /** The row's own summary, shown while the detail request is in flight. */
  fallback?: ServiceUptime
}) {
  const { data, error, loading, refresh } = usePolling(
    () => api.monitoringService(serviceId, window),
    0,
    [serviceId, window],
  )

  const service = data ?? fallback ?? null

  const latency = useMemo(() => {
    if (!service) return null
    const points = service.sparkline
    if (points.length === 0) return null
    const values = points.map((point) => point.ms)
    if (!values.some((value) => value !== null)) return null
    return {
      labels: points.map((point) => pointLabel(point.t, window)),
      values,
    }
  }, [service, window])

  if (loading && !service) {
    return (
      <div className="space-y-3 p-4">
        <Skeleton className="h-7 w-full" />
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-14" />
          ))}
        </div>
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (error && !service) {
    return (
      <div className="p-4">
        <ErrorState title="Could not load this service" description={error} onRetry={refresh} />
      </div>
    )
  }

  if (!service) return null

  return (
    <div className="space-y-5 p-4">
      {/* A failed refresh keeps the last good detail on screen and says so,
          rather than replacing a working panel with an error. */}
      {error && (
        <p className="rounded-md border border-warning-border bg-warning-subtle px-3 py-2 text-[12px] text-ink-2">
          {error} Showing the last values we received.
        </p>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={serviceStatusTone(service.status)} dot>
            {service.status === 'UP' ? 'Operational' : service.status === 'DOWN' ? 'Down' : service.status === 'DEGRADED' ? 'Degraded' : 'Unknown'}
          </Badge>
          <span className="text-[12px] text-ink-3">
            {service.lastStateChange
              ? `State last changed ${formatDate(service.lastStateChange)}`
              : 'No state change recorded in this window'}
          </span>
        </div>
        <span className="text-[11.5px] text-ink-4 capitalize">{service.kind}</span>
      </div>

      <div>
        <p className="mb-2 text-[11.5px] font-medium text-ink-3">Availability across the window</p>
        <UptimeStrip
          points={service.sparkline}
          aria-label={`${service.name} availability across the selected window`}
        />
      </div>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Stat label="Uptime" value={formatUptime(service.uptimePercent)} hint="in window" />
        <Stat label="Downtime" value={formatDuration(service.downtimeSeconds)} hint="cumulative" />
        <Stat label="Incidents" value={String(service.incidentCount)} hint="in window" />
        <Stat label="Last response" value={formatMillis(service.responseTimeMs.last)} hint="most recent probe" />
      </div>

      <div className="grid grid-cols-3 gap-2">
        <Stat label="p50" value={formatMillis(service.responseTimeMs.p50)} />
        <Stat label="p95" value={formatMillis(service.responseTimeMs.p95)} />
        <Stat label="p99" value={formatMillis(service.responseTimeMs.p99)} />
      </div>

      <div>
        <p className="mb-2 text-[11.5px] font-medium text-ink-3">Response time</p>
        {latency ? (
          <LineChart
            series={[{ id: 'ms', label: `${service.name} response time`, values: latency.values }]}
            labels={latency.labels}
            height={180}
            formatValue={(value) => formatMillis(value)}
            aria-label={`${service.name} response time across the selected window`}
          />
        ) : (
          <div className="flex h-[120px] items-center justify-center rounded-lg border border-dashed border-line">
            <p className="text-[12.5px] text-ink-4">No response times recorded in this window yet</p>
          </div>
        )}
      </div>

      <div>
        <p className="mb-2 text-[11.5px] font-medium text-ink-3">Incidents</p>
        <IncidentTimeline
          incidents={service.incidents ?? []}
          showService={false}
          emptyTitle="No incidents for this service"
          emptyDescription="This service has not gone down over the selected window."
        />
      </div>
    </div>
  )
}
