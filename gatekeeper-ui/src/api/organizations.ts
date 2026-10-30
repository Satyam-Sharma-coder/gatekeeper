import { request, requestVoid } from './client.ts'
import type {
  Organization,
  OrganizationRequest,
  OrganizationStatusRequest,
  SettableOrganizationStatus,
} from './types.ts'

const BASE = '/api/platform/organizations'

const DUPLICATE_MESSAGE = 'An organization with this name or slug already exists.'

export function listOrganizations(signal?: AbortSignal): Promise<Organization[]> {
  return request<Organization[]>(BASE, { signal })
}

export function getOrganization(id: number, signal?: AbortSignal): Promise<Organization> {
  return request<Organization>(`${BASE}/${id}`, {
    signal,
    messages: { 404: 'Organization not found.' },
  })
}

export function createOrganization(payload: OrganizationRequest): Promise<Organization> {
  return request<Organization>(BASE, {
    method: 'POST',
    body: payload,
    messages: { 409: DUPLICATE_MESSAGE },
  })
}

export function updateOrganization(id: number, payload: OrganizationRequest): Promise<Organization> {
  return request<Organization>(`${BASE}/${id}`, {
    method: 'PUT',
    body: payload,
    messages: { 404: 'Organization not found.', 409: DUPLICATE_MESSAGE },
  })
}

export function updateOrganizationStatus(
  id: number,
  status: SettableOrganizationStatus,
): Promise<Organization> {
  const body: OrganizationStatusRequest = { status }
  return request<Organization>(`${BASE}/${id}/status`, {
    method: 'PATCH',
    body,
    messages: { 404: 'Organization not found.' },
  })
}

export function deleteOrganization(id: number): Promise<void> {
  return requestVoid(`${BASE}/${id}`, {
    method: 'DELETE',
    messages: { 404: 'Organization not found.' },
  })
}
