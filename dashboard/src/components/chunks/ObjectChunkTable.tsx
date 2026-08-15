import { Boxes } from 'lucide-react'
import type { ChunkPlacement } from '../../types/api'
import { Table, THead, TBody, TR, TH, TD, TableEmpty } from '../ui/Table'
import { ErrorState } from '../ui/EmptyState'
import { LoadingRegion, SkeletonRows } from '../ui/Skeleton'
import { nodeLabel } from '../cluster/NodeStateBadge'
import { ChunkStatusBadge } from './ChunkStatusBadge'
import { formatDate, timeAgo } from '../../lib/format'

const COLUMNS = 5

export interface ObjectChunkTableProps {
  chunks: ChunkPlacement[] | null
  loading: boolean
  error: string | null
  onRetry: () => void
}

/**
 * Where each chunk of one object actually lives.
 *
 * The object header says "2 of 2 chunks replicated"; this says which two
 * nodes, which is the question an operator asks next when a node goes down.
 *
 * A chunk with no replica node yet shows an em dash rather than repeating the
 * primary — the second copy has not been placed, and drawing it as though it
 * had would misreport durability in the one view that exists to report it.
 */
export function ObjectChunkTable({ chunks, loading, error, onRetry }: ObjectChunkTableProps) {
  // A first load that failed has nothing to show beside the error; a failed
  // refresh does, and is reported below the table instead.
  if (error && !chunks) {
    return (
      <ErrorState title="Chunk placement could not be loaded" description={error} onRetry={onRetry} />
    )
  }

  const table = (
    <Table dense caption="Each chunk of this object, and the nodes holding it">
      <THead>
        <TR interactive={false}>
          <TH>Chunk</TH>
          <TH>Primary</TH>
          <TH>Replica</TH>
          <TH>Status</TH>
          <TH>Recorded</TH>
        </TR>
      </THead>
      <TBody>
        {loading && !chunks ? (
          <SkeletonRows rows={2} cols={COLUMNS} />
        ) : chunks && chunks.length > 0 ? (
          chunks.map((chunk) => (
            <TR key={chunk.id} interactive={false}>
              <TD className="tnum whitespace-nowrap font-medium text-ink">#{chunk.chunkIndex}</TD>
              <TD className="whitespace-nowrap font-medium text-ink">{nodeLabel(chunk.nodeId)}</TD>
              <TD className="whitespace-nowrap">
                {chunk.replicaNodeId ? (
                  nodeLabel(chunk.replicaNodeId)
                ) : (
                  <span className="text-ink-4" title="No second copy has been placed yet">
                    —
                  </span>
                )}
              </TD>
              <TD>
                <ChunkStatusBadge status={chunk.replicationStatus} />
              </TD>
              <TD className="whitespace-nowrap text-ink-3" title={formatDate(chunk.createdAt)}>
                {timeAgo(chunk.createdAt)}
              </TD>
            </TR>
          ))
        ) : (
          <TableEmpty
            colSpan={COLUMNS}
            icon={<Boxes />}
            title="No chunks recorded yet"
            description="The metadata service has not indexed a placement for this object. That is normal for a few seconds after an upload, and worth investigating after that."
          />
        )}
      </TBody>
    </Table>
  )

  return (
    <div className="space-y-3">
      {loading && !chunks ? (
        <LoadingRegion label="Loading chunk placement">{table}</LoadingRegion>
      ) : (
        table
      )}

      {/* A failed refresh keeps the last good rows on screen — blanking a
          panel because one poll timed out is worse than showing it stale. */}
      {error && chunks && (
        <p className="text-[12.5px] text-warning-text">
          {error}{' '}
          <button
            type="button"
            onClick={onRetry}
            className="font-medium underline underline-offset-2 hover:text-ink"
          >
            Try again
          </button>
        </p>
      )}
    </div>
  )
}
