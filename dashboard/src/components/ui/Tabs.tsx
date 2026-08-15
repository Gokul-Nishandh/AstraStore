import { type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface TabItem<T extends string = string> {
  value: T
  label: ReactNode
  /** Trailing count, e.g. the number of rows behind the tab. */
  count?: number | null
  disabled?: boolean
}

export interface TabsProps<T extends string = string> {
  items: TabItem<T>[]
  value: T
  onChange: (value: T) => void
  'aria-label': string
  className?: string
}

/**
 * Underlined tabs for switching a page's primary view.
 *
 * Scrolls sideways rather than wrapping on narrow screens — a tab strip that
 * reflows onto two rows pushes the content it labels below the fold, which
 * on a phone means the user cannot see the effect of their own tap.
 */
export function Tabs<T extends string = string>({
  items,
  value,
  onChange,
  className,
  ...props
}: TabsProps<T>) {
  return (
    <div
      role="tablist"
      aria-label={props['aria-label']}
      className={cn('no-scrollbar flex gap-1 overflow-x-auto border-b border-line', className)}
    >
      {items.map((item) => {
        const active = item.value === value
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            aria-selected={active}
            disabled={item.disabled}
            onClick={() => onChange(item.value)}
            className={cn(
              'relative -mb-px inline-flex shrink-0 items-center gap-2 whitespace-nowrap border-b-2 px-3 py-2.5',
              'text-[13px] font-medium transition-colors duration-150',
              'disabled:cursor-not-allowed disabled:text-ink-4',
              active
                ? 'border-accent text-ink'
                : 'border-transparent text-ink-3 hover:border-line-strong hover:text-ink',
            )}
          >
            {item.label}
            {item.count != null && (
              <span
                className={cn(
                  'tnum rounded-full px-1.5 py-0.5 text-[10.5px] font-semibold',
                  active ? 'bg-accent-subtle text-accent-text' : 'bg-neutral-subtle text-ink-3',
                )}
              >
                {item.count}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}

export interface SegmentedControlProps<T extends string = string> {
  items: { value: T; label: ReactNode }[]
  value: T
  onChange: (value: T) => void
  'aria-label': string
  size?: 'sm' | 'md'
  className?: string
}

/**
 * A compact switch between mutually exclusive options — time windows, list
 * versus grid. Distinct from Tabs on purpose: tabs change what a page is
 * about, a segmented control changes how the same thing is displayed.
 */
export function SegmentedControl<T extends string = string>({
  items,
  value,
  onChange,
  size = 'md',
  className,
  ...props
}: SegmentedControlProps<T>) {
  return (
    <div
      role="radiogroup"
      aria-label={props['aria-label']}
      className={cn(
        'inline-flex shrink-0 items-center gap-0.5 rounded-lg border border-line bg-surface-2 p-0.5',
        className,
      )}
    >
      {items.map((item) => {
        const active = item.value === value
        return (
          <button
            key={item.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(item.value)}
            className={cn(
              'inline-flex items-center justify-center gap-1.5 rounded-md font-medium whitespace-nowrap',
              'transition-colors duration-150',
              size === 'sm' ? 'h-7 px-2.5 text-[12px]' : 'h-8 px-3 text-[13px]',
              active
                ? 'bg-surface text-ink shadow-xs'
                : 'text-ink-3 hover:text-ink',
            )}
          >
            {item.label}
          </button>
        )
      })}
    </div>
  )
}
