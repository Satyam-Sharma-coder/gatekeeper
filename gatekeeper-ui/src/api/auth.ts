import { isRecord } from '../lib/guards.ts'
import { ApiError, request } from './client.ts'
import type { LoginRequest, LoginResponse } from './types.ts'
import { isRole } from './types.ts'

function isLoginResponse(value: unknown): value is LoginResponse {
  return (
    isRecord(value) &&
    typeof value.token === 'string' &&
    value.token.length > 0 &&
    typeof value.expiresInSeconds === 'number' &&
    isRole(value.role) &&
    (value.organizationId === null ||
      value.organizationId === undefined ||
      typeof value.organizationId === 'number')
  )
}

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const data = await request<unknown>('/api/auth/login', {
    method: 'POST',
    body: credentials,
    auth: false,
    messages: {
      401: 'Invalid username or password.',
      403: 'Your organization is not active. Contact your platform administrator.',
      500: 'Sign-in failed. Check your username and password, or try again later.',
    },
  })
  if (!isLoginResponse(data)) {
    throw new ApiError(0, 'The server returned an unexpected sign-in response.')
  }
  return { ...data, organizationId: data.organizationId ?? null }
}
