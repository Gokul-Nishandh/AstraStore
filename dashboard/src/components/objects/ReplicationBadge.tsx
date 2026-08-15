import { Badge } from '../ui/Badge'
import { formatCount } from '../../lib/format'
import type { ObjectRecord } from '../../types/api'

/**
 * Durability for one object, stated honestly.
 *
 * A zero chunk total means the metadata service has not recorded placements
 * yet — that is "awaiting data", not "0% replicated". Claiming either full
 * durability or total loss from an absent figure would be a lie in both
 * directions.
 */
export function ReplicationBadge({
  object,
  size = 'md',
}: {
  object: Pick<ObjectRecord, 'chunksReplicated' | 'chunksTotal'>
  size?: 'sm' | 'md'
}) {
  const { chunksReplicated: replicated, chunksTotal: total } = object

  if (total <= 0) {
    return (
      <Badge tone="neutral" size={size} dot>
        Awaiting data
      </Badge>
    )
  }

  if (replicated >= total) {
    return (
      <Badge tone="success" size={size} dot>
        Replicated
      </Badge>
    )
  }

  return (
    <Badge tone="warning" size={size} dot>
      <span className="tnum">
        {formatCount(replicated)}/{formatCount(total)} chunks
      </span>
    </Badge>
  )
}
