import { Star } from 'lucide-react'
import { Tooltip } from '../ui/Tooltip'
import { cn } from '../../lib/cn'

export interface StarButtonProps {
  starred: boolean
  onToggle: () => void
  /** Name of the object, so the control reads as more than "star". */
  name: string
  busy?: boolean
  /** READ_ONLY accounts get a disabled control with the reason attached. */
  disabledReason?: string
  className?: string
}

export function StarButton({
  starred,
  onToggle,
  name,
  busy = false,
  disabledReason,
  className,
}: StarButtonProps) {
  const disabled = Boolean(disabledReason)

  const button = (
    <button
      type="button"
      // aria-pressed rather than a label that flips: a screen reader user
      // hears the current state, not a guess at what the click will do.
      aria-pressed={starred}
      aria-label={`Star ${name}`}
      disabled={disabled || busy}
      onClick={(event) => {
        event.stopPropagation()
        event.preventDefault()
        onToggle()
      }}
      className={cn(
        'grid size-11 shrink-0 place-items-center rounded-md transition-colors duration-150 sm:size-9',
        // Handing the hit target to the wrapper is what lets the tooltip
        // explain a control the browser will not send events to.
        'disabled:pointer-events-none',
        starred ? 'text-accent-text' : 'text-ink-4 hover:bg-surface-2 hover:text-ink-2',
        disabled && 'opacity-50',
        busy && 'opacity-60',
        className,
      )}
    >
      <Star aria-hidden className={cn('size-4', starred && 'fill-current')} />
    </button>
  )

  if (!disabled) return button

  return <Tooltip content={disabledReason}>{button}</Tooltip>
}
