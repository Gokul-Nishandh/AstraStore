import { forwardRef, type SelectHTMLAttributes } from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '../../lib/cn'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  options: SelectOption[]
  /** Leading option that maps to an empty value — "Any action", "All users". */
  placeholder?: string
  size?: 'sm' | 'md'
  invalid?: boolean
}

/**
 * A native `<select>` wearing the design system.
 *
 * Deliberately not a custom listbox: filter bars on this product are used
 * heavily on mobile, and the platform picker is faster, accessible for free,
 * and cannot drift out of the viewport. The custom treatment is limited to
 * the chrome — the `appearance-none` reset plus our own chevron, because the
 * native arrow cannot be recoloured to match the ink ramp.
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { className, options, placeholder, size = 'md', invalid, ...props },
  ref,
) {
  return (
    <div className="relative inline-flex w-full">
      <select
        ref={ref}
        aria-invalid={invalid || undefined}
        className={cn(
          'w-full appearance-none rounded-md border bg-surface pr-9 font-medium text-ink',
          'transition-colors duration-150 outline-none',
          'hover:border-line-strong focus-visible:border-accent',
          'disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-ink-4',
          invalid ? 'border-danger-border' : 'border-line',
          size === 'sm' ? 'h-8 pl-2.5 text-[13px]' : 'h-9 pl-3 text-sm',
          className,
        )}
        {...props}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
      </select>
      <ChevronDown
        aria-hidden
        className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-ink-4"
      />
    </div>
  )
})
