import { type HTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

/* ------------------------------------------------------------------
   Status vocabulary
   ------------------------------------------------------------------
   The whole product uses these six tones and nothing else. `accent` is
   for brand and intent; it is NOT a status. A node being healthy is
   `success`, never `accent` — mixing the two is what made the old
   dashboard hard to scan.
   ------------------------------------------------------------------ */
export type Tone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger' | 'info'

const toneStyles: Record<Tone, string> = {
  neutral: 'bg-neutral-subtle border-neutral-border text-ink-2',
  accent: 'bg-accent-subtle border-accent-border text-accent-text',
  success: 'bg-success-subtle border-success-border text-success-text',
  warning: 'bg-warning-subtle border-warning-border text-warning-text',
  danger: 'bg-danger-subtle border-danger-border text-danger-text',
  info: 'bg-neutral-subtle border-neutral-border text-ink-2',
}

const dotColors: Record<Tone, string> = {
  neutral: 'bg-ink-4',
  accent: 'bg-accent',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  info: 'bg-ink-3',
}

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone
  /** Show a leading status dot. */
  dot?: boolean
  /** Animate the dot — reserve for genuinely live values. */
  pulse?: boolean
  size?: 'sm' | 'md'
}

export function Badge({
  className,
  tone = 'neutral',
  dot = false,
  pulse = false,
  size = 'md',
  children,
  ...props
}: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border font-medium whitespace-nowrap',
        size === 'sm' ? 'px-2 py-0.5 text-[10.5px]' : 'px-2.5 py-1 text-[11.5px]',
        toneStyles[tone],
        className,
      )}
      {...props}
    >
      {dot && <StatusDot tone={tone} pulse={pulse} />}
      {children}
    </span>
  )
}

export interface StatusDotProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone
  pulse?: boolean
}

/**
 * Colour is never the only signal in this product — a dot always sits
 * beside a text label — so this stays decorative and hidden from
 * assistive technology.
 */
export function StatusDot({ className, tone = 'neutral', pulse = false, ...props }: StatusDotProps) {
  return (
    <span
      aria-hidden
      className={cn(
        'inline-block size-1.5 shrink-0 rounded-full',
        dotColors[tone],
        pulse && 'motion-safe:animate-pulse-dot',
        className,
      )}
      {...props}
    />
  )
}

/* ------------------------------------------------------------------
   Domain mappings — one place that decides what a backend state looks
   like, so a node reading DEGRADED is amber on every screen.
   ------------------------------------------------------------------ */

export function nodeStateTone(state: string | null | undefined): Tone {
  switch (state) {
    case 'HEALTHY':
      return 'success'
    case 'DEGRADED':
    case 'RECOVERING':
      return 'warning'
    case 'DOWN':
      return 'danger'
    default:
      return 'neutral'
  }
}

/**
 * A chunk's replication state.
 *
 * Only REPLICATED and COMPLETE are `success` — those are the two the backend
 * counts as durable in `countChunksReplicated`, and this mapping stays in
 * step with it so a chunk the object header calls replicated is never amber
 * in the table below it. PENDING is neutral rather than a warning: a chunk
 * written a second ago has not failed at anything.
 */
export function replicationStatusTone(status: string | null | undefined): Tone {
  switch (status) {
    case 'REPLICATED':
    case 'COMPLETE':
      return 'success'
    case 'REPLICATING':
    case 'REPAIRING':
    case 'UNDER_REPLICATED':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

export function serviceStatusTone(status: string | null | undefined): Tone {
  switch (status?.toUpperCase()) {
    case 'UP':
      return 'success'
    case 'DEGRADED':
    case 'WARNING':
      return 'warning'
    case 'DOWN':
    case 'OUT_OF_SERVICE':
      return 'danger'
    default:
      return 'neutral'
  }
}
