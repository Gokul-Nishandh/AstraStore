import { type ReactNode } from 'react'
import { Database, Layers } from 'lucide-react'
import type { ClusterStatus } from '../../types/api'
import { Card, CardDescription, CardTitle } from '../ui/Card'
import { Badge } from '../ui/Badge'
import { Tooltip } from '../ui/Tooltip'
import { StackedBar } from '../charts/StackedBar'
import { formatBytes, formatCount, formatPercent } from '../../lib/format'

function Figure({
  label,
  value,
  hint,
  badge,
}: {
  label: string
  value: string
  hint?: string
  badge?: ReactNode
}) {
  return (
    <div className="min-w-0">
      <p className="flex items-center gap-1.5 text-[11px] font-medium text-ink-4">
        {label}
        {badge}
      </p>
      <p className="tnum mt-1 truncate text-[15px] font-semibold text-ink">{value}</p>
      {hint && <p className="mt-0.5 truncate text-[11px] text-ink-4">{hint}</p>}
    </div>
  )
}

/**
 * Cluster capacity, told twice.
 *
 * `raw` is what sits on disk; `logical` is what users uploaded. At a
 * replication factor of 2 a 1 GB object occupies 2 GB, so the two answer
 * different questions and are drawn as two bars rather than two axes on one.
 *
 * When no node has reported a quota the whole panel says so. It does not fall
 * back to host filesystem sizes — summing those across containers sharing a
 * disk is exactly what produced the phantom "10 TB across 3 nodes".
 */
export function CapacityPanel({ cluster }: { cluster: ClusterStatus }) {
  const {
    insufficientData,
    reportingNodes,
    totalNodes,
    totalCapacityBytes,
    usedBytes,
    availableBytes,
    usedRatio,
    totalChunkCount,
    replicationFactor,
    rawBytesStored,
    logicalBytesStored,
    logicalBytesAvailable,
    replicationOverheadBytes,
    logicalBytesIsEstimate,
  } = cluster

  if (insufficientData) {
    return (
      <Card>
        <CardTitle>Cluster capacity</CardTitle>
        <CardDescription>
          Physical bytes on disk and the logical bytes users uploaded.
        </CardDescription>
        <div className="mt-4 rounded-lg border border-dashed border-line-strong bg-surface-2 px-4 py-8 text-center">
          <p className="font-display text-sm font-semibold text-ink">Awaiting capacity reports</p>
          <p className="mx-auto mt-1.5 max-w-sm text-pretty text-[13px] leading-relaxed text-ink-3">
            {reportingNodes} of {totalNodes} nodes have told us their storage quota. Until at least
            one does, the cluster does not know how much space it has — and will not guess.
          </p>
        </div>
      </Card>
    )
  }

  const logicalTotal =
    logicalBytesStored !== null && logicalBytesAvailable !== null
      ? logicalBytesStored + logicalBytesAvailable
      : null

  return (
    <Card>
      <CardTitle>Cluster capacity</CardTitle>
      <CardDescription>
        Summed from the quotas nodes reported about themselves — {reportingNodes} of {totalNodes}{' '}
        reporting.
      </CardDescription>

      <section className="mt-5">
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h4 className="flex items-center gap-1.5 text-[12.5px] font-semibold text-ink">
            <Database aria-hidden className="size-3.5 text-ink-4" />
            Physical — raw bytes on disk
          </h4>
          <p className="tnum text-[11.5px] text-ink-3">
            {formatPercent(usedRatio)} of {formatBytes(totalCapacityBytes)}
          </p>
        </div>

        <StackedBar
          segments={[
            { id: 'logical', label: 'User data', value: logicalBytesStored },
            { id: 'overhead', label: `Replication copies (×${replicationFactor})`, value: replicationOverheadBytes },
          ]}
          total={totalCapacityBytes}
          remainderLabel="Free"
          formatValue={formatBytes}
          aria-label="Raw cluster capacity by composition"
        />

        <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Figure label="Raw stored" value={formatBytes(rawBytesStored)} hint="physically on disk" />
          <Figure
            label="Replication overhead"
            value={formatBytes(replicationOverheadBytes)}
            hint={`factor ×${replicationFactor}`}
          />
          <Figure label="Used" value={formatBytes(usedBytes)} hint="reported by nodes" />
          <Figure label="Available" value={formatBytes(availableBytes)} hint="within reported quota" />
        </div>
      </section>

      <section className="mt-6 border-t border-line pt-5">
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h4 className="flex items-center gap-1.5 text-[12.5px] font-semibold text-ink">
            <Layers aria-hidden className="size-3.5 text-ink-4" />
            Logical — bytes users uploaded
          </h4>
          {logicalBytesIsEstimate && (
            <Tooltip content={`Derived from raw bytes divided by the replication factor of ${replicationFactor}, not counted object by object.`}>
              <Badge tone="neutral" size="sm">
                Estimated
              </Badge>
            </Tooltip>
          )}
        </div>

        <StackedBar
          segments={[{ id: 'stored', label: 'Uploaded', value: logicalBytesStored }]}
          total={logicalTotal}
          remainderLabel="Room for more"
          formatValue={formatBytes}
          aria-label="Logical bytes uploaded against logical space remaining"
        />

        <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Figure
            label="Logical stored"
            value={formatBytes(logicalBytesStored)}
            hint="what users uploaded"
            badge={logicalBytesIsEstimate ? <span className="text-ink-4">(est.)</span> : undefined}
          />
          <Figure
            label="Logical headroom"
            value={formatBytes(logicalBytesAvailable)}
            hint={`before ×${replicationFactor} expansion`}
          />
          <Figure label="Chunks tracked" value={formatCount(totalChunkCount)} hint="across all nodes" />
          <Figure label="Replication factor" value={`×${replicationFactor}`} hint="copies per chunk" />
        </div>
      </section>
    </Card>
  )
}
