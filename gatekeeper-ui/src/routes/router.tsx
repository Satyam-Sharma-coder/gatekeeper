import type { ComponentType } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { LoadingState } from '../componenets/ui/Spinner.tsx'
import { AppShell } from '../componenets/layout/AppShell.tsx'
import type { NavItem } from '../componenets/layout/AppShell.tsx'
import LoginPage from '../pages/LoginPage.tsx'
import NotFoundPage from '../pages/NotFoundPage.tsx'
import { GuestRoute, ProtectedRoute, RootRedirect } from './ProtectedRoute.tsx'

const PLATFORM_NAV: readonly NavItem[] = [{ to: '/platform/organizations', label: 'Organizations' }]

const TENANT_NAV: readonly NavItem[] = [
  { to: '/tenant', label: 'Dashboard', end: true },
  { to: '/tenant/routes', label: 'Routes' },
  { to: '/tenant/audit', label: 'Audit log' },
  { to: '/tenant/organization', label: 'Organization' },
  { to: '/tenant/users', label: 'Users' },
]

const GATEWAY_NAV: readonly NavItem[] = [
  { to: '/gateway', label: 'Status', end: true },
  { to: '/gateway/routes', label: 'Active routes' },
  { to: '/gateway/registration', label: 'Registration' },
]

interface PageModule {
  default: ComponentType
}

async function page(module: Promise<PageModule>) {
  return { Component: (await module).default }
}

export const router = createBrowserRouter([
  { path: '/', element: <RootRedirect /> },
  {
    element: <GuestRoute />,
    children: [{ path: '/login', element: <LoginPage /> }],
  },
  {
    element: <ProtectedRoute allowedRoles={['PLATFORM_ADMIN']} />,
    children: [
      {
        path: '/platform',
        element: <AppShell areaLabel="Platform administration" navItems={PLATFORM_NAV} />,
        hydrateFallbackElement: <LoadingState />,
        children: [
          { index: true, element: <Navigate to="/platform/organizations" replace /> },
          { path: 'organizations', lazy: () => page(import('../pages/platform/OrganizationsPage.tsx')) },
          { path: 'organizations/:id', lazy: () => page(import('../pages/platform/OrganizationDetailPage.tsx')) },
        ],
      },
    ],
  },
  {
    element: <ProtectedRoute allowedRoles={['TENANT_ADMIN']} />,
    children: [
      {
        path: '/tenant',
        element: <AppShell areaLabel="Tenant administration" navItems={TENANT_NAV} />,
        hydrateFallbackElement: <LoadingState />,
        children: [
          { index: true, lazy: () => page(import('../pages/tenant/TenantDashboardPage.tsx')) },
          { path: 'routes', lazy: () => page(import('../pages/tenant/TenantRoutesPage.tsx')) },
          { path: 'routes/:id', lazy: () => page(import('../pages/tenant/TenantRouteDetailPage.tsx')) },
          { path: 'audit', lazy: () => page(import('../pages/tenant/TenantAuditPage.tsx')) },
          { path: 'organization', lazy: () => page(import('../pages/tenant/TenantOrganizationPage.tsx')) },
          { path: 'users', lazy: () => page(import('../pages/tenant/TenantUsersPage.tsx')) },
        ],
      },
    ],
  },
  {
    element: <ProtectedRoute allowedRoles={['GATEWAY_SERVICE']} />,
    children: [
      {
        path: '/gateway',
        element: <AppShell areaLabel="Gateway service" navItems={GATEWAY_NAV} />,
        hydrateFallbackElement: <LoadingState />,
        children: [
          { index: true, lazy: () => page(import('../pages/gateway/GatewayStatusPage.tsx')) },
          { path: 'routes', lazy: () => page(import('../pages/gateway/GatewayRoutesPage.tsx')) },
          { path: 'registration', lazy: () => page(import('../pages/gateway/GatewayRegistrationPage.tsx')) },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
