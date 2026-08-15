import { MoreVertical } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Menu, type MenuItem } from '../ui/Menu'
import { Table, THead, TBody, TR, TH, TD } from '../ui/Table'
import { FileTile } from './FileIcon'
import { StarButton } from './StarButton'
import { ReplicationBadge } from './ReplicationBadge'
import { objectFolder, objectName } from './helpers'
import type { StarState } from './useStarState'
import { formatBytes, formatDate, timeAgo } from '../../lib/format'
import { useMediaQuery } from '../../lib/hooks'
import { cn } from '../../lib/cn'
import type { ObjectRecord } from '../../types/api'

export interface ObjectListProps {
  objects: ObjectRecord[]
  /** Bucket id → name, for listings that span more than one bucket. */
  bucketNames?: Map<string, string>
  showBucket?: boolean
  /** Which timestamp the date column carries. */
  timestamp?: 'createdAt' | 'deletedAt'
  timestampLabel?: string
  /** Omit to hide the star column — trash rows cannot be starred. */
  star?: StarState
  starDisabledReason?: string
  showReplication?: boolean
  /**
   * Drops every column except name, size and the row controls.
   *
   * Tailwind breakpoints track the viewport, not the container. On a 1280px
   * screen the `lg:` columns appear even though the sidebar leaves the table
   * roughly 950px, which is what made a panel-width listing scroll sideways.
   * Panels opt into this; a full-width page does not.
   */
  compact?: boolean
  actions: (object: ObjectRecord) => MenuItem[]
  /** Detail route for a row. Return null for rows with no detail page. */
  href?: (object: ObjectRecord) => string | null
  /** Renders names struck through — used for objects awaiting purge. */
  struck?: boolean
  /** Screen-reader name for the table. */
  caption: string
  className?: string
}

/**
 * The one object listing.
 *
 * My Drive, Recent, Starred, Trash and the bucket explorer all render the
 * same `ObjectRecord`, so they all render this — which is what stops a file
 * from looking like a different kind of thing depending on which screen you
 * found it on. Below `sm` it becomes a card list rather than a table that
 * drags the page sideways.
 */
export function ObjectList({
  objects,
  bucketNames,
  showBucket = false,
  timestamp = 'createdAt',
  timestampLabel = 'Added',
  star,
  starDisabledReason,
  showReplication = true,
  compact = false,
  actions,
  href,
  struck = false,
  caption,
  className,
}: ObjectListProps) {
  const isWide = useMediaQuery('(min-width: 640px)')

  if (objects.length === 0) return null

  if (!isWide) {
    return (
      <ul className={cn('divide-y divide-line', className)}>
        {objects.map((object) => (
          <ObjectCard
            key={object.id}
            object={object}
            bucketName={bucketNames?.get(object.bucketId)}
            showBucket={showBucket}
            timestamp={timestamp}
            star={star}
            starDisabledReason={starDisabledReason}
            actions={actions}
            href={href}
            struck={struck}
          />
        ))}
      </ul>
    )
  }

  return (
    <Table caption={caption} className={className}>
      <THead>
        <TR interactive={false}>
          <TH className="w-full">Name</TH>
          {showBucket && !compact && <TH className="hidden lg:table-cell">Bucket</TH>}
          <TH numeric>Size</TH>
          {!compact && <TH className="hidden md:table-cell">{timestampLabel}</TH>}
          {showReplication && !compact && (
            <TH className="hidden lg:table-cell">Replication</TH>
          )}
          {star && <TH className="w-px"><span className="sr-only">Starred</span></TH>}
          <TH className="w-px"><span className="sr-only">Actions</span></TH>
        </TR>
      </THead>
      <TBody>
        {objects.map((object) => {
          const to = href?.(object) ?? null
          const name = objectName(object.key)
          const folder = objectFolder(object.key)
          const stamp = timestamp === 'deletedAt' ? object.deletedAt : object.createdAt

          return (
            <TR key={object.id}>
              <TD>
                <div className="flex min-w-0 items-center gap-3">
                  <FileTile contentType={object.contentType} objectKey={object.key} />
                  <div className="min-w-0">
                    {to ? (
                      <Link
                        to={to}
                        className={cn(
                          'block truncate text-[13.5px] font-medium text-ink transition-colors hover:text-accent-text',
                          struck && 'line-through',
                        )}
                      >
                        {name}
                      </Link>
                    ) : (
                      <span className={cn('block truncate text-[13.5px] font-medium text-ink', struck && 'line-through')}>
                        {name}
                      </span>
                    )}
                    {folder && <p className="truncate text-[11.5px] text-ink-4">{folder}/</p>}
                  </div>
                </div>
              </TD>

              {showBucket && !compact && (
                <TD className="hidden max-w-40 truncate text-[12.5px] text-ink-3 lg:table-cell">
                  {bucketNames?.get(object.bucketId) ?? '—'}
                </TD>
              )}

              <TD numeric className="whitespace-nowrap text-[12.5px]">
                {formatBytes(object.sizeBytes)}
              </TD>

              {!compact && (
                <TD className="hidden whitespace-nowrap text-[12.5px] text-ink-3 md:table-cell">
                  <span className="tnum">{formatDate(stamp)}</span>
                </TD>
              )}

              {showReplication && !compact && (
                <TD className="hidden lg:table-cell">
                  <ReplicationBadge object={object} size="sm" />
                </TD>
              )}

              {star && (
                <TD className="px-1">
                  <StarButton
                    starred={star.isStarred(object)}
                    busy={star.isBusy(object)}
                    onToggle={() => star.toggle(object)}
                    name={name}
                    disabledReason={starDisabledReason}
                  />
                </TD>
              )}

              <TD className="px-1">
                <RowMenu object={object} actions={actions} name={name} />
              </TD>
            </TR>
          )
        })}
      </TBody>
    </Table>
  )
}

