import { useMemo, useState } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { listActiveGatewayRoutes } from '../../api/gateway.ts'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Button } from '../../componenets/ui/Button.tsx'
import { Card, PageHeader } from '../../componenets/ui/Card.tsx'
import { EmptyState } from '../../componenets/ui/EmptyState.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { TextField } from '../../componenets/ui/TextField.tsx'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatDateTime } from '../../lib/format.ts'

export default function GatewayRoutesPage() {
  useDocumentTitle('Active routes')
  const { data, error, loading, reload } = useAsyncData(listActiveGatewayRoutes)
  const [query, setQuery] = useState('')

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase()
    return (data ?? []).filter(
      (route) =>
        needle === '' ||
        route.name.toLowerCase().includes(needle) ||
        route.routeId.toLowerCase().includes(needle) ||
        route.pathPattern.toLowerCase().includes(needle) ||
        route.targetBaseUrl.toLowerCase().includes(needle),
    )
  }, [data, query])

  function renderContent() {
    if (loading && !data) return <LoadingState label="Loading active routes..." />
    if (error) {
      return (
        <div className="p-4">
          <Banner
            tone="error"
            title="Could not load active routes"
            actions={
              <Button variant="secondary" size="sm" onClick={() => reload()}>
                Retry
              </Button>
            }
          >
            {getErrorMessage(error)}
          </Banner>
        </div>
      )
    }
    if (!data || data.length === 0) {
      return (
        <EmptyState
          title="No active routes"
          description="Routes appear here once a tenant activates them and the tenant's organization is active."
        />
      )
    }
    if (visible.length === 0) {
      return (
        <EmptyState
          title="No routes match your search"
          action={
            <Button variant="secondary" onClick={() => setQuery('')}>
              Clear search
            </Button>
          }
        />
      )
    }
    return (
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
          <caption className="sr-only">Active routes served by the gateway</caption>
          <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-4 py-3">Route</th>
              <th scope="col" className="px-4 py-3">Organization</th>
              <th scope="col" className="px-4 py-3">Path pattern</th>
              <th scope="col" className="px-4 py-3">Target</th>
              <th scope="col" className="px-4 py-3">Methods</th>
              <th scope="col" className="px-4 py-3">Limit</th>
              <th scope="col" className="px-4 py-3">Timeout</th>
              <th scope="col" className="px-4 py-3">Header rules</th>
              <th scope="col" className="px-4 py-3">Version</th>
              <th scope="col" className="px-4 py-3">Updated</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {visible.map((route) => (
              <tr key={route.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <p className="font-medium text-slate-900">{route.name}</p>
                  <p className="font-mono text-xs text-slate-500">{route.routeId}</p>
                </td>
                <td className="px-4 py-3 tabular-nums text-slate-600">{route.organizationId}</td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">{route.pathPattern}</td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">
                  <span className="break-all">
                    {route.targetBaseUrl}
                    {route.targetPathPrefix}
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-slate-600">{[...route.allowedMethods].sort().join(', ')}</td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                  {route.requestsPerMinute === null ? 'None' : `${route.requestsPerMinute}/min`}
                </td>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums text-slate-600">{route.responseTimeoutMs} ms</td>
                <td className="px-4 py-3 tabular-nums text-slate-600">{route.headerRules.length}</td>
                <td className="px-4 py-3 tabular-nums text-slate-600">v{route.configurationVersion}</td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(route.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  return (
    <>
      <PageHeader
        title="Active routes"
        description="The routes the gateway should currently serve, across all active organizations."
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
        <TextField
          label="Search active routes"
          hideLabel
          type="search"
          placeholder="Search by name, route ID, path or target"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <Button variant="secondary" onClick={() => reload()} loading={loading && data !== null}>
          Refresh
        </Button>
      </div>

      <Card>
        <div className="-mx-5 -my-4">{renderContent()}</div>
      </Card>

      {data && data.length > 0 ? (
        <p aria-live="polite" className="mt-3 text-xs text-slate-500">
          Showing {visible.length} of {data.length} active route{data.length === 1 ? '' : 's'}.
        </p>
      ) : null}
    </>
  )
}
