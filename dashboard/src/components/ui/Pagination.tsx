import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'
import { cn } from '../../lib/cn'

export interface PaginationProps {
  /** Zero-based, matching the backend. */
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onChange: (page: number) => void
  className?: string
}

/**
 * Prev/next with a spoken range rather than a numbered page list.
 *
 * The audit trail runs to tens of thousands of rows, where a numbered list
 * is both unusable and expensive to render. "1–50 of 12,480" answers the
 * question a page number is a proxy for, and it stays one line on a phone.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onChange,
  className,
}: PaginationProps) {
  if (totalElements === 0) return null

  const first = page * pageSize + 1
  const last = Math.min((page + 1) * pageSize, totalElements)

  return (
    <div
      className={cn(
        'flex flex-wrap items-center justify-between gap-3 border-t border-line px-1 pt-3',
        className,
      )}
    >
      <p className="text-[12.5px] text-ink-3">
        <span className="tnum font-medium text-ink-2">
          {first.toLocaleString()}–{last.toLocaleString()}
        </span>{' '}
        of <span className="tnum font-medium text-ink-2">{totalElements.toLocaleString()}</span>
      </p>

      <div className="flex items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          disabled={page <= 0}
          onClick={() => onChange(page - 1)}
          icon={<ChevronLeft />}
        >
          Previous
        </Button>
        <span className="tnum hidden text-[12.5px] text-ink-3 sm:inline">
          Page {page + 1} of {Math.max(1, totalPages)}
        </span>
        <Button
          variant="secondary"
          size="sm"
          disabled={page >= totalPages - 1}
          onClick={() => onChange(page + 1)}
        >
          Next
          <ChevronRight aria-hidden className="size-4" />
        </Button>
      </div>
    </div>
  )
}
