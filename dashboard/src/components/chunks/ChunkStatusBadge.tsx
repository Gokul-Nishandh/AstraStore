import type { ChunkReplicationStatus } from '../../types/api'
import { Badge, replicationStatusTone } from '../ui/Badge'

/* Label and tone come from one place, so a chunk reading UNDER_REPLICATED is
   amber with the same words on the object page and the node drill-down. */
const labels: Record<ChunkReplicationStatus, string> = {
  PENDING: 'Pending',
  REPLICATING: 'Replicating',
  REPLICATED: 'Replicated',
  UNDER_REPLICATED: 'Under-replicated',
  REPAIRING: 'Repairing',
  FAILED: 'Failed',
  COMPLETE: 'Complete',
}

/** In-flight states pulse; settled ones do not. */
const live: ChunkReplicationStatus[] = ['REPLICATING', 'REPAIRING']

export function ChunkStatusBadge({
  status,
  size = 'sm',
}: {
  status: ChunkReplicationStatus
  size?: 'sm' | 'md'
}) {
  return (
    <Badge tone={replicationStatusTone(status)} size={size} dot pulse={live.includes(status)}>
      {labels[status] ?? status}
    </Badge>
  )
}
