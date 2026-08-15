import { createContext, useContext } from 'react'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface ToastOptions {
  /** Secondary line under the message — use for the "what now". */
  description?: string
  /** Milliseconds on screen. Errors default to longer; 0 pins it open. */
  duration?: number
  action?: { label: string; onClick: () => void }
}

export interface ToastContextValue {
  /** Preserved signature: toast(message, type) still works everywhere. */
  toast: (message: string, type?: ToastType, options?: ToastOptions) => void
  dismiss: (id: number) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within ToastProvider')
  return ctx
}
