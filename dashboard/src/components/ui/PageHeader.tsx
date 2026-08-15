import { type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface PageHeaderProps {
  title: string
  description?: ReactNode
  /** Primary + secondary page actions. Keep to two; more belongs in a Menu. */
  actions?: ReactNode
  /** Small copper kicker above the title — the section this page belongs to. */
  eyebrow?: ReactNode
  /** Optional badge row under the title (status, environment, counts). */
  meta?: ReactNode
  className?: string
}

/**
 * The one h1 on every page. Actions wrap below the title rather than
 * shrinking it, so a long bucket name never gets squeezed to an ellipsis on
 * a narrow viewport.
 */
export function PageHeader({ title, description, actions, eyebrow, meta, className }: PageHeaderProps) {
  return (
    <div className={cn('flex flex-wrap items-end justify-between gap-x-6 gap-y-4', className)}>
      <div className="min-w-0 flex-1 basis-64">
        {eyebrow && (
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-accent-text">
            {eyebrow}
          </p>
        )}
        <h1 className="font-display text-xl font-semibold tracking-tight text-balance text-ink sm:text-[22px]">
          {title}
        </h1>
        {description && (
          <p className="mt-1.5 max-w-2xl text-pretty text-[13px] leading-relaxed text-ink-3">{description}</p>
        )}
        {meta && <div className="mt-3 flex flex-wrap items-center gap-2">{meta}</div>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}
