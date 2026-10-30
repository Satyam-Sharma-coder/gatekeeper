import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { login as loginRequest } from '../api/auth.ts'
import { setUnauthorizedHandler } from '../api/client.ts'
import { AuthContext } from './authContext.ts'
import type { AuthContextValue } from './authContext.ts'
import { parseJwt } from './jwt.ts'
import { clearSession, loadSession, saveSession } from './session.ts'
import type { Session } from './session.ts'

const SESSION_EXPIRED_NOTICE = 'Your session has expired. Please sign in again.'
const MAX_TIMER_MS = 2_147_483_647

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [session, setSession] = useState<Session | null>(loadSession)
  const [notice, setNotice] = useState<string | null>(null)

  const expire = useCallback(() => {
    clearSession()
    setSession(null)
    setNotice(SESSION_EXPIRED_NOTICE)
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setSession(null)
    setNotice(null)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const response = await loginRequest({ username, password })
    const claims = parseJwt(response.token)
    const issuedAt = Date.now()
    const byLifetime = issuedAt + response.expiresInSeconds * 1000
    const byClaim = claims?.exp ? claims.exp * 1000 : byLifetime
    const next: Session = {
      token: response.token,
      username: claims?.sub ?? username,
      role: response.role,
      organizationId: response.organizationId,
      issuedAt,
      expiresAt: Math.min(byLifetime, byClaim),
    }
    saveSession(next)
    setNotice(null)
    setSession(next)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(expire)
    return () => setUnauthorizedHandler(null)
  }, [expire])

  useEffect(() => {
    if (!session) return
    const remaining = Math.max(0, session.expiresAt - Date.now())
    const timer = window.setTimeout(expire, Math.min(remaining, MAX_TIMER_MS))
    return () => window.clearTimeout(timer)
  }, [session, expire])

  const value = useMemo<AuthContextValue>(
    () => ({ session, notice, login, logout }),
    [session, notice, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
