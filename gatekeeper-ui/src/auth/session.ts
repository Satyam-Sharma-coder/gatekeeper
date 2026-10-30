import { isRole } from '../api/types.ts'
import type { Role } from '../api/types.ts'
import { isRecord } from '../lib/guards.ts'

const STORAGE_KEY = 'gatekeeper.session'

export interface Session {
  token: string
  username: string
  role: Role
  organizationId: number | null
  issuedAt: number
  expiresAt: number
}

function isSession(value: unknown): value is Session {
  return (
    isRecord(value) &&
    typeof value.token === 'string' &&
    typeof value.username === 'string' &&
    isRole(value.role) &&
    (value.organizationId === null || typeof value.organizationId === 'number') &&
    typeof value.issuedAt === 'number' &&
    typeof value.expiresAt === 'number'
  )
}

export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!isSession(parsed) || parsed.expiresAt <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function saveSession(session: Session): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // storage unavailable: the session then lives only in memory until reload
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // nothing to clear
  }
}

export function readStoredToken(): string | null {
  return loadSession()?.token ?? null
}
