import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest, UserProfile } from '../types/api'
import { api, getAccessToken, getRefreshToken, setAuthTokens, SESSION_EXPIRED_EVENT } from './api'
import { AuthContext, type AuthStatus } from './auth-context'

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

  /* Restore the session from a stored token exactly once, on mount. The
     token alone is not trusted — the profile call is what confirms it is
     still valid and, just as importantly, what picks up a role change made
     by an administrator since the token was issued. */
  useEffect(() => {
    if (!getAccessToken()) return

    let cancelled = false
    api
      .profile()
      .then((profile) => {
        if (cancelled) return
        setUser(fromProfile(profile))
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        setAuthTokens(null, null)
        setUser(null)
        setStatus('anonymous')
      })

    return () => {
      cancelled = true
    }
  }, [])

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
