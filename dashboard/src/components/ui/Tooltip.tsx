import { useId, useState, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface TooltipProps {
  /** Kept short — a tooltip is a label, not documentation. */
  content: ReactNode
  children: ReactNode
  side?: 'top' | 'bottom'
  className?: string
}

/**
 * Supplementary label on hover and keyboard focus.
 *
 * Two rules this enforces. It opens on focus as well as hover, so keyboard
 * users are not the only people denied the explanation. And it is never the
 * sole carrier of meaning — touch devices have no hover state at all, so
 * anything essential must also be readable without it.
 */
export function Tooltip({ content, children, side = 'top', className }: TooltipProps) {
  const [open, setOpen] = useState(false)
  const id = useId()

  return (
    <span
      className={cn('relative inline-flex', className)}
      onPointerEnter={() => setOpen(true)}
      onPointerLeave={() => setOpen(false)}
      onFocusCapture={() => setOpen(true)}
      onBlurCapture={() => setOpen(false)}
    >
      <span aria-describedby={open ? id : undefined} className="inline-flex">
        {children}
      </span>

      {open && (
        <span
          role="tooltip"
          id={id}
          className={cn(
            'pointer-events-none absolute left-1/2 z-50 w-max max-w-56 -translate-x-1/2 rounded-md',
            'border border-line bg-bg-elevated px-2.5 py-1.5 text-center text-[12px] leading-snug',
            'text-ink-2 shadow-lg motion-safe:animate-fade-in',
            side === 'top' ? 'bottom-full mb-1.5' : 'top-full mt-1.5',
          )}
        >
          {content}
        </span>
      )}
    </span>
  )
}
