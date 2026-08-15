import { useState } from 'react'
import { Activity, HardDrive, RefreshCw, Server, Wrench } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card, CardSection } from '../components/ui/Card'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { Skeleton, SkeletonTile } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/toast-context'
import { StatTile } from '../components/charts/StatTile'
import { CapacityPanel } from '../components/cluster/CapacityPanel'
import { NodeTable } from '../components/cluster/NodeTable'
import { NodeChunksDialog } from '../components/chunks/NodeChunksDialog'
import { api } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { formatBytes, formatCount, formatDate } from '../lib/format'
import { useClusterHealth, useReplicationStatus } from '../lib/hooks'
import type { StorageNode } from '../types/api'

/**
 * The node-level view.
 *
 * Overview answers "is the cluster healthy"; this page answers "which node,
 * and how full". Capacity here is always the node's configured quota — never
 * the host filesystem, because several nodes share one disk in a compose
 * deployment and summing host figures invents capacity that does not exist.
 */
export function ClusterHealthPage() {
  const { toast } = useToast()
  const cluster = useClusterHealth()
  const replication = useReplicationStatus()

  const [healing, setHealing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [inspecting, setInspecting] = useState<StorageNode | null>(null)

  const data = cluster.data
  const nodes = data?.nodes ?? []
  const noCapacityYet = !data || data.insufficientData

  const runHeal = async () => {
    setBusy(true)
    try {
      const result = await api.triggerHeal()
      toast(
        result.underReplicatedChunksFound > 0
          ? `Healing started for ${result.underReplicatedChunksFound} under-replicated chunks.`
          : 'Nothing needed healing — every chunk is fully replicated.',
        'success',
      )
      setHealing(false)
      replication.refresh()
      cluster.refresh()
    } catch (error) {
      toast(toUserMessage(error, 'The heal run could not be started.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Operations"
        title="Cluster"
        description="Storage nodes, their configured quotas, and how much of each is in use."
        meta={
          data && (
            <span className="text-[12px] text-ink-4">Checked {formatDate(data.checkedAt)}</span>
          )
        }
        actions={
          <>
            <Button variant="secondary" size="sm" icon={<RefreshCw />} onClick={cluster.refresh}>
              Refresh
            </Button>
            <Button size="sm" icon={<Wrench />} onClick={() => setHealing(true)}>
              Run heal
            </Button>
          </>
        }
      />

      {cluster.error && !data && (
        <ErrorState
          title="The placement service is unreachable"
          description={cluster.error}
          onRetry={cluster.refresh}
        />
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cluster.loading && !data ? (
          Array.from({ length: 4 }).map((_, i) => <SkeletonTile key={i} />)
        ) : (
          <>
            <StatTile
              label="Healthy nodes"
              icon={<Server />}
              value={data ? `${data.healthyNodes}/${data.totalNodes}` : '—'}
              detail={
                data && data.downNodes > 0
                  ? `${data.downNodes} down · ${data.degradedNodes} degraded`
                  : 'All nodes reporting'
              }
            />
            <StatTile
              label="Eligible for placement"
              icon={<Activity />}
              value={data ? formatCount(data.eligibleNodes) : '—'}
              detail="Nodes accepting new chunks"
            />
            <StatTile
              label="Cluster capacity"
              icon={<HardDrive />}
              value={noCapacityYet ? '—' : formatBytes(data.totalCapacityBytes)}
              detail={
                noCapacityYet
                  ? 'No node has reported a quota yet'
                  : `${data.reportingNodes} of ${data.totalNodes} nodes reporting`
              }
            />
            <StatTile
              label="Under-replicated"
              icon={<Wrench />}
              value={formatCount(replication.data?.underReplicatedChunks)}
              detail={
                replication.data
                  ? `of ${formatCount(replication.data.totalTrackedChunks)} tracked chunks`
                  : 'Awaiting replication data'
              }
            />
          </>
        )}
      </div>

      <CardSection
        icon={<HardDrive />}
        title="Capacity"
        description="Every figure is a sum of quotas nodes reported about themselves."
      >
        {data ? <CapacityPanel cluster={data} /> : null}
      </CardSection>

      <Card padded={false}>
        <div className="border-b border-line p-4">
          <h2 className="font-display text-[15px] font-semibold tracking-tight text-ink">
            Storage nodes
          </h2>
          <p className="mt-0.5 text-[12.5px] text-ink-3">
            Each node reports its own quota and usage on every heartbeat. Select a node to see the
            chunks it holds.
          </p>
        </div>

        {cluster.loading && !data ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : nodes.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={<Server />}
              size="sm"
              title="No storage nodes registered"
              description="The placement service has not seen a heartbeat from any node yet."
            />
          </div>
        ) : (
          <div className="scroll-x">
            {/* This whole page is behind AdminGuard, so every reader here
                already holds the role the chunk endpoint requires. */}
            <NodeTable nodes={nodes} onSelect={setInspecting} />
          </div>
        )}
      </Card>

      <NodeChunksDialog node={inspecting} onClose={() => setInspecting(null)} />

      <ConfirmDialog
        open={healing}
        onClose={() => setHealing(false)}
        onConfirm={runHeal}
        loading={busy}
        title="Run a heal pass?"
        confirmLabel="Run heal"
        body="This scans for under-replicated chunks and copies them to additional nodes. It is safe to run at any time, but it will use network and disk while it works."
      />
    </div>
  )
}