function ObjectCard({
  object,
  bucketName,
  showBucket,
  timestamp,
  star,
  starDisabledReason,
  actions,
  href,
  struck,
}: {
  object: ObjectRecord
  bucketName?: string
  showBucket: boolean
  timestamp: 'createdAt' | 'deletedAt'
  star?: StarState
  starDisabledReason?: string
  actions: (object: ObjectRecord) => MenuItem[]
  href?: (object: ObjectRecord) => string | null
  struck: boolean
}) {
  const to = href?.(object) ?? null
  const name = objectName(object.key)
  const stamp = timestamp === 'deletedAt' ? object.deletedAt : object.createdAt

  const meta = [
    showBucket ? bucketName : null,
    formatBytes(object.sizeBytes),
    timestamp === 'deletedAt' ? `deleted ${timeAgo(stamp)}` : timeAgo(stamp),
  ].filter(Boolean)

  const body = (
    <div className="flex min-w-0 flex-1 items-center gap-3">
      <FileTile contentType={object.contentType} objectKey={object.key} />
      <div className="min-w-0">
        <p className={cn('truncate text-[13.5px] font-medium text-ink', struck && 'line-through')}>{name}</p>
        <p className="tnum truncate text-[11.5px] text-ink-4">{meta.join(' · ')}</p>
      </div>
    </div>
  )

  return (
    <li className="flex min-h-14 items-center gap-1 py-1.5">
      {to ? (
        <Link to={to} className="flex min-h-11 min-w-0 flex-1 items-center rounded-lg transition-colors hover:bg-surface-2">
          {body}
        </Link>
      ) : (
        <div className="flex min-h-11 min-w-0 flex-1 items-center">{body}</div>
      )}

      {star && (
        <StarButton
          starred={star.isStarred(object)}
          busy={star.isBusy(object)}
          onToggle={() => star.toggle(object)}
          name={name}
          disabledReason={starDisabledReason}
        />
      )}
      <RowMenu object={object} actions={actions} name={name} />
    </li>
  )
}

function RowMenu({
  object,
  actions,
  name,
}: {
  object: ObjectRecord
  actions: (object: ObjectRecord) => MenuItem[]
  name: string
}) {
  const items = actions(object)
  if (items.length === 0) return null

  return (
    <Menu
      items={items}
      trigger={(props) => (
        <button
          type="button"
          {...props}
          aria-label={`Actions for ${name}`}
          className="grid size-11 shrink-0 place-items-center rounded-md text-ink-4 transition-colors duration-150 hover:bg-surface-2 hover:text-ink sm:size-9"
        >
          <MoreVertical aria-hidden className="size-4" />
        </button>
      )}
    />
  )
}
