import { type ReactNode } from 'react'
import type { ClusterStatus, MonitoringSummary, ReplicationStatus } from '../../types/api'
import { Skeleton } from '../ui/Skeleton'
import { StatusDot, type Tone } from '../ui/Badge'
import { formatBytes, formatCount, formatUptime } from '../../lib/format'
import { cn } from '../../lib/cn'

function Cell({
  label,
  value,
  detail,
  tone,
  loading,
}: {
  label: string
  value: ReactNode
  detail?: ReactNode
  /** Only for figures that genuinely carry state. Never decoration. */
  tone?: Tone
  loading?: boolean
}) {
  return (
    <div className="min-w-0 bg-surface px-4 py-3.5">
      <p className="flex items-center gap-1.5 text-[11px] font-medium text-ink-4">
        {tone && <StatusDot tone={tone} />}
        {label}
      </p>
      {loading ? (
        <>
          <Skeleton className="mt-2 h-6 w-20" />
          <Skeleton className="mt-2 h-2.5 w-24" />
        </>
      ) : (
        <>
          <p className="tnum mt-1.5 truncate font-sans text-[22px] leading-none font-semibold tracking-tight text-ink">
            {value}
          </p>
          {detail && <p className="mt-1.5 truncate text-[11.5px] text-ink-3">{detail}</p>}
        </>
      )}
    </div>
  )
}

/**
 * The six figures an operator checks first.
 *
 * Every one of them can be "we don't know yet" and says so. `insufficientData`
 * on the monitoring summary and on the cluster status are honoured
 * separately, because monitoring can have plenty of samples while no node has
 * yet reported a quota — and vice versa.
 */
export function HealthBand({
  summary,
  cluster,
  replication,
  windowLabel,
  loading,
  className,
}: {
  summary: MonitoringSummary | null
  cluster: ClusterStatus | null
  replication: ReplicationStatus | null
  windowLabel: string
  loading: boolean
  className?: string
}) {
  const servicesTone: Tone | undefined = !summary
    ? undefined
    : summary.servicesDown > 0
      ? 'danger'
      : summary.servicesDegraded > 0
        ? 'warning'
        : 'success'

  const nodesTone: Tone | undefined = !cluster
    ? undefined
    : cluster.downNodes > 0
      ? 'danger'
      : cluster.degradedNodes > 0 || cluster.recoveringNodes > 0
        ? 'warning'
        : 'success'

  const underReplicated = replication?.underReplicatedChunks ?? null

  return (
    <div
      className={cn(
        'grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-line bg-line',
        'sm:grid-cols-3 xl:grid-cols-6',
        className,
      )}
    >
      <Cell
        label="Services up"
        tone={servicesTone}
        loading={loading && !summary}
        value={summary ? `${summary.servicesUp}/${summary.servicesTotal}` : '—'}
        detail={
          summary
            ? `${summary.servicesDegraded} degraded · ${summary.servicesDown} down`
            : 'contacting monitoring'
        }
      />

      <Cell
        label="Cluster uptime"
        loading={loading && !summary}
        value={summary?.insufficientData ? '—' : formatUptime(summary?.clusterUptimePercent)}
        detail={
          summary?.insufficientData
            ? 'not enough samples yet'
            : summary
              ? windowLabel.toLowerCase()
              : undefined
        }
      />

      <Cell
        label="Open incidents"
        tone={summary && summary.openIncidents > 0 ? 'danger' : undefined}
        loading={loading && !summary}
        value={summary ? formatCount(summary.openIncidents) : '—'}
        detail={summary ? `${formatCount(summary.incidentsInWindow)} in window` : undefined}
      />

      <Cell
        label="Nodes healthy"
        tone={nodesTone}
        loading={loading && !cluster}
        value={cluster ? `${cluster.healthyNodes}/${cluster.totalNodes}` : '—'}
        detail={cluster ? `${cluster.eligibleNodes} eligible for placement` : undefined}
      />

      <Cell
        label="Raw stored"
        loading={loading && !cluster}
        value={formatBytes(cluster?.rawBytesStored)}
        detail={
          cluster?.insufficientData
            ? 'no node has reported a quota'
            : cluster
              ? `of ${formatBytes(cluster.totalCapacityBytes)} reported`
              : undefined
        }
      />

      <Cell
        label="Under-replicated"
        tone={underReplicated !== null && underReplicated > 0 ? 'warning' : undefined}
        loading={loading && !replication}
        value={formatCount(underReplicated)}
        detail={
          replication
            ? `of ${formatCount(replication.totalTrackedChunks)} chunks tracked`
            : undefined
        }
      />
    </div>
  )
}
