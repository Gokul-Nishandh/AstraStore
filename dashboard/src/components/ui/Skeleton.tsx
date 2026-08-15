import { cn } from '../../lib/cn'

/**
 * Loading placeholder. Shaped like the content it stands in for, so the
 * layout does not jump when real data lands.
 */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('skeleton rounded-md', className)} />
}

/** A skeleton table body matching the real table's row height. */
export function SkeletonRows({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <tr key={rowIndex} className="border-b border-line last:border-0">
          {Array.from({ length: cols }).map((_, colIndex) => (
            <td key={colIndex} className="px-4 py-3.5">
              <Skeleton
                className={cn('h-3.5', colIndex === 0 ? 'w-2/3' : colIndex === cols - 1 ? 'w-12' : 'w-20')}
              />
            </td>
          ))}
        </tr>
      ))}
    </>
  )
}

/** Placeholder for a metric tile while its value loads. */
export function SkeletonTile() {
  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <Skeleton className="h-3 w-24" />
      <Skeleton className="mt-3 h-7 w-20" />
      <Skeleton className="mt-3 h-2.5 w-32" />
    </div>
  )
}

/**
 * Wraps loading regions so screen readers announce that content is on the
 * way instead of reading out a wall of empty boxes.
 */
export function LoadingRegion({ label = 'Loading', children }: { label?: string; children: React.ReactNode }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">{label}</span>
      {children}
    </div>
  )
}
