export const ROLES = ['PLATFORM_ADMIN', 'TENANT_ADMIN', 'GATEWAY_SERVICE'] as const
export type Role = (typeof ROLES)[number]

export const ORGANIZATION_STATUSES = ['PENDING', 'ACTIVE', 'SUSPENDED', 'DELETED'] as const
export type OrganizationStatus = (typeof ORGANIZATION_STATUSES)[number]

export const SETTABLE_STATUSES = ['PENDING', 'ACTIVE', 'SUSPENDED'] as const
export type SettableOrganizationStatus = (typeof SETTABLE_STATUSES)[number]

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  role: Role
  organizationId: number | null
}

export interface Organization {
  id: number
  name: string
  slug: string
  status: OrganizationStatus
  createdAt: string
  updatedAt: string
}

export interface OrganizationRequest {
  name: string
  slug: string
}

export interface OrganizationStatusRequest {
  status: SettableOrganizationStatus
}

export interface TenantAdmin {
  id: number
  organizationId: number
  username: string
  enabled: boolean
}

export interface TenantAdminRequest {
  username: string
  password: string
  enabled: boolean
}

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value)
}

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const
export type HttpMethodName = (typeof HTTP_METHODS)[number]

export const HEADER_DIRECTIONS = ['REQUEST', 'RESPONSE'] as const
export type HeaderDirection = (typeof HEADER_DIRECTIONS)[number]

export const HEADER_ACTIONS = ['ADD', 'REMOVE'] as const
export type HeaderAction = (typeof HEADER_ACTIONS)[number]

export interface HeaderRule {
  id: number
  direction: string
  action: string
  headerName: string
  headerValue: string | null
}

export interface HeaderRuleRequest {
  direction: HeaderDirection
  action: HeaderAction
  headerName: string
  headerValue: string | null
}

export interface ApiRoute {
  id: number
  organizationId: number
  routeId: string
  name: string
  pathPattern: string
  targetBaseUrl: string
  targetPathPrefix: string
  requestsPerMinute: number | null
  responseTimeoutMs: number
  idempotencyEnabled: boolean
  active: boolean
  configurationVersion: number
  allowedMethods: string[]
  headerRules: HeaderRule[]
  createdAt: string
  updatedAt: string
}

export interface RouteRequest {
  routeId: string
  name: string
  pathPattern: string
  targetBaseUrl: string
  targetPathPrefix: string
  requestsPerMinute: number | null
  responseTimeoutMs: number
  idempotencyEnabled: boolean
  active: boolean
  allowedMethods: string[]
  headerRules: HeaderRuleRequest[]
}

export interface RouteStatusRequest {
  active: boolean
}

export interface RouteVersion {
  id: number
  routeId: string
  versionNumber: number
  changeType: string
  configurationSnapshot: string
  createdAt: string
}

export interface AuditRecord {
  id: number
  actor: string
  action: string
  resourceType: string
  resourceId: string | null
  correlationId: string | null
  durationMs: number | null
  result: string
  details: string | null
  createdAt: string
}

export interface TenantOrganizationRequest {
  name: string
}
