import { useEffect, useState } from 'react'
import { Boxes, ExternalLink } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { StorageNode } from '../../types/api'
import { Dialog } from '../ui/Dialog'
import { Badge } from '../ui/Badge'
import { Button } from '../ui/Button'
import { ErrorState } from '../ui/EmptyState'
import { Pagination } from '../ui/Pagination'
import { LoadingRegion, SkeletonRows } from '../ui/Skeleton'
import { Table, THead, TBody, TR, TH, TD, TableEmpty } from '../ui/Table'
import { nodeLabel, NodeStateBadge } from '../cluster/NodeStateBadge'
import { ChunkStatusBadge } from './ChunkStatusBadge'
import { useNodeChunks } from '../../lib/hooks'
import { formatBytes, formatCount, formatDate, timeAgo } from '../../lib/format'

const COLUMNS = 5
const PAGE_SIZE = 25

/**
 * Everything one node is holding.
 *
 * Two things this view deliberately does not do. It does not claim a chunk's
 * byte size — no per-chunk length is recorded anywhere, so the size column is
 * the whole object's and is labelled as such. And it does not hide a chunk
 * whose object has gone: those rows are the orphans left by a cleanup event
 * that never landed, and finding them is a reason to open this at all.
 */
export function NodeChunksDialog({ node, onClose }: { node: StorageNode | null; onClose: () => void }) {
  const [page, setPage] = useState(0)

  // A different node starts at its own first page rather than inheriting the
  // page number the previous one happened to be on.
  useEffect(() => setPage(0), [node?.nodeId])

  const chunks = useNodeChunks(node, page, PAGE_SIZE)
  const data = chunks.data

  return (
    <Dialog
      open={node !== null}
      onClose={onClose}
      size="xl"
      title={node ? `Chunks on ${nodeLabel(node.nodeId)}` : 'Chunks'}
      description={
        node ? (
          <span className="flex flex-wrap items-center gap-2">
            <NodeStateBadge state={node.state} />
            <span className="font-mono text-[11.5px] text-ink-4">{node.nodeId}</span>
          </span>
        ) : undefined
      }
      footer={
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      }
    >
      {chunks.error && !data ? (
        <ErrorState
          title="This node's chunks could not be loaded"
          description={chunks.error}
          onRetry={chunks.refresh}
        />
      ) : (
        <div className="space-y-3">
          <Body loading={chunks.loading} data={data} />

          {data && data.totalElements > 0 && (
            <Pagination
              page={data.number}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              pageSize={data.size}
              onChange={setPage}
            />
          )}

          {chunks.error && data && (
            <p className="text-[12.5px] text-warning-text">
              {chunks.error}{' '}
              <button
                type="button"
                onClick={chunks.refresh}
                className="font-medium underline underline-offset-2 hover:text-ink"
              >
                Try again
              </button>
            </p>
          )}
        </div>
      )}
    </Dialog>
  )
}

function Body({
  loading,
  data,
}: {
  loading: boolean
  data: ReturnType<typeof useNodeChunks>['data']
}) {
  const table = (
    <Table dense maxHeight="60vh" caption="Chunks held by this node, and the objects they belong to">
      <THead>
        <TR interactive={false}>
          <TH>Chunk</TH>
          <TH>Object</TH>
          <TH numeric>Object size</TH>
          <TH>Role</TH>
          <TH>Status</TH>
        </TR>
      </THead>
      <TBody>
        {loading && !data ? (
          <SkeletonRows rows={6} cols={COLUMNS} />
        ) : data && data.content.length > 0 ? (
          data.content.map((chunk) => (
            <TR key={chunk.id} interactive={false}>
              <TD className="whitespace-nowrap">
                <span className="tnum block font-medium text-ink">#{chunk.chunkIndex}</span>
                <span
                  className="block text-[11px] text-ink-4"
                  title={formatDate(chunk.createdAt)}
                >
                  {timeAgo(chunk.createdAt)}
                </span>
              </TD>
              <TD className="max-w-[22ch]">
                {chunk.objectKey ? (
                  <>
                    <Link
                      to={`/dashboard/objects/${chunk.objectId}`}
                      className="flex items-center gap-1.5 truncate font-medium text-ink hover:text-accent-text"
                      title={chunk.objectKey}
                    >
                      <span className="truncate">{chunk.objectKey}</span>
                      <ExternalLink aria-hidden className="size-3 shrink-0 text-ink-4" />
                    </Link>
                    <span className="block truncate text-[11px] text-ink-4">
                      {chunk.bucketName ?? '—'}
                    </span>
                  </>
                ) : (
                  // The chunk outlived its object. Reported, not hidden.
                  <>
                    <span className="block font-medium text-warning-text">Orphaned chunk</span>
                    <span className="block truncate font-mono text-[11px] text-ink-4">
                      {chunk.objectId}
                    </span>
                  </>
                )}
              </TD>
              <TD numeric className="whitespace-nowrap">
                {formatBytes(chunk.objectSizeBytes)}
              </TD>
              <TD>
                <Badge tone={chunk.role === 'PRIMARY' ? 'accent' : 'neutral'} size="sm">
                  {chunk.role === 'PRIMARY' ? 'Primary' : 'Replica'}
                </Badge>
                <span className="mt-1 block whitespace-nowrap text-[11px] text-ink-4">
                  {chunk.peerNodeId ? `peer ${nodeLabel(chunk.peerNodeId)}` : 'no peer yet'}
                </span>
              </TD>
              <TD>
                <ChunkStatusBadge status={chunk.replicationStatus} />
              </TD>
            </TR>
          ))
        ) : (
          <TableEmpty
            colSpan={COLUMNS}
            icon={<Boxes />}
            title="This node holds no chunks"
            description="Nothing has been placed here yet. A node that has just joined, or one excluded from placement, will look like this."
          />
        )}
      </TBody>
    </Table>
  )

  return (
    <>
      {data && (
        <p className="text-[12.5px] text-ink-3">
          <span className="tnum font-medium text-ink-2">{formatCount(data.totalElements)}</span>{' '}
          {data.totalElements === 1 ? 'chunk' : 'chunks'} held, counting both the copies this node
          is primary for and the ones it holds as a replica.
        </p>
      )}
      {loading && !data ? <LoadingRegion label="Loading chunks">{table}</LoadingRegion> : table}
    </>
  )
}
