import { type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface EmptyStateProps {
  icon?: ReactNode
  title: string
  description?: ReactNode
  /** The one thing we want the user to do next. */
  action?: ReactNode
  /** A quieter secondary route out — "learn more", "clear filters". */
  secondaryAction?: ReactNode
  className?: string
  size?: 'sm' | 'md'
}

/**
 * An empty state is a real screen, not a shrug. Every one of these should
 * say what belongs here and offer the action that fills it — a bare
 * "No data" is the thing this component exists to prevent.
 */
export function EmptyState({
  icon,
  title,
  description,
  action,
  secondaryAction,
  className,
  size = 'md',
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-xl border border-dashed border-line-strong text-center',
        size === 'sm' ? 'px-6 py-10' : 'accent-wash px-6 py-16',
        className,
      )}
    >
      {icon && (
        <span
          aria-hidden
          className={cn(
            'mb-4 grid place-items-center rounded-xl border border-line bg-surface-2 text-ink-3',
            size === 'sm' ? 'size-10 [&>svg]:size-5' : 'size-14 [&>svg]:size-6',
          )}
        >
          {icon}
        </span>
      )}

      <h3 className={cn('font-display font-semibold text-ink', size === 'sm' ? 'text-sm' : 'text-base')}>
        {title}
      </h3>

      {description && (
        <p className="mt-1.5 max-w-sm text-pretty text-[13px] leading-relaxed text-ink-3">{description}</p>
      )}

      {(action || secondaryAction) && (
        <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
          {action}
          {secondaryAction}
        </div>
      )}
    </div>
  )
}

/**
 * The failure twin of EmptyState. Used wherever a fetch fails, so an error
 * is a recoverable state with a retry button rather than a dead screen or a
 * raw status code.
 */
export function ErrorState({
  title = 'Something went wrong',
  description,
  onRetry,
  className,
}: {
  title?: string
  description?: ReactNode
  onRetry?: () => void
  className?: string
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-xl border border-danger-border bg-danger-subtle px-6 py-12 text-center',
        className,
      )}
    >
      <h3 className="font-display text-base font-semibold text-ink">{title}</h3>
      {description && (
        <p className="mt-1.5 max-w-sm text-pretty text-[13px] leading-relaxed text-ink-2">{description}</p>
      )}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-5 inline-flex h-9 items-center rounded-md border border-line-strong bg-surface px-4 text-sm font-medium text-ink transition-colors hover:bg-surface-2"
        >
          Try again
        </button>
      )}
    </div>
  )
}
