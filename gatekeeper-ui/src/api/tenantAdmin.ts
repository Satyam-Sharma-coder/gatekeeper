import { request, requestVoid } from './client.ts'
import type { TenantAdmin, TenantAdminRequest } from './types.ts'

const CONFLICT_MESSAGE = 'This organization already has a tenant admin, or the username is taken.'

function path(organizationId: number): string {
  return `/api/platform/organizations/${organizationId}/tenant-admin`
}

export function getTenantAdmin(organizationId: number, signal?: AbortSignal): Promise<TenantAdmin> {
  return request<TenantAdmin>(path(organizationId), {
    signal,
    messages: { 404: 'Tenant administrator not found.' },
  })
}

export function createTenantAdmin(
  organizationId: number,
  payload: TenantAdminRequest,
): Promise<TenantAdmin> {
  return request<TenantAdmin>(path(organizationId), {
    method: 'POST',
    body: payload,
    messages: { 409: CONFLICT_MESSAGE },
  })
}

export function updateTenantAdmin(
  organizationId: number,
  payload: TenantAdminRequest,
): Promise<TenantAdmin> {
  return request<TenantAdmin>(path(organizationId), {
    method: 'PUT',
    body: payload,
    messages: { 404: 'Tenant administrator not found.', 409: 'That username is already taken.' },
  })
}

export function deleteTenantAdmin(organizationId: number): Promise<void> {
  return requestVoid(path(organizationId), {
    method: 'DELETE',
    messages: { 404: 'Tenant administrator not found.' },
  })
}
