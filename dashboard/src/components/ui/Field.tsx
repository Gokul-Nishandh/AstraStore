import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import { cn } from '../../lib/cn'

/**
 * One control surface for every input type, so a text field, a select and
 * a textarea line up to the same height and share one focus treatment.
 */
const controlClass =
  'w-full rounded-lg border border-line-strong bg-bg-elevated text-ink ' +
  'placeholder:text-ink-4 ' +
  'transition-[border-color,box-shadow] duration-150 ' +
  'hover:border-ink-4 ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent-border ' +
  'disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-ink-4 disabled:hover:border-line-strong ' +
  'read-only:bg-surface-2 read-only:text-ink-2'

const errorClass = 'border-danger focus:border-danger focus:ring-danger-border'

/** Wraps label, control, hint and error with the right ids wired up. */
function FieldShell({
  id,
  label,
  hint,
  error,
  required,
  children,
  className,
}: {
  id: string
  label?: ReactNode
  hint?: ReactNode
  error?: string
  required?: boolean
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn('w-full', className)}>
      {label && (
        <label htmlFor={id} className="mb-1.5 flex items-center gap-1 text-[13px] font-medium text-ink-2">
          {label}
          {required && (
            <span className="text-danger-text" aria-hidden>
              *
            </span>
          )}
        </label>
      )}
      {children}
      {/* aria-live so a validation message is announced when it appears,
          not only when the field is next focused. */}
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1.5 text-xs text-danger-text">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1.5 text-xs text-ink-3">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

function describedBy(id: string, error?: string, hint?: ReactNode) {
  if (error) return `${id}-error`
  if (hint) return `${id}-hint`
  return undefined
}

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode
  hint?: ReactNode
  error?: string
  /** Rendered inside the control, before the text. */
  leading?: ReactNode
  /** Rendered inside the control, after the text (e.g. a reveal toggle). */
  trailing?: ReactNode
  wrapperClassName?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, wrapperClassName, label, hint, error, leading, trailing, id, required, ...props },
  ref,
) {
  const autoId = useId()
  const inputId = id ?? autoId

  return (
    <FieldShell
      id={inputId}
      label={label}
      hint={hint}
      error={error}
      required={required}
      className={wrapperClassName}
    >
      <div className="relative">
        {leading && (
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-0 left-3 grid place-items-center text-ink-3 [&>svg]:size-4"
          >
            {leading}
          </span>
        )}
        <input
          ref={ref}
          id={inputId}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(inputId, error, hint)}
          className={cn(
            controlClass,
            'h-10 px-3 text-sm',
            leading && 'pl-9',
            trailing && 'pr-10',
            error && errorClass,
            className,
          )}
          {...props}
        />
        {trailing && (
          <span className="absolute inset-y-0 right-2 grid place-items-center text-ink-3">{trailing}</span>
        )}
      </div>
    </FieldShell>
  )
})

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: ReactNode
  hint?: ReactNode
  error?: string
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { className, label, hint, error, id, required, ...props },
  ref,
) {
  const autoId = useId()
  const inputId = id ?? autoId

  return (
    <FieldShell id={inputId} label={label} hint={hint} error={error} required={required}>
      <textarea
        ref={ref}
        id={inputId}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(inputId, error, hint)}
        className={cn(controlClass, 'min-h-24 resize-y px-3 py-2.5 text-sm', error && errorClass, className)}
        {...props}
      />
    </FieldShell>
  )
})

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: ReactNode
  hint?: ReactNode
  error?: string
  wrapperClassName?: string
}

/**
 * A native select, deliberately. It is keyboard accessible and renders the
 * platform picker on mobile for free — which matters more on the filter-heavy
 * audit and monitoring screens than a custom listbox would.
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { className, wrapperClassName, label, hint, error, id, required, children, ...props },
  ref,
) {
  const autoId = useId()
  const inputId = id ?? autoId

  return (
    <FieldShell
      id={inputId}
      label={label}
      hint={hint}
      error={error}
      required={required}
      className={wrapperClassName}
    >
      <div className="relative">
        <select
          ref={ref}
          id={inputId}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(inputId, error, hint)}
          className={cn(
            controlClass,
            'h-10 appearance-none py-0 pl-3 pr-9 text-sm',
            error && errorClass,
            className,
          )}
          {...props}
        >
          {children}
        </select>
        <svg
          aria-hidden
          viewBox="0 0 20 20"
          fill="none"
          className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-ink-3"
        >
          <path d="M6 8l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
    </FieldShell>
  )
})

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode
  hint?: ReactNode
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { className, label, hint, id, ...props },
  ref,
) {
  const autoId = useId()
  const inputId = id ?? autoId

  return (
    <div className={cn('flex items-start gap-2.5', className)}>
      <input
        ref={ref}
        id={inputId}
        type="checkbox"
        className="mt-0.5 size-4 shrink-0 cursor-pointer rounded border-line-strong bg-bg-elevated text-accent accent-[var(--accent)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--ring)] disabled:cursor-not-allowed"
        {...props}
      />
      <div className="min-w-0">
        <label htmlFor={inputId} className="cursor-pointer text-[13px] font-medium text-ink-2">
          {label}
        </label>
        {hint && <p className="mt-0.5 text-xs text-ink-3">{hint}</p>}
      </div>
    </div>
  )
})
