import { ChevronRight } from 'lucide-react'
import type { ServiceUptime } from '../../types/api'
import { Table, THead, TBody, TR, TH, TD } from '../ui/Table'
import { Badge, serviceStatusTone } from '../ui/Badge'
import { UptimeStrip } from '../charts/UptimeStrip'
import { formatDuration, formatMillis, formatUptime } from '../../lib/format'
import { cn } from '../../lib/cn'

const STATUS_LABEL: Record<string, string> = {
  UP: 'Operational',
  DOWN: 'Down',
  DEGRADED: 'Degraded',
  UNKNOWN: 'Unknown',
}

/**
 * Every service, every measure, one row each.
 *
 * The strip in the last column is the availability history rather than a
 * latency trend: the question this table answers is "was it up", and a bar
 * per bucket keeps a single failed probe visible instead of averaging it into
 * a smooth line.
 */
export function ServiceTable({
  services,
  selectedId,
  onSelect,
  detailPanelId,
}: {
  services: ServiceUptime[]
  selectedId: string | null
  onSelect: (id: string) => void
  /** Id of the panel the rows expand into, for `aria-controls`. */
  detailPanelId: string
}) {
  return (
    <Table
      dense
      className="min-w-[860px]"
      caption="Monitored services with availability, downtime and response times"
    >
      <THead>
        <TR interactive={false}>
          <TH>Service</TH>
          <TH>Status</TH>
          <TH numeric>Uptime</TH>
          <TH numeric>Downtime</TH>
          <TH numeric>Incidents</TH>
          <TH numeric>p50</TH>
          <TH numeric>p95</TH>
          <TH numeric>p99</TH>
          <TH className="w-[180px]">Availability</TH>
          <TH className="w-px">
            <span className="sr-only">Open detail</span>
          </TH>
        </TR>
      </THead>
      <TBody>
        {services.map((service) => {
          const open = selectedId === service.id
          return (
            <TR
              key={service.id}
              selected={open}
              onClick={() => onSelect(service.id)}
              className="cursor-pointer"
            >
              <TD className="whitespace-nowrap">
                <span className="block font-medium text-ink">{service.name}</span>
                <span className="block text-[11px] text-ink-4 capitalize">{service.kind}</span>
              </TD>
              <TD>
                <Badge tone={serviceStatusTone(service.status)} dot pulse={service.status === 'UP'}>
                  {STATUS_LABEL[service.status] ?? service.status}
                </Badge>
              </TD>
              <TD numeric className="font-medium text-ink">
                {formatUptime(service.uptimePercent)}
              </TD>
              <TD numeric>{formatDuration(service.downtimeSeconds)}</TD>
              <TD numeric className={service.incidentCount > 0 ? 'text-warning-text' : undefined}>
                {service.incidentCount}
              </TD>
              <TD numeric>{formatMillis(service.responseTimeMs.p50)}</TD>
              <TD numeric>{formatMillis(service.responseTimeMs.p95)}</TD>
              <TD numeric>{formatMillis(service.responseTimeMs.p99)}</TD>
              <TD>
                <UptimeStrip
                  points={service.sparkline}
                  className="w-[168px]"
                  aria-label={`${service.name} availability`}
                />
              </TD>
              <TD>
                <button
                  type="button"
                  aria-expanded={open}
                  aria-controls={detailPanelId}
                  aria-label={`${open ? 'Hide' : 'Show'} detail for ${service.name}`}
                  onClick={(event) => {
                    event.stopPropagation()
                    onSelect(service.id)
                  }}
                  className="grid size-8 place-items-center rounded-md text-ink-4 transition-colors hover:bg-surface-3 hover:text-ink"
                >
                  <ChevronRight
                    aria-hidden
                    className={cn('size-4 transition-transform duration-150', open && 'rotate-90')}
                  />
                </button>
              </TD>
            </TR>
          )
        })}
      </TBody>
    </Table>
  )
}

/** The same rows as cards, for viewports too narrow for ten columns. */
export function ServiceCards({
  services,
  selectedId,
  onSelect,
  detailPanelId,
}: {
  services: ServiceUptime[]
  selectedId: string | null
  onSelect: (id: string) => void
  detailPanelId: string
}) {
  return (
    <ul className="space-y-2">
      {services.map((service) => {
        const open = selectedId === service.id
        return (
          <li key={service.id}>
            <button
              type="button"
              aria-expanded={open}
              aria-controls={detailPanelId}
              onClick={() => onSelect(service.id)}
              className={cn(
                'w-full rounded-xl border p-3 text-left transition-colors duration-150',
                open ? 'border-accent-border bg-accent-subtle' : 'border-line bg-surface hover:bg-surface-2',
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-[13.5px] font-medium text-ink">{service.name}</p>
                  <p className="text-[11px] text-ink-4 capitalize">{service.kind}</p>
                </div>
                <Badge tone={serviceStatusTone(service.status)} dot size="sm">
                  {STATUS_LABEL[service.status] ?? service.status}
                </Badge>
              </div>

              <dl className="mt-3 grid grid-cols-3 gap-2 text-[11.5px]">
                <div>
                  <dt className="text-ink-4">Uptime</dt>
                  <dd className="tnum font-medium text-ink">{formatUptime(service.uptimePercent)}</dd>
                </div>
                <div>
                  <dt className="text-ink-4">Downtime</dt>
                  <dd className="tnum font-medium text-ink">{formatDuration(service.downtimeSeconds)}</dd>
                </div>
                <div>
                  <dt className="text-ink-4">Incidents</dt>
                  <dd className="tnum font-medium text-ink">{service.incidentCount}</dd>
                </div>
                <div>
                  <dt className="text-ink-4">p50</dt>
                  <dd className="tnum font-medium text-ink">{formatMillis(service.responseTimeMs.p50)}</dd>
                </div>
                <div>
                  <dt className="text-ink-4">p95</dt>
                  <dd className="tnum font-medium text-ink">{formatMillis(service.responseTimeMs.p95)}</dd>
                </div>
                <div>
                  <dt className="text-ink-4">p99</dt>
                  <dd className="tnum font-medium text-ink">{formatMillis(service.responseTimeMs.p99)}</dd>
                </div>
              </dl>
            </button>

            <div className="mt-2 px-1">
              <UptimeStrip points={service.sparkline} aria-label={`${service.name} availability`} />
            </div>
          </li>
        )
      })}
    </ul>
  )
}
