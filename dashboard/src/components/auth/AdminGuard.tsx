import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../lib/useAuth'
import { RouteFallback } from '../layout/RouteFallback'

/**
 * Gate for the operations pages.
 *
 * Usable either as a layout route (no children — it renders an `Outlet`) or
 * as a wrapper around a single element.
 *
 * This is a navigation convenience, not the security boundary. Every admin
 * endpoint enforces the role server-side; hiding the page only stops a
 * non-admin from walking into a screen where every request would fail.
 */
export function AdminGuard({ children }: { children?: ReactNode }) {
  const { status, isAdmin } = useAuth()

  if (status === 'loading') return <RouteFallback />
  if (status === 'anonymous') return <Navigate to="/login" replace />

  // Bounce to the drive rather than showing a refusal screen: for someone
  // without the role, the operations pages may as well not exist.
  if (!isAdmin) return <Navigate to="/dashboard" replace />

  return <>{children ?? <Outlet />}</>
}
