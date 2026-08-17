import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest, UserProfile } from '../types/api'
import { api, getAccessToken, getRefreshToken, setAuthTokens, SESSION_EXPIRED_EVENT } from './api'
import { AuthContext, type AuthStatus } from './auth-context'

/**
 * Routes where nothing reads the session, so resolving it buys nothing.
 *
 * A visitor arriving with a stale token used to cost two failed requests here
 * — the profile call, then the refresh it triggered — and a browser prints
 * both to the console as errors no matter how cleanly the app catches them.
 * The marketing page showing red in devtools is a bad first impression, and
 * the work was wasted either way.
 *
 * `/login` and `/register` are deliberately NOT in this list: both redirect an
 * already-signed-in visitor away from the form, which needs a resolved
 * session.
 */
const NO_SESSION_PATHS = new Set([
  '/',
  '/forgot-password',
  '/reset-password',
  '/privacy',
  '/terms',
  '/support',
  '/data-deletion',
])

/**
 * Whether a JWT's own `exp` says it is already spent.
 *
 * Read, never trusted — the server still verifies the signature. This only
 * decides whether sending the token is worth a round trip. A malformed token
 * counts as expired: it cannot succeed either.
 */
function isExpired(token: string | null): boolean {
  if (!token) return true

  const parts = token.split('.')
  if (parts.length !== 3) return true

  try {
    const json = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    const claims = JSON.parse(json) as { exp?: number }
    // No `exp` means we cannot rule it out — let the server decide.
    if (typeof claims.exp !== 'number') return false
    return claims.exp * 1000 <= Date.now()
  } catch {
    return true
  }
}

function fromAuthResponse(response: AuthResponse): AuthUser {
  return {
    userId: response.userId,
    username: response.username,
    email: response.email,
    roles: response.roles,
  }
}

function fromProfile(profile: UserProfile): AuthUser {
  return {
    userId: profile.id,
    username: profile.username,
    email: profile.email,
    roles: profile.roles,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  // Starts at "loading" whenever a token survived the reload. Treating that
  // as "anonymous" is what used to bounce a signed-in user to /login on
  // every refresh, before the profile call had a chance to answer.
  const [status, setStatus] = useState<AuthStatus>(() =>
    getAccessToken() ? 'loading' : 'anonymous',
  )

  /* Restore the session from a stored token exactly once, the first time the
     user is on a route that actually reads it. The token alone is not trusted
     — the profile call is what confirms it is still valid and, just as
     importantly, what picks up a role change made by an administrator since
     the token was issued.

     Keyed on the path rather than run flatly on mount: someone who lands on
     the marketing page and then clicks through to the dashboard restores at
     that click instead of paying for it up front. */
  const location = useLocation()
  const restoreStarted = useRef(false)

  useEffect(() => {
    if (restoreStarted.current) return
    if (NO_SESSION_PATHS.has(location.pathname)) return

    const access = getAccessToken()
    const refresh = getRefreshToken()
    if (!access && !refresh) return

    restoreStarted.current = true
    let cancelled = false

    const clear = () => {
      if (cancelled) return
      setAuthTokens(null, null)
      setUser(null)
      setStatus('anonymous')
    }

    const restore = async () => {
      /* An access token we can already see has expired is not worth a request
         — it can only come back 401, and the client would then refresh anyway.
         Going straight to the refresh turns the common "came back the next
         day" case into a silent, successful renewal. */
      if (access && isExpired(access)) {
        if (!refresh) {
          clear()
          return
        }
        try {
          const renewed = await api.refresh({ refreshToken: refresh })
          if (cancelled) return
          setAuthTokens(renewed.token, renewed.refreshToken)
        } catch {
          clear()
          return
        }
      }

      try {
        const profile = await api.profile()
        if (cancelled) return
        setUser(fromProfile(profile))
        setStatus('authenticated')
      } catch {
        clear()
      }
    }

    void restore()

    return () => {
      cancelled = true
    }
  }, [location.pathname])

  /* One listener for a spent refresh token, rather than every in-flight
     request discovering the dead session independently and racing to
     redirect. */
  useEffect(() => {
    const onExpired = () => {
      setUser(null)
      setStatus('anonymous')
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired)
  }, [])

  const login = useCallback(async (payload: LoginRequest) => {
    const response = await api.login(payload)
    setAuthTokens(response.token, response.refreshToken)
    setUser(fromAuthResponse(response))
    setStatus('authenticated')
  }, [])

  const register = useCallback(async (payload: RegisterRequest) => {
    await api.register(payload)
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      try {
        await api.logout({ refreshToken })
      } catch {
        // Best effort. Server-side revocation is a courtesy; clearing the
        // local tokens is what actually ends this session.
      }
    }
    setAuthTokens(null, null)
    setUser(null)
    setStatus('anonymous')
  }, [])

  const refreshUser = useCallback(async () => {
    const profile = await api.profile()
    setUser(fromProfile(profile))
    setStatus('authenticated')
  }, [])

  /** After a profile edit that changed identity, the server reissues the pair. */
  const applyTokens = useCallback((token: string, refreshToken: string, next: AuthUser) => {
    setAuthTokens(token, refreshToken)
    setUser(next)
  }, [])

  const value = useMemo(
    () => ({
      user,
      status,
      isAuthenticated: status === 'authenticated' && user !== null,
      isAdmin: user?.roles.includes('ADMIN') ?? false,
      // READ_ONLY is the only role that cannot write, and it does not stack
      // with the others: holding it alongside USER would be contradictory,
      // so the check is for its presence, not the absence of the rest.
      canWrite: user !== null && !user.roles.includes('READ_ONLY'),
      login,
      register,
      logout,
      refreshUser,
      applyTokens,
    }),
    [user, status, login, register, logout, refreshUser, applyTokens],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
