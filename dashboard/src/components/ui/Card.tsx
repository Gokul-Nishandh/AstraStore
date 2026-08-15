import { type HTMLAttributes, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padded?: boolean
  /** Lifts the card off the page. Use for modals and hero panels only. */
  elevated?: boolean
  /** Removes the border and background — for grouping without a visual box. */
  bare?: boolean
}

export function Card({ className, padded = true, elevated = false, bare = false, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-xl transition-colors',
        !bare && 'border border-line bg-surface',
        elevated ? 'shadow-lg' : !bare && 'shadow-xs',
        padded && 'p-5',
        className,
      )}
      {...props}
    />
  )
}

export function CardHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('mb-4 flex items-start justify-between gap-4', className)} {...props} />
}

export function CardTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={cn('font-display text-[15px] font-semibold tracking-tight text-ink', className)}
      {...props}
    />
  )
}

export function CardDescription({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('mt-1 text-[13px] leading-relaxed text-ink-3', className)} {...props} />
}

/**
 * A titled section with an optional icon and trailing actions — the
 * repeating unit across settings, cluster and object pages. Having one
 * component for it is what keeps headers aligned from screen to screen.
 */
export function CardSection({
  icon,
  title,
  description,
  actions,
  children,
  className,
  ...props
}: {
  icon?: ReactNode
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
} & Omit<HTMLAttributes<HTMLDivElement>, 'title'>) {
  return (
    <Card className={className} {...props}>
      <CardHeader>
        <div className="flex items-start gap-3">
          {icon && (
            <span
              aria-hidden
              className="grid size-9 shrink-0 place-items-center rounded-lg border border-accent-border bg-accent-subtle text-accent-text [&>svg]:size-4"
            >
              {icon}
            </span>
          )}
          <div className="min-w-0">
            <CardTitle>{title}</CardTitle>
            {description && <CardDescription>{description}</CardDescription>}
          </div>
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </CardHeader>
      {children}
    </Card>
  )
}
