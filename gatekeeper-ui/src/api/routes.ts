import { request, requestVoid } from './client.ts'
import type { ApiRoute, AuditRecord, RouteRequest, RouteStatusRequest, RouteVersion } from './types.ts'

const BASE = '/api/tenant/routes'
const NOT_FOUND = 'Route not found.'

export function listRoutes(signal?: AbortSignal): Promise<ApiRoute[]> {
  return request<ApiRoute[]>(BASE, { signal })
}

export function getRoute(id: number, signal?: AbortSignal): Promise<ApiRoute> {
  return request<ApiRoute>(`${BASE}/${id}`, { signal, messages: { 404: NOT_FOUND } })
}

export function createRoute(payload: RouteRequest): Promise<ApiRoute> {
  return request<ApiRoute>(BASE, {
    method: 'POST',
    body: payload,
    messages: { 409: 'A route with this Route ID already exists in your organization.' },
  })
}

export function updateRoute(id: number, payload: RouteRequest): Promise<ApiRoute> {
  return request<ApiRoute>(`${BASE}/${id}`, {
    method: 'PUT',
    body: payload,
    messages: { 404: NOT_FOUND },
  })
}

export function changeRouteStatus(id: number, active: boolean): Promise<ApiRoute> {
  const body: RouteStatusRequest = { active }
  return request<ApiRoute>(`${BASE}/${id}/status`, {
    method: 'PATCH',
    body,
    messages: { 404: NOT_FOUND },
  })
}

export function deleteRoute(id: number): Promise<void> {
  return requestVoid(`${BASE}/${id}`, { method: 'DELETE', messages: { 404: NOT_FOUND } })
}

export function listRouteVersions(routeId: string, signal?: AbortSignal): Promise<RouteVersion[]> {
  return request<RouteVersion[]>(`/api/tenant/route-versions/${encodeURIComponent(routeId)}`, { signal })
}

export function listAuditRecords(signal?: AbortSignal): Promise<AuditRecord[]> {
  return request<AuditRecord[]>('/api/tenant/audit-records', { signal })
}
