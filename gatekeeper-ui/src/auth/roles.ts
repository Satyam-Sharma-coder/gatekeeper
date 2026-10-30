import type { Role } from '../api/types.ts'

const HOME: Readonly<Record<Role, string>> = {
  PLATFORM_ADMIN: '/platform/organizations',
  TENANT_ADMIN: '/tenant',
  GATEWAY_SERVICE: '/gateway',
}

const LABELS: Readonly<Record<Role, string>> = {
  PLATFORM_ADMIN: 'Platform admin',
  TENANT_ADMIN: 'Tenant admin',
  GATEWAY_SERVICE: 'Gateway service',
}

export function roleHome(role: Role): string {
  return HOME[role]
}

export function roleLabel(role: Role): string {
  return LABELS[role]
}
