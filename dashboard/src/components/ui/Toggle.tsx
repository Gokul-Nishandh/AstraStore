import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react'
import { Check, Minus } from 'lucide-react'
import { cn } from '../../lib/cn'

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: ReactNode
  description?: ReactNode
  /** Header checkbox state when only some rows below are selected. */
  indeterminate?: boolean
}

/**
 * The native input stays in the DOM and carries every accessibility
 * behaviour; it is made transparent and the visible box is a sibling driven
 * by `peer-*` variants. That is what keeps keyboard focus, form
 * participation and screen-reader semantics working for free.
 */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { className, label, description, indeterminate, id, ...props },
  ref,
) {
  const box = (
    <span className="relative inline-grid shrink-0 place-items-center">
      <input
        ref={(node) => {
          if (node) node.indeterminate = indeterminate ?? false
          if (typeof ref === 'function') ref(node)
          else if (ref) ref.current = node
        }}
        id={id}
        type="checkbox"
        className="peer size-4 cursor-pointer appearance-none rounded-[5px] border border-line-strong bg-surface transition-colors checked:border-accent checked:bg-accent indeterminate:border-accent indeterminate:bg-accent disabled:cursor-not-allowed disabled:bg-surface-2"
        {...props}
      />
      {indeterminate ? (
        <Minus aria-hidden className="pointer-events-none absolute size-3 text-on-accent opacity-0 peer-indeterminate:opacity-100" />
      ) : (
        <Check aria-hidden className="pointer-events-none absolute size-3 text-on-accent opacity-0 peer-checked:opacity-100" />
      )}
    </span>
  )

  if (!label) return <span className={className}>{box}</span>

  return (
    <label
      htmlFor={id}
      className={cn('flex cursor-pointer items-start gap-2.5 select-none', className)}
    >
      <span className="mt-0.5">{box}</span>
      <span className="min-w-0">
        <span className="block text-[13px] font-medium text-ink">{label}</span>
        {description && (
          <span className="mt-0.5 block text-[12px] leading-relaxed text-ink-3">{description}</span>
        )}
      </span>
    </label>
  )
})

export interface SwitchProps {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  description?: ReactNode
  disabled?: boolean
  /** Hides the label visually but keeps it for assistive technology. */
  hideLabel?: boolean
  className?: string
}

/**
 * A switch, not a checkbox: it takes effect immediately rather than on a
 * later save. Used only where that is genuinely true — anything behind a
 * Save button should be a Checkbox instead.
 */
export function Switch({
  checked,
  onChange,
  label,
  description,
  disabled,
  hideLabel,
  className,
}: SwitchProps) {
  const control = (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={hideLabel ? label : undefined}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full border transition-colors duration-150',
        'disabled:cursor-not-allowed disabled:opacity-50',
        checked ? 'border-accent bg-accent' : 'border-line-strong bg-surface-3',
      )}
    >
      <span
        aria-hidden
        className={cn(
          'inline-block size-3.5 rounded-full transition-transform duration-150 ease-[var(--ease-out)]',
          checked ? 'translate-x-[18px] bg-on-accent' : 'translate-x-[3px] bg-ink-3',
        )}
      />
    </button>
  )

  if (hideLabel) return <span className={className}>{control}</span>

  return (
    <div className={cn('flex items-start justify-between gap-4', className)}>
      <div className="min-w-0">
        <p className="text-[13px] font-medium text-ink">{label}</p>
        {description && (
          <p className="mt-0.5 text-[12px] leading-relaxed text-ink-3">{description}</p>
        )}
      </div>
      {control}
    </div>
  )
}
