import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../lib/useAuth'
import { RouteFallback } from '../layout/RouteFallback'

/**
 * Gate for anything requiring a signed-in user.
 *
 * The `loading` status is the case that matters: on a full page reload we
 * hold a token but have not yet confirmed it. Redirecting then would throw
 * a signed-in user out to the login screen on every refresh, so we wait.
 */
export function AuthGuard({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <RouteFallback />

  if (status === 'anonymous') {
    // Carry the attempted location so sign-in can return the user to the
    // page they actually asked for rather than a generic landing spot.
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
