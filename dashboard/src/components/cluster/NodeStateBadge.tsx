import type { NodeState } from '../../types/api'
import { Badge, nodeStateTone } from '../ui/Badge'

/* The label and the tone both come from one mapping, so a node reading
   DEGRADED is amber with the word "Degraded" on every screen. */
const labels: Record<NodeState, string> = {
  HEALTHY: 'Healthy',
  DEGRADED: 'Degraded',
  DOWN: 'Down',
  RECOVERING: 'Recovering',
}

/**
 * A node id as an operator says it out loud: `storage-node-1` reads as
 * "Node 1". Anything that does not match that shape is left alone rather
 * than mangled, so a differently-named node still shows its real id.
 *
 * A node answers to two names in this system and both arrive here. The
 * placement service reports `storage-node-1`; chunk placement rows carry
 * `http://storage-node-1:8088`, because the upload service records the
 * address the download service later fetches from. Both should read as
 * "Node 1" — an operator comparing the cluster table with a chunk listing
 * should not have to work out that they name the same machine.
 */
export function nodeLabel(nodeId: string): string {
  // The host of a base URL, or the whole string when it is a bare id.
  const host = /^[a-z]+:\/\/([^/:]+)/i.exec(nodeId)?.[1] ?? nodeId
  const match = /^(?:astra-)?(?:storage-)?node[-_]?(\d+)$/i.exec(host)
  return match ? `Node ${match[1]}` : nodeId
}

export function NodeStateBadge({ state }: { state: NodeState }) {
  return (
    <Badge tone={nodeStateTone(state)} dot pulse={state === 'RECOVERING'}>
      {labels[state] ?? state}
    </Badge>
  )
}
