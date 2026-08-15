import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import type { Incident } from '../../types/api'
import { Badge } from '../ui/Badge'
import { EmptyState } from '../ui/EmptyState'
import { formatDate, formatDuration, timeAgo } from '../../lib/format'
import { cn } from '../../lib/cn'

/**
 * The probe's own error text, capped.
 *
 * This is recorded telemetry an operator needs — "connection refused" is the
 * whole point of the row — not a thrown value being leaked to a user, so it
 * is shown rather than replaced. It is still bounded, because a stack trace
 * pasted into this field must not take over the panel.
 */
function probeError(text: string): string {
  const collapsed = text.replace(/\s+/g, ' ').trim()
  return collapsed.length > 180 ? `${collapsed.slice(0, 179)}…` : collapsed
}

export function IncidentRow({ incident, showService = true }: { incident: Incident; showService?: boolean }) {
  return (
    <li className="group relative flex gap-3 pb-5 last:pb-0">
      {/* The rail is decorative structure; the badge carries the state. */}
      <span aria-hidden className="absolute left-[7px] top-5 bottom-0 w-px bg-line group-last:hidden" />
      <span
        aria-hidden
        className={cn(
          'relative mt-1 grid size-4 shrink-0 place-items-center rounded-full border',
          incident.ongoing ? 'border-danger-border bg-danger-subtle' : 'border-line bg-surface-2',
        )}
      >
        <span
          className={cn(
            'size-1.5 rounded-full',
            incident.ongoing ? 'bg-danger motion-safe:animate-pulse-dot' : 'bg-ink-4',
          )}
        />
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          {showService && (
            <span className="text-[13px] font-medium text-ink">{incident.serviceName}</span>
          )}
          {incident.ongoing ? (
            <Badge tone="danger" size="sm" dot pulse>
              Ongoing
            </Badge>
          ) : (
            <Badge tone="neutral" size="sm">
              Resolved
            </Badge>
          )}
          <span className="tnum text-[11.5px] text-ink-4">
            {formatDuration(incident.durationSeconds)}
          </span>
        </div>

        <p className="tnum mt-1 text-[12px] text-ink-3">
          Started {formatDate(incident.startedAt)} · {timeAgo(incident.startedAt)}
          {incident.endedAt && ` · recovered ${formatDate(incident.endedAt)}`}
        </p>

        {incident.lastError && (
          <p className="mt-1.5 rounded-md bg-surface-2 px-2 py-1.5 font-mono text-[11px] leading-relaxed text-ink-3">
            <span className="mr-1.5 font-sans text-ink-4">Last probe error:</span>
            {probeError(incident.lastError)}
          </p>
        )}
      </div>
    </li>
  )
}

export function IncidentTimeline({
  incidents,
  showService = true,
  emptyTitle = 'No incidents in this window',
  emptyDescription = 'Nothing has gone down over the selected period. Widen the window to look further back.',
  className,
}: {
  incidents: Incident[]
  showService?: boolean
  emptyTitle?: string
  emptyDescription?: string
  className?: string
}) {
  if (incidents.length === 0) {
    return (
      <EmptyState
        size="sm"
        icon={<CheckCircle2 className="size-5" />}
        title={emptyTitle}
        description={emptyDescription}
        className={className}
      />
    )
  }

  // Ongoing first, then most recent — an outage happening now outranks one
  // that started later but has already recovered.
  const ordered = [...incidents].sort((a, b) => {
    if (a.ongoing !== b.ongoing) return a.ongoing ? -1 : 1
    return new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
  })

  return (
    <ul className={cn('relative', className)}>
      {ordered.map((incident) => (
        <IncidentRow key={incident.id} incident={incident} showService={showService} />
      ))}
    </ul>
  )
}

export function OngoingBanner({ incidents }: { incidents: Incident[] }) {
  const ongoing = incidents.filter((incident) => incident.ongoing)
  if (ongoing.length === 0) return null

  return (
    <div className="flex items-start gap-3 rounded-xl border border-danger-border bg-danger-subtle px-4 py-3">
      <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0 text-danger-text" />
      <div className="min-w-0">
        <p className="text-[13px] font-medium text-ink">
          {ongoing.length === 1
            ? `${ongoing[0].serviceName} is down right now`
            : `${ongoing.length} services are down right now`}
        </p>
        <p className="tnum mt-0.5 text-[12px] text-ink-2">
          {ongoing
            .map((incident) => `${incident.serviceName} · ${formatDuration(incident.durationSeconds)}`)
            .join('   ·   ')}
        </p>
      </div>
    </div>
  )
}
