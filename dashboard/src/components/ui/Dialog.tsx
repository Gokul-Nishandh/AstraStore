import { useCallback, useEffect, useId, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { cn } from '../../lib/cn'

export type DialogSize = 'sm' | 'md' | 'lg' | 'xl'

const sizes: Record<DialogSize, string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-2xl',
}

export interface DialogProps {
  open: boolean
  onClose: () => void
  title?: string
  description?: ReactNode
  children?: ReactNode
  footer?: ReactNode
  className?: string
  size?: DialogSize
  /** Hide the corner close button — for dialogs that force an explicit choice. */
  hideClose?: boolean
  /** Clicking the backdrop dismisses. Turn off for destructive flows mid-submit. */
  dismissOnBackdrop?: boolean
  /**
   * Required when there is no `title`: every dialog must have an accessible
   * name, and an unnamed modal is announced as just "dialog".
   */
  ariaLabel?: string
}

/* Nested dialogs must not fight over `body.overflow`, so the lock is
   reference counted at module scope rather than per instance. */
let scrollLocks = 0
let previousOverflow = ''

/**
 * Built on the native `<dialog>` element deliberately. `showModal()` gives us
 * the top layer, the focus trap and the inert background for free — three
 * things a hand-rolled modal gets subtly wrong — and the backdrop is already
 * themed in index.css.
 *
 * What the browser does *not* reliably give us, and this component adds:
 *  - Escape routed back through React state instead of closing the element
 *    behind the `open` prop's back (see the `cancel` handler).
 *  - Focus restored to whatever opened the dialog, even when the trigger was
 *    re-rendered while the dialog was up.
 *  - A body scroll lock, so the page behind does not scroll on wheel.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  className,
  size = 'md',
  hideClose = false,
  dismissOnBackdrop = true,
  ariaLabel,
}: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const restoreRef = useRef<HTMLElement | null>(null)
  const autoId = useId()
  const titleId = `${autoId}-title`
  const descId = `${autoId}-desc`

  // Latest-value refs keep the event listeners below from re-subscribing on
  // every render of the parent.
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose
  const openRef = useRef(open)
  openRef.current = open
  const dismissRef = useRef(dismissOnBackdrop)
  dismissRef.current = dismissOnBackdrop

  const restoreFocus = useCallback(() => {
    const target = restoreRef.current
    restoreRef.current = null
    if (target && target.isConnected) target.focus()
  }, [])

  useEffect(() => {
    const el = ref.current
    if (!el) return

    if (open) {
      if (!el.open) {
        restoreRef.current = document.activeElement as HTMLElement | null
        el.showModal()
      }
    } else if (el.open) {
      el.close()
      restoreFocus()
    }
  }, [open, restoreFocus])

  // Close on unmount so an unmounted-while-open dialog cannot leave the page
  // inert, and hand focus back where it came from.
  useEffect(
    () => () => {
      const el = ref.current
      if (el?.open) el.close()
      const target = restoreRef.current
      restoreRef.current = null
      if (target && target.isConnected) target.focus()
    },
    [],
  )

  useEffect(() => {
    if (!open) return
    scrollLocks += 1
    if (scrollLocks === 1) {
      previousOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
    }
    return () => {
      scrollLocks -= 1
      if (scrollLocks === 0) document.body.style.overflow = previousOverflow
    }
  }, [open])

  useEffect(() => {
    const el = ref.current
    if (!el) return

    // Escape fires `cancel`. Preventing the default keeps the element's open
    // state and the React prop from drifting apart — the parent closes it.
    const onCancel = (e: Event) => {
      e.preventDefault()
      onCloseRef.current()
    }

    // The dialog element fills its own box, so a click that lands on the
    // element itself came from the backdrop.
    const onClick = (e: MouseEvent) => {
      if (!dismissRef.current) return
      if (e.target === el) onCloseRef.current()
    }

    // Covers `<form method="dialog">` and any native close we did not drive.
    const onNativeClose = () => {
      if (openRef.current) onCloseRef.current()
    }

    el.addEventListener('cancel', onCancel)
    el.addEventListener('click', onClick)
    el.addEventListener('close', onNativeClose)
    return () => {
      el.removeEventListener('cancel', onCancel)
      el.removeEventListener('click', onClick)
      el.removeEventListener('close', onNativeClose)
    }
  }, [])

  const labelledBy = title ? titleId : undefined

  return (
    <dialog
      ref={ref}
      aria-labelledby={labelledBy}
      aria-label={labelledBy ? undefined : ariaLabel}
      aria-describedby={description ? descId : undefined}
      className={cn(
        'm-auto w-[calc(100vw-1.5rem)] rounded-xl border border-line bg-bg-elevated p-0 text-ink shadow-xl',
        'backdrop:bg-overlay open:motion-safe:animate-modal-in',
        sizes[size],
        className,
      )}
    >
      {(title || description || !hideClose) && (
        <div className="flex items-start justify-between gap-4 px-5 pt-5">
          <div className="min-w-0">
            {title && (
              <h2 id={titleId} className="font-display text-base font-semibold tracking-tight text-ink">
                {title}
              </h2>
            )}
            {description && (
              <p id={descId} className="mt-1 text-[13px] leading-relaxed text-ink-3">
                {description}
              </p>
            )}
          </div>
          {!hideClose && (
            <button
              type="button"
              onClick={onClose}
              aria-label="Close dialog"
              className="-mr-1 -mt-1 grid size-8 shrink-0 place-items-center rounded-md text-ink-3 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-2 hover:text-ink"
            >
              <X className="size-4" />
            </button>
          )}
        </div>
      )}

      {children != null && <div className="px-5 py-5">{children}</div>}

      {footer && (
        <div className="flex flex-wrap justify-end gap-2 border-t border-line bg-surface/60 px-5 py-4">
          {footer}
        </div>
      )}
    </dialog>
  )
}
