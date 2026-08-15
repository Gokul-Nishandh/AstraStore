import type { ServiceUptime } from '../../types/api'
import { StatusDot, serviceStatusTone } from '../ui/Badge'
import { Skeleton } from '../ui/Skeleton'
import { formatUptime } from '../../lib/format'
import { cn } from '../../lib/cn'

const STATUS_WORD: Record<string, string> = {
  UP: 'operational',
  DOWN: 'down',
  DEGRADED: 'degraded',
  UNKNOWN: 'unknown',
}

/**
 * Every monitored service on one line — the "is anything on fire" glance.
 *
 * The uptime figure beside each name is whatever the window measured, and an
 * em dash when it measured nothing. A strip of green pills all reading 100%
 * on a cluster that started ten minutes ago is a lie told at a glance, which
 * is the worst kind.
 */
export function ServicesStrip({
  services,
  loading,
  selectedId,
  onSelect,
}: {
  services: ServiceUptime[] | null
  loading: boolean
  selectedId?: string | null
  onSelect?: (id: string) => void
}) {
  if (loading && !services) {
    return (
      <div className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-line bg-line sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
        {Array.from({ length: 6 }).map((_, index) => (
          <div key={index} className="bg-surface px-3 py-2.5">
            <Skeleton className="h-3 w-2/3" />
            <Skeleton className="mt-1.5 h-2.5 w-1/3" />
          </div>
        ))}
      </div>
    )
  }

  if (!services || services.length === 0) return null

  return (
    <div className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-line bg-line sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      {services.map((service) => {
        const tone = serviceStatusTone(service.status)
        const body = (
          <>
            <StatusDot tone={tone} pulse={service.status === 'UP'} className="mt-1.5" />
            <span className="min-w-0 leading-tight">
              <span className="block truncate text-[12.5px] font-medium text-ink">{service.name}</span>
              <span className="tnum block text-[11px] text-ink-4">
                {formatUptime(service.uptimePercent)} · {STATUS_WORD[service.status] ?? 'unknown'}
              </span>
            </span>
          </>
        )

        if (!onSelect) {
          return (
            <div key={service.id} className="flex min-h-11 items-start gap-2.5 bg-surface px-3 py-2.5">
              {body}
            </div>
          )
        }

        return (
          <button
            key={service.id}
            type="button"
            onClick={() => onSelect(service.id)}
            aria-pressed={selectedId === service.id}
            className={cn(
              'flex min-h-11 items-start gap-2.5 px-3 py-2.5 text-left transition-colors duration-150',
              selectedId === service.id ? 'bg-accent-subtle' : 'bg-surface hover:bg-surface-2',
            )}
          >
            {body}
          </button>
        )
      })}
    </div>
  )
}
