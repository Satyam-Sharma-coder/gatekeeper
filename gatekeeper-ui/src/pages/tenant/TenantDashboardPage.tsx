import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/authContext.ts'
import { getErrorMessage } from '../../api/client.ts'
import { listAuditRecords, listRoutes } from '../../api/routes.ts'
import { getOwnOrganization } from '../../api/tenantOrganization.ts'
import { SessionCard } from '../../componenets/session/SessionCard.tsx'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Card, PageHeader } from '../../componenets/ui/Card.tsx'
import { ComingSoonCard } from '../../componenets/ui/ComingSoonCard.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { StatusBadge } from '../../componenets/ui/StatusBadge.tsx'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatDateTime } from '../../lib/format.ts'

interface StatProps {
  label: string
  value: number
}

function Stat({ label, value }: StatProps) {
  return (
    <div className="rounded-lg bg-slate-50 px-4 py-3">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{value}</p>
    </div>
  )
}

export default function TenantDashboardPage() {
  useDocumentTitle('Tenant dashboard')
  const { session } = useAuth()
  const organization = useAsyncData(getOwnOrganization)
  const routes = useAsyncData(listRoutes)
  const audit = useAsyncData(listAuditRecords)
  if (!session) return null

  const activeCount = routes.data?.filter((route) => route.active).length ?? 0
  const recent = audit.data?.slice(0, 5) ?? []

  return (
    <>
      <PageHeader title="Tenant dashboard" description={`Signed in as ${session.username}.`} />
      <div className="space-y-6">
        {session.organizationId === null ? (
          <Banner tone="warning">
            Your account is not linked to an organization, so tenant features cannot be scoped. Contact a platform
            administrator.
          </Banner>
        ) : null}

        <div className="grid gap-6 lg:grid-cols-2">
          <Card
            title="Organization"
            actions={
              <Link to="/tenant/organization" className="text-sm text-indigo-700 hover:underline">
                Manage
              </Link>
            }
          >
            {organization.loading && !organization.data ? <LoadingState label="Loading organization..." /> : null}
            {organization.error ? <Banner tone="error">{getErrorMessage(organization.error)}</Banner> : null}
            {organization.data ? (
              <div className="space-y-1">
                <p className="text-lg font-semibold text-slate-900">{organization.data.name}</p>
                <p className="font-mono text-xs text-slate-500">/{organization.data.slug}/</p>
                <div className="pt-2">
                  <StatusBadge status={organization.data.status} />
                </div>
              </div>
            ) : null}
          </Card>

          <Card
            title="Routes"
            actions={
              <Link to="/tenant/routes" className="text-sm text-indigo-700 hover:underline">
                View all
              </Link>
            }
          >
            {routes.loading && !routes.data ? <LoadingState label="Loading routes..." /> : null}
            {routes.error ? <Banner tone="error">{getErrorMessage(routes.error)}</Banner> : null}
            {routes.data ? (
              <div className="grid grid-cols-3 gap-3">
                <Stat label="Total" value={routes.data.length} />
                <Stat label="Active" value={activeCount} />
                <Stat label="Inactive" value={routes.data.length - activeCount} />
              </div>
            ) : null}
          </Card>
        </div>

        <Card
          title="Recent activity"
          actions={
            <Link to="/tenant/audit" className="text-sm text-indigo-700 hover:underline">
              Full audit log
            </Link>
          }
        >
          {audit.loading && !audit.data ? <LoadingState label="Loading activity..." /> : null}
          {audit.error ? <Banner tone="error">{getErrorMessage(audit.error)}</Banner> : null}
          {audit.data && recent.length === 0 ? <p className="text-sm text-slate-500">No activity recorded yet.</p> : null}
          {recent.length > 0 ? (
            <ul className="divide-y divide-slate-100">
              {recent.map((record) => (
                <li key={record.id} className="flex flex-wrap items-baseline justify-between gap-2 py-2 text-sm">
                  <span className="text-slate-900">
                    {record.details ?? record.action}
                    {record.resourceId ? <span className="ml-2 font-mono text-xs text-slate-500">{record.resourceId}</span> : null}
                  </span>
                  <span className="text-xs text-slate-500">
                    {record.actor} &middot; {formatDateTime(record.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
          ) : null}
        </Card>

        <SessionCard session={session} organizationNote="Fixed by your account. You cannot switch organizations." />

        <ComingSoonCard
          title="Users"
          description="Manage who can access your organization."
          planned={['List users', 'Invite and disable users']}
        />
      </div>
    </>
  )
}
