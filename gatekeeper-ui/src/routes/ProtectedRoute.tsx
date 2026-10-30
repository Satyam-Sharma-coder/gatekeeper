import { Navigate, Outlet } from 'react-router-dom'
import type { Role } from '../api/types.ts'
import { useAuth } from '../auth/authContext.ts'
import { roleHome } from '../auth/roles.ts'

interface ProtectedRouteProps {
  allowedRoles: readonly Role[]
}

export function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const { session } = useAuth()

  if (!session) return <Navigate to="/login" replace />
  if (!allowedRoles.includes(session.role)) return <Navigate to={roleHome(session.role)} replace />
  return <Outlet />
}

export function GuestRoute() {
  const { session } = useAuth()

  if (session) return <Navigate to={roleHome(session.role)} replace />
  return <Outlet />
}

export function RootRedirect() {
  const { session } = useAuth()

  return <Navigate to={session ? roleHome(session.role) : '/login'} replace />
}
