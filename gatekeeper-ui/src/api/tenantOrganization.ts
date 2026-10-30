import { request } from './client.ts'
import type { Organization, TenantOrganizationRequest } from './types.ts'

const PATH = '/api/tenant/organization'

export function getOwnOrganization(signal?: AbortSignal): Promise<Organization> {
  return request<Organization>(PATH, {
    signal,
    messages: { 404: 'Your organization was not found.' },
  })
}

export function updateOwnOrganization(payload: TenantOrganizationRequest): Promise<Organization> {
  return request<Organization>(PATH, {
    method: 'PUT',
    body: payload,
    messages: { 404: 'Your organization was not found.', 409: 'An organization with this name already exists.' },
  })
}
