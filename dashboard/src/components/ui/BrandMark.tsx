import type { CSSProperties } from 'react'
import { cn } from '../../lib/cn'

export function Asterisk({ className, style }: { className?: string; style?: CSSProperties }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" className={className} style={style} aria-hidden>
      <path
        d="M12 3.5v17M5.9 7.5l12.2 9M18.1 7.5l-12.2 9"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function BrandMark({
  inverted,
  className,
}: {
  inverted?: boolean
  className?: string
}) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <span
        className={cn(
          'grid size-7 shrink-0 place-items-center rounded-[7px] transition-colors duration-200',
          // The mark is the one place copper is used as a pure fill; its
          // foreground comes from the paired --on-accent token rather than a
          // hardcoded white, which is what keeps it legible in both themes.
          inverted ? 'bg-ink text-bg' : 'bg-accent text-on-accent',
        )}
      >
        <Asterisk className="size-3.5" />
      </span>
      <span className={cn('text-[17px] tracking-tight', inverted ? 'text-bg' : 'text-ink')}>
        <span className="font-semibold">Astra</span>
        <span className={cn('font-light', inverted ? 'text-bg/60' : 'text-ink-3')}>Store</span>
      </span>
    </span>
  )
}
