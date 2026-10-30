import { readStoredToken } from '../auth/session.ts'
import { isRecord } from '../lib/guards.ts'

const rawBaseUrl: unknown = import.meta.env.VITE_API_BASE_URL
const BASE_URL = typeof rawBaseUrl === 'string' ? rawBaseUrl.trim().replace(/\/+$/, '') : ''

const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.'

const GENERIC_REASONS = new Set([
  'bad request',
  'unauthorized',
  'forbidden',
  'not found',
  'conflict',
  'internal server error',
  'no message available',
])

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  method?: HttpMethod
  body?: unknown
  auth?: boolean
  messages?: Partial<Record<number, string>>
  signal?: AbortSignal
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

export function getErrorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
}

export function getErrorStatus(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null
}

function defaultMessage(status: number): string {
  if (status === 400 || status === 422) return 'The request was rejected. Please check the values you entered.'
  if (status === 401) return SESSION_EXPIRED_MESSAGE
  if (status === 403) return 'You do not have permission to perform this action.'
  if (status === 404) return 'The requested item was not found.'
  if (status === 409) return 'This conflicts with an existing record, for example a duplicate name or username.'
  if (status >= 500) return 'The server ran into a problem. Please try again later.'
  return `The request failed (HTTP ${status}).`
}

function usableText(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const text = value.trim()
  if (!text || text.length > 300 || GENERIC_REASONS.has(text.toLowerCase())) return null
  return text
}

function extractMessage(text: string): string | null {
  if (!text.trim()) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    return null
  }
  if (!isRecord(parsed)) return null
  const direct = usableText(parsed.message) ?? usableText(parsed.detail) ?? usableText(parsed.error)
  if (direct) return direct
  if (Array.isArray(parsed.errors)) {
    for (const item of parsed.errors as unknown[]) {
      if (isRecord(item)) {
        const nested = usableText(item.defaultMessage) ?? usableText(item.message)
        if (nested) return nested
      }
    }
  }
  return null
}

async function execute(path: string, options: RequestOptions): Promise<unknown> {
  const { method = 'GET', body, auth = true, messages, signal } = options
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const token = readStoredToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.')
  }

  const text = await response.text().catch(() => '')
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')

  if (response.ok) {
    if (!text.trim()) return undefined
    try {
      return JSON.parse(text) as unknown
    } catch {
      throw new ApiError(response.status, 'The server returned a response that could not be read.')
    }
  }

  if (response.status === 401 && auth) {
    unauthorizedHandler?.()
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE)
  }

  throw new ApiError(
    response.status,
    extractMessage(text) ?? messages?.[response.status] ?? defaultMessage(response.status),
  )
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const result = await execute(path, options)
  if (result === undefined) throw new ApiError(0, 'The server returned an empty response.')
  return result as T
}

export async function requestVoid(path: string, options: RequestOptions = {}): Promise<void> {
  await execute(path, options)
}
