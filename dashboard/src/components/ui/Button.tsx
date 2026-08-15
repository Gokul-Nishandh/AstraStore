import {
  cloneElement,
  forwardRef,
  isValidElement,
  type ButtonHTMLAttributes,
  type ReactElement,
  type ReactNode,
} from 'react'
import { cn } from '../../lib/cn'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'subtle'
type Size = 'sm' | 'md' | 'lg' | 'icon' | 'icon-sm'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  /** Rendered before the label. Hidden from assistive tech. */
  icon?: ReactNode
  /**
   * Renders the single child element with the button's styling instead of a
   * `<button>`. For navigation that should look like a button: a router
   * `<Link>` keeps real link semantics — middle-click, open in new tab,
   * a visible href — which a `<button onClick={navigate}>` throws away.
   */
  asChild?: boolean
}

/**
 * Every variant pairs a fill with its matching `text-on-*` foreground.
 *
 * That pairing is the point: the previous build set a light fill and left
 * the text white, which is how invisible button labels shipped. If you add
 * a variant, it needs a checked foreground — never inherit one.
 */
const variants: Record<Variant, string> = {
  // Copper fill with near-black text. Reads at roughly 7:1 in both themes.
  primary:
    'bg-accent text-on-accent shadow-sm hover:bg-accent-hover active:bg-accent-active ' +
    'disabled:bg-surface-3 disabled:text-ink-4 disabled:shadow-none',

  secondary:
    'bg-surface-2 text-ink border border-line-strong hover:bg-surface-3 hover:border-accent-border ' +
    'active:bg-surface-4 disabled:bg-surface-2 disabled:text-ink-4 disabled:border-line',

  subtle:
    'bg-accent-subtle text-accent-text border border-accent-border hover:bg-accent-subtle ' +
    'hover:border-accent active:bg-accent-subtle disabled:bg-surface-2 disabled:text-ink-4 disabled:border-line',

  ghost:
    'text-ink-2 hover:text-ink hover:bg-surface-2 active:bg-surface-3 ' +
    'disabled:text-ink-4 disabled:hover:bg-transparent',

  // Destructive actions read as an outline until hovered, so a delete
  // button never dominates a toolbar it happens to sit in.
  danger:
    'bg-transparent text-danger-text border border-danger-border hover:bg-danger hover:text-on-danger ' +
    'hover:border-danger active:bg-danger active:text-on-danger ' +
    'disabled:bg-surface-2 disabled:text-ink-4 disabled:border-line',
}

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-[13px] gap-1.5 rounded-md',
  md: 'h-9 px-4 text-sm gap-2 rounded-md',
  lg: 'h-11 px-6 text-[15px] gap-2 rounded-lg',
  icon: 'size-9 rounded-md',
  'icon-sm': 'size-8 rounded-md',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = 'primary', size = 'md', loading = false, icon, disabled, asChild, children, ...props },
  ref,
) {
  const isDisabled = disabled || loading

  const classes = cn(
    'relative inline-flex items-center justify-center font-medium whitespace-nowrap select-none',
    'transition-[background-color,border-color,color,box-shadow] duration-150 ease-[var(--ease-out)]',
    'disabled:cursor-not-allowed',
    'motion-safe:active:scale-[0.985] motion-safe:disabled:active:scale-100',
    variants[variant],
    sizes[size],
    className,
  )

  if (asChild && isValidElement(children)) {
    const child = children as ReactElement<{ className?: string }>
    return cloneElement(child, {
      className: cn(classes, child.props.className),
    })
  }

  return (
    <button
      ref={ref}
      // Communicates the busy state to screen readers, which a spinner alone does not.
      aria-busy={loading || undefined}
      disabled={isDisabled}
      className={classes}
      {...props}
    >
      {loading ? (
        <Spinner />
      ) : icon ? (
        <span aria-hidden className="grid shrink-0 place-items-center [&>svg]:size-4">
          {icon}
        </span>
      ) : null}
      {children}
    </button>
  )
})

/** Inherits `currentColor`, so it is legible on every variant automatically. */
function Spinner() {
  return (
    <span
      aria-hidden
      className="size-4 shrink-0 rounded-full border-2 border-current border-t-transparent motion-safe:animate-spin-slow"
    />
  )
}
