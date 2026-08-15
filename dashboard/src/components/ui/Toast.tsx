import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react'
import { ToastContext, type ToastOptions, type ToastType } from './toast-context'
import { cn } from '../../lib/cn'

interface ToastRecord {
  id: number
  message: string
  type: ToastType
  description?: string
  duration: number
  action?: { label: string; onClick: () => void }
}

const DEFAULT_DURATION: Record<ToastType, number> = {
  success: 3500,
  info: 3500,
  warning: 5000,
  // Errors stay longer: they usually carry something the user must read.
  error: 6500,
}

const MAX_VISIBLE = 4

const icons: Record<ToastType, ReactNode> = {
  success: <CheckCircle2 />,
  error: <XCircle />,
  warning: <AlertTriangle />,
  info: <Info />,
}

const accents: Record<ToastType, string> = {
  success: 'text-success-text',
  error: 'text-danger-text',
  warning: 'text-warning-text',
  info: 'text-ink-2',
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastRecord[]>([])
  const nextId = useRef(1)
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>())

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id))
    const timer = timers.current.get(id)
    if (timer) {
      clearTimeout(timer)
      timers.current.delete(id)
    }
  }, [])

  const toast = useCallback(
    (message: string, type: ToastType = 'info', options?: ToastOptions) => {
      const id = nextId.current++
      const duration = options?.duration ?? DEFAULT_DURATION[type]

      setToasts((current) => {
        const next = [...current, { id, message, type, description: options?.description, duration, action: options?.action }]
        // Keep the stack shallow so it never covers the interface.
        return next.slice(-MAX_VISIBLE)
      })

      // duration 0 pins the toast until it is dismissed by hand.
      if (duration > 0) {
        timers.current.set(
          id,
          setTimeout(() => dismiss(id), duration),
        )
      }
    },
    [dismiss],
  )

  // Clear pending timers on unmount so nothing fires against a dead tree.
  useEffect(() => {
    const pending = timers.current
    return () => {
      pending.forEach(clearTimeout)
      pending.clear()
    }
  }, [])

  const value = useMemo(() => ({ toast, dismiss }), [toast, dismiss])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        // Assertive for errors would interrupt; polite still gets announced.
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed bottom-4 right-4 z-[100] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
      >
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onDismiss={() => dismiss(t.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  )
}

function ToastItem({ toast, onDismiss }: { toast: ToastRecord; onDismiss: () => void }) {
  return (
    <div
      className={cn(
        'pointer-events-auto flex items-start gap-3 rounded-xl border border-line bg-bg-elevated p-3.5 shadow-lg',
        'motion-safe:animate-toast-in',
      )}
    >
      <span aria-hidden className={cn('mt-px shrink-0 [&>svg]:size-[18px]', accents[toast.type])}>
        {icons[toast.type]}
      </span>

      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-medium leading-snug text-ink">{toast.message}</p>
        {toast.description && (
          <p className="mt-1 text-[12.5px] leading-relaxed text-ink-3">{toast.description}</p>
        )}
        {toast.action && (
          <button
            type="button"
            onClick={() => {
              toast.action?.onClick()
              onDismiss()
            }}
            className="mt-2 text-[12.5px] font-semibold text-accent-text underline-offset-2 hover:underline"
          >
            {toast.action.label}
          </button>
        )}
      </div>

      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss notification"
        className="-m-1 shrink-0 rounded-md p-1 text-ink-4 transition-colors hover:bg-surface-2 hover:text-ink"
      >
        <X className="size-4" />
      </button>
    </div>
  )
}
