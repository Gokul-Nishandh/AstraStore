import { ChevronRight, Info } from 'lucide-react'
import type { StorageNode } from '../../types/api'
import { Table, THead, TBody, TR, TH, TD } from '../ui/Table'
import { Badge } from '../ui/Badge'
import { Tooltip } from '../ui/Tooltip'
import { Meter } from '../charts/Meter'
import { NodeStateBadge } from './NodeStateBadge'
import { nodeLabel } from './NodeStateBadge'
import { formatBytes, formatCount, formatDate, timeAgo } from '../../lib/format'

export interface NodeTableProps {
  nodes: StorageNode[]
  /**
   * Opens the node's chunk drill-down. Omitted for a caller who must not have
   * it — the listing endpoint behind it is admin-only — and the rows then stay
   * inert rather than offering an action that would be refused.
   */
  onSelect?: (node: StorageNode) => void
}

/**
 * Every node, every field the placement service publishes about it.
 *
 * Capacity is drawn as a meter *and* written out in bytes: the bar answers
 * "how full" at a glance, the figures answer "how much", and a node that has
 * never reported a quota gets the meter's hatched track rather than an empty
 * bar that would read as plenty of room.
 *
 * When `onSelect` is given the whole row is a click target, which is what
 * makes the drill-down discoverable. The real control is the button in the
 * node cell: it carries the accessible name, takes keyboard focus, and meets
 * the 44px touch minimum. The row handler is a convenience layered on top of
 * it, not a replacement for it.
 */
export function NodeTable({ nodes, onSelect }: NodeTableProps) {
  return (
    <Table dense caption="Storage nodes, their state and their reported capacity">
      <THead>
        <TR interactive={false}>
          <TH>Node</TH>
          <TH>State</TH>
          <TH>Placement</TH>
          <TH className="min-w-[190px]">Quota used</TH>
          <TH numeric>Available</TH>
          <TH numeric>Chunks</TH>
          <TH numeric>
            <Tooltip content="Free space on the host filesystem. Advisory only — nodes sharing a host all report the same figure, so it must never be summed.">
              <span className="inline-flex items-center gap-1">
                Host disk
                <Info aria-hidden className="size-3 text-ink-4" />
              </span>
            </Tooltip>
          </TH>
          <TH>Last seen</TH>
          <TH numeric>Failures</TH>
          {onSelect && (
            <TH>
              {/* Not "Chunks" — there is already a Chunks column, and two
                  identically named headers is a maze for anyone navigating
                  the table by column. */}
              <span className="sr-only">Open chunk listing</span>
            </TH>
          )}
        </TR>
      </THead>
      <TBody>
        {nodes.map((node) => (
          <TR
            key={node.nodeId}
            interactive={Boolean(onSelect)}
            className={onSelect ? 'cursor-pointer' : undefined}
            onClick={onSelect ? () => onSelect(node) : undefined}
          >
            <TD className="whitespace-nowrap">
              {onSelect ? (
                <button
                  type="button"
                  // The row's handler fires on bubble; without this the click
                  // would open the drill-down twice.
                  onClick={(e) => {
                    e.stopPropagation()
                    onSelect(node)
                  }}
                  className="flex min-h-11 flex-col justify-center rounded-md text-left transition-colors hover:text-accent-text"
                  aria-label={`View chunks on ${nodeLabel(node.nodeId)}`}
                >
                  <span className="block font-medium text-ink">{nodeLabel(node.nodeId)}</span>
                  <span className="block font-mono text-[11px] text-ink-4" title={node.baseUrl}>
                    {node.baseUrl}
                  </span>
                </button>
              ) : (
                <>
                  <span className="block font-medium text-ink">{nodeLabel(node.nodeId)}</span>
                  <span className="block font-mono text-[11px] text-ink-4" title={node.baseUrl}>
                    {node.baseUrl}
                  </span>
                </>
              )}
            </TD>
            <TD>
              <NodeStateBadge state={node.state} />
            </TD>
            <TD>
              {node.eligibleForPlacement ? (
                <Badge tone="neutral" size="sm">
                  Eligible
                </Badge>
              ) : (
                <Badge tone="warning" size="sm" dot>
                  Excluded
                </Badge>
              )}
            </TD>
            <TD>
              <Meter
                ratio={node.usedRatio}
                size="sm"
                aria-label={`${nodeLabel(node.nodeId)} quota used`}
              />
              <p className="tnum mt-1.5 text-[11.5px] text-ink-3">
                {node.capacityReported ? (
                  <>
                    {formatBytes(node.usedBytes)} of {formatBytes(node.capacityBytes)}
                  </>
                ) : (
                  'Quota not reported yet'
                )}
              </p>
            </TD>
            <TD numeric className="whitespace-nowrap">
              {formatBytes(node.availableBytes)}
            </TD>
            <TD numeric>{formatCount(node.chunkCount)}</TD>
            <TD numeric className="whitespace-nowrap text-ink-3">
              {formatBytes(node.hostDiskFreeBytes)}
            </TD>
            <TD className="whitespace-nowrap">
              <span className="block text-ink-2">{node.lastSeen ? timeAgo(node.lastSeen) : 'never'}</span>
              <span className="block text-[11px] text-ink-4">
                checked {node.lastChecked ? timeAgo(node.lastChecked) : '—'}
              </span>
            </TD>
            <TD
              numeric
              className={
                node.consecutiveFailures > 2
                  ? 'text-danger-text'
                  : node.consecutiveFailures > 0
                    ? 'text-warning-text'
                    : undefined
              }
              title={node.lastChecked ? `Last probe ${formatDate(node.lastChecked)}` : undefined}
            >
              {formatCount(node.consecutiveFailures)}
            </TD>
            {onSelect && (
              /* A visible affordance for the row click. Hover alone does not
                 exist on touch, so the chevron is always drawn. */
              <TD className="w-8 text-right">
                <ChevronRight aria-hidden className="inline size-4 text-ink-4" />
              </TD>
            )}
          </TR>
        ))}
      </TBody>
    </Table>
  )
}
