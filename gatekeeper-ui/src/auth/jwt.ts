import { isRecord } from '../lib/guards.ts'

export interface JwtClaims {
  sub: string | null
  exp: number | null
  iat: number | null
}

function decodeBase64Url(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  return new TextDecoder().decode(Uint8Array.from(binary, (char) => char.charCodeAt(0)))
}

export function parseJwt(token: string): JwtClaims | null {
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const payload: unknown = JSON.parse(decodeBase64Url(parts[1]))
    if (!isRecord(payload)) return null
    return {
      sub: typeof payload.sub === 'string' ? payload.sub : null,
      exp: typeof payload.exp === 'number' ? payload.exp : null,
      iat: typeof payload.iat === 'number' ? payload.iat : null,
    }
  } catch {
    return null
  }
}
