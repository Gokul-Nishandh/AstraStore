import { createContext } from 'react'
import type { AuthUser, LoginRequest, RegisterRequest } from '../types/api'

/**
 * `status` exists so guards can tell "not signed in" apart from "we hold a
 * token and are still checking it". Collapsing those two into one boolean is
 * what made a page reload bounce a signed-in user back to the login screen.
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  user: AuthUser | null
  status: AuthStatus
  isAuthenticated: boolean
  isAdmin: boolean
  /** False for READ_ONLY accounts — disables every mutating control. */
  canWrite: boolean
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
  /** Re-reads the profile after a change that alters identity or roles. */
  refreshUser: () => Promise<void>
  /** Applied after a profile edit that made the server reissue tokens. */
  applyTokens: (token: string, refreshToken: string, user: AuthUser) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
