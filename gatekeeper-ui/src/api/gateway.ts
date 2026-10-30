import { request } from './client.ts'
import type { ApiRoute } from './types.ts'

export function listActiveGatewayRoutes(signal?: AbortSignal): Promise<ApiRoute[]> {
  return request<ApiRoute[]>('/api/gateway/routes', { signal })
}
