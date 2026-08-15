import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Dialog } from './Dialog'
import { Button } from './Button'

export interface ConfirmDialogProps {
  open: boolean
  title: string
  /** Sits under the title, in the dialog header. */
  description?: ReactNode
  /** Body copy. Defaults to the standard irreversible-action warning. */
  body?: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  /**
   * Renders the confirm button in the `danger` variant and swaps the default
   * body copy for the "cannot be undone" warning. Use for deletes and
   * anything that drops data.
   */
  destructive?: boolean
  /**
   * May return a promise. While it is pending the dialog shows a loading
   * state and blocks further clicks, so a slow delete cannot be submitted
   * twice by an impatient double click.
   */
  onConfirm: () => void | Promise<void>
  onClose: () => void
  /** Externally driven busy state, OR-ed with the internal one. */
  loading?: boolean
}

/**
 * Deliberately does NOT close itself on success. The caller decides — a
 * delete that fails should leave the dialog open with the error visible
 * rather than dismissing and losing the context.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  body,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  onConfirm,
  onClose,
  loading = false,
}: ConfirmDialogProps) {
  const [pending, setPending] = useState(false)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  // A dialog reopened after a failed attempt must not inherit the old spinner.
  useEffect(() => {
    if (!open) setPending(false)
  }, [open])

  const busy = pending || loading

  const handleConfirm = useCallback(async () => {
    if (pending) return
    setPending(true)
    try {
      await onConfirm()
    } catch {
      // Swallowed on purpose: the caller owns error reporting (a toast, an
      // inline message). Re-throwing here would only surface as an unhandled
      // rejection while leaving the button stuck in its busy state.
    } finally {
      if (mounted.current) setPending(false)
    }
  }, [onConfirm, pending])

  const defaultBody = destructive
    ? 'This action cannot be undone.'
    : 'Please confirm you want to continue.'

  return (
    <Dialog
      open={open}
      onClose={busy ? () => {} : onClose}
      title={title}
      description={description}
      size="sm"
      hideClose={busy}
      dismissOnBackdrop={!busy}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            {cancelLabel}
          </Button>
          <Button
            variant={destructive ? 'danger' : 'primary'}
            onClick={handleConfirm}
            loading={busy}
            disabled={busy}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-[13px] leading-relaxed text-ink-2">{body ?? defaultBody}</p>
    </Dialog>
  )
}
