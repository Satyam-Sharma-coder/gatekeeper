import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getErrorMessage } from '../../api/client.ts'
import { getRoute } from '../../api/routes.ts'
import { getOwnOrganization } from '../../api/tenantOrganization.ts'
import type { ApiRoute } from '../../api/types.ts'
import { DeleteRouteDialog } from '../../componenets/routes/DeleteRouteDialog.tsx'
import { RouteFormModal } from '../../componenets/routes/RouteFormModal.tsx'
import { RouteStatusDialog } from '../../componenets/routes/RouteStatusDialog.tsx'
import { RouteVersionsPanel } from '../../componenets/routes/RouteVersionsPanel.tsx'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Button } from '../../componenets/ui/Button.tsx'
import { Card, DetailList, PageHeader } from '../../componenets/ui/Card.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { FlagBadge } from '../../componenets/ui/StatusBadge.tsx'
import { useToast } from '../../componenets/ui/toastContext.ts'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatDateTime } from '../../lib/format.ts'

type DialogKind = 'edit' | 'status' | 'delete'

function BackLink() {
  return (
    <Link to="/tenant/routes" className="mb-2 inline-block text-sm text-indigo-700 hover:underline">
      &larr; All routes
    </Link>
  )
}

function Mono({ children }: { children: string }) {
  return <span className="break-all font-mono text-xs">{children}</span>
}

export default function TenantRouteDetailPage() {
  const { id: idParam } = useParams()
  const id = Number(idParam)
  const validId = Number.isInteger(id) && id > 0
  const navigate = useNavigate()
  const toast = useToast()
  const loader = useCallback(
    (signal: AbortSignal): Promise<ApiRoute> =>
      validId ? getRoute(id, signal) : Promise.reject(new Error('Invalid route id')),
    [id, validId],
  )
  const { data, error, loading, reload } = useAsyncData(loader)
  const organization = useAsyncData(getOwnOrganization)
  const [dialog, setDialog] = useState<DialogKind | null>(null)

  useDocumentTitle(data?.name ?? 'Route')

  if (!validId) {
    return (
      <>
        <BackLink />
        <Banner tone="error">That route address is not valid.</Banner>
      </>
    )
  }

  if (loading && !data) {
    return (
      <>
        <BackLink />
        <LoadingState label="Loading route..." />
      </>
    )
  }

  if (error || !data) {
    return (
      <>
        <BackLink />
        <Banner
          tone="error"
          title="Could not load the route"
          actions={
            <Button variant="secondary" size="sm" onClick={() => reload()}>
              Retry
            </Button>
          }
        >
          {getErrorMessage(error)}
        </Banner>
      </>
    )
  }

  function closeDialog() {
    setDialog(null)
  }

  function handleChanged(message: string) {
    toast.success(message)
    closeDialog()
    reload()
  }

  return (
    <>
      <PageHeader
        title={data.name}
        description={`Route ID: ${data.routeId}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => setDialog('edit')}>
              Edit
            </Button>
            <Button variant="secondary" onClick={() => setDialog('status')}>
              {data.active ? 'Deactivate' : 'Activate'}
            </Button>
            <Button variant="danger" onClick={() => setDialog('delete')}>
              Delete
            </Button>
          </>
        }
      >
        <BackLink />
      </PageHeader>

      <div className="space-y-6">
        <Card title="Route details">
          <DetailList
            items={[
              { label: 'Status', value: <FlagBadge on={data.active} onLabel="Active" offLabel="Inactive" /> },
              { label: 'Configuration version', value: `v${data.configurationVersion}` },
              { label: 'Path pattern', value: <Mono>{data.pathPattern}</Mono> },
              { label: 'Target base URL', value: <Mono>{data.targetBaseUrl}</Mono> },
              { label: 'Target path prefix', value: <Mono>{data.targetPathPrefix}</Mono> },
              { label: 'Allowed methods', value: [...data.allowedMethods].sort().join(', ') || 'None' },
              {
                label: 'Rate limit',
                value: data.requestsPerMinute === null ? 'No limit' : `${data.requestsPerMinute} requests per minute`,
              },
              { label: 'Response timeout', value: `${data.responseTimeoutMs} ms` },
              { label: 'Idempotency', value: data.idempotencyEnabled ? 'Enabled' : 'Disabled' },
              { label: 'Created', value: formatDateTime(data.createdAt) },
              { label: 'Last updated', value: formatDateTime(data.updatedAt) },
            ]}
          />
        </Card>

        <Card title="Header rules" description="Applied by the gateway as requests and responses pass through.">
          {data.headerRules.length === 0 ? (
            <p className="text-sm text-slate-500">No header rules. Headers pass through unchanged.</p>
          ) : (
            <div className="-mx-5 -my-4 overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                <caption className="sr-only">Header rules</caption>
                <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-4 py-3">Direction</th>
                    <th scope="col" className="px-4 py-3">Action</th>
                    <th scope="col" className="px-4 py-3">Header</th>
                    <th scope="col" className="px-4 py-3">Value</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.headerRules.map((rule) => (
                    <tr key={rule.id}>
                      <td className="px-4 py-3 text-slate-700">{rule.direction.toLowerCase()}</td>
                      <td className="px-4 py-3 text-slate-700">{rule.action.toLowerCase()}</td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-900">{rule.headerName}</td>
                      <td className="break-all px-4 py-3 font-mono text-xs text-slate-600">
                        {rule.headerValue ?? '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <RouteVersionsPanel routeId={data.routeId} currentVersion={data.configurationVersion} />
      </div>

      {dialog === 'edit' ? (
        <RouteFormModal
          route={data}
          organizationSlug={organization.data?.slug ?? null}
          onClose={closeDialog}
          onSaved={(route) => handleChanged(`Route "${route.name}" updated.`)}
        />
      ) : null}
      {dialog === 'status' ? (
        <RouteStatusDialog
          route={data}
          onClose={closeDialog}
          onChanged={(route) => handleChanged(`"${route.name}" is now ${route.active ? 'active' : 'inactive'}.`)}
        />
      ) : null}
      {dialog === 'delete' ? (
        <DeleteRouteDialog
          route={data}
          onClose={closeDialog}
          onDeleted={(route) => {
            toast.success(`Route "${route.name}" deleted.`)
            navigate('/tenant/routes', { replace: true })
          }}
        />
      ) : null}
    </>
  )
}
