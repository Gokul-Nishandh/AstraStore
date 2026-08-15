import {
  createContext,
  useContext,
  type HTMLAttributes,
  type ReactNode,
  type TdHTMLAttributes,
  type ThHTMLAttributes,
} from 'react'
import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react'
import { cn } from '../../lib/cn'
import { EmptyState, type EmptyStateProps } from './EmptyState'

export type SortDirection = 'asc' | 'desc'

/** Density is set once on <Table> and read by every cell, so a dense table
 *  cannot end up with half its rows at the default height. */
const DensityContext = createContext(false)

export interface TableProps extends HTMLAttributes<HTMLTableElement> {
  /** Tighter rows for log-style data — audit entries, replication rows. */
  dense?: boolean
  /** Applied to the scrolling wrapper rather than the <table>. */
  containerClassName?: string
  /**
   * Makes the wrapper vertically scrollable, which is what lets the sticky
   * header actually pin. Without it the header still sticks, but the wrapper
   * has no vertical scroll to pin against.
   */
  maxHeight?: number | string
  /** Visually hidden <caption>. Give every table one — screen reader users
   *  navigating by table need to know which one they landed in. */
  caption?: ReactNode
}

/**
 * The wrapper owns the horizontal overflow (`.scroll-x`), never the page.
 * A wide table that pushes the document sideways is the single most common
 * way a dashboard layout breaks on a narrow viewport.
 */
export function Table({
  className,
  containerClassName,
  dense = false,
  maxHeight,
  caption,
  children,
  ...props
}: TableProps) {
  return (
    <DensityContext.Provider value={dense}>
      <div
        className={cn('scroll-x w-full', maxHeight != null && 'overflow-y-auto', containerClassName)}
        style={maxHeight != null ? { maxHeight } : undefined}
      >
        <table
          className={cn('w-full border-collapse text-left text-sm', className)}
          {...props}
        >
          {caption && <caption className="sr-only">{caption}</caption>}
          {children}
        </table>
      </div>
    </DensityContext.Provider>
  )
}

export interface THeadProps extends HTMLAttributes<HTMLTableSectionElement> {
  /** Pin the header while the body scrolls. On by default. */
  sticky?: boolean
}

export function THead({ className, sticky = true, ...props }: THeadProps) {
  return (
    <thead
      className={cn(
        'border-b border-line bg-surface-2',
        // Opaque, not translucent: rows scrolling underneath a see-through
        // header is what made the old table unreadable mid-scroll.
        sticky && 'sticky top-0 z-10',
        className,
      )}
      {...props}
    />
  )
}

export function TBody({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={className} {...props} />
}

export interface THProps extends Omit<ThHTMLAttributes<HTMLTableCellElement>, 'onClick'> {
  /** Right-aligns the column. Pair with `numeric` on the matching cells. */
  numeric?: boolean
  /** Turns the label into a sort control. Requires `onSort`. */
  sortable?: boolean
  /** Current direction, or null/undefined when this column is not the sort key. */
  sortDirection?: SortDirection | null
  /** Called with the direction the user is asking for. */
  onSort?: (next: SortDirection) => void
}

export function TH({
  className,
  numeric = false,
  sortable = false,
  sortDirection = null,
  onSort,
  children,
  ...props
}: THProps) {
  const dense = useContext(DensityContext)

  const ariaSort: ThHTMLAttributes<HTMLTableCellElement>['aria-sort'] = sortable
    ? sortDirection === 'asc'
      ? 'ascending'
      : sortDirection === 'desc'
        ? 'descending'
        : 'none'
    : undefined

  const label = (
    <span className="inline-flex items-center gap-1.5">
      <span className="truncate">{children}</span>
      {sortable && <SortGlyph direction={sortDirection} />}
    </span>
  )

  return (
    <th
      scope="col"
      aria-sort={ariaSort}
      className={cn(
        'whitespace-nowrap text-[11px] font-semibold uppercase tracking-wide text-ink-3',
        dense ? 'px-3 py-2' : 'px-4 py-2.5',
        numeric && 'text-right',
        sortable && 'p-0',
        className,
      )}
      {...props}
    >
      {sortable && onSort ? (
        <button
          type="button"
          onClick={() => onSort(sortDirection === 'asc' ? 'desc' : 'asc')}
          className={cn(
            'flex w-full items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide',
            'transition-colors duration-150 ease-[var(--ease-out)] hover:text-ink',
            sortDirection ? 'text-ink' : 'text-ink-3',
            dense ? 'px-3 py-2' : 'px-4 py-2.5',
            numeric && 'justify-end',
          )}
        >
          {label}
        </button>
      ) : (
        label
      )}
    </th>
  )
}

/** Always-visible affordance: an un-sorted column shows the neutral glyph so
 *  it is discoverable without hovering, which is invisible on touch. */
function SortGlyph({ direction }: { direction: SortDirection | null }) {
  const Icon = direction === 'asc' ? ArrowUp : direction === 'desc' ? ArrowDown : ChevronsUpDown
  return <Icon aria-hidden className={cn('size-3 shrink-0', direction ? 'text-accent-text' : 'text-ink-4')} />
}

export interface TRProps extends HTMLAttributes<HTMLTableRowElement> {
  /** Marks the row as the current selection / open detail. */
  selected?: boolean
  /** Suppresses the hover tint on rows that are not clickable targets. */
  interactive?: boolean
}

/**
 * No zebra striping — alternating fills fight with the status colours in the
 * cells. A single clear hover tint does the row-tracking job instead.
 */
export function TR({ className, selected = false, interactive = true, ...props }: TRProps) {
  return (
    <tr
      aria-selected={selected || undefined}
      className={cn(
        'border-b border-line transition-colors duration-150 ease-[var(--ease-out)] last:border-b-0',
        interactive && 'hover:bg-surface-2',
        selected && 'bg-accent-subtle hover:bg-accent-subtle',
        className,
      )}
      {...props}
    />
  )
}

export interface TDProps extends TdHTMLAttributes<HTMLTableCellElement> {
  /** Right-aligns and applies tabular numerals. Use for every count, size
   *  and duration column so digits line up down the column. */
  numeric?: boolean
  /** Monospace — for IDs, checksums and hashes. */
  mono?: boolean
}

export function TD({ className, numeric = false, mono = false, ...props }: TDProps) {
  const dense = useContext(DensityContext)
  return (
    <td
      className={cn(
        'align-middle text-ink-2',
        dense ? 'px-3 py-2' : 'px-4 py-3',
        numeric && 'tnum text-right',
        mono && 'font-mono text-[12.5px]',
        className,
      )}
      {...props}
    />
  )
}

export interface TableEmptyProps extends EmptyStateProps {
  /** Must match the column count, or the message will not span the table. */
  colSpan: number
}

/**
 * An empty table body still needs to be a table row, or the layout collapses.
 * This renders a real EmptyState inside a spanning cell so "no results" looks
 * like the rest of the product rather than a blank rectangle.
 */
export function TableEmpty({ colSpan, className, size = 'sm', ...props }: TableEmptyProps) {
  return (
    <tr>
      <td colSpan={colSpan} className="p-0">
        <EmptyState {...props} size={size} className={cn('border-0 bg-transparent', className)} />
      </td>
    </tr>
  )
}
