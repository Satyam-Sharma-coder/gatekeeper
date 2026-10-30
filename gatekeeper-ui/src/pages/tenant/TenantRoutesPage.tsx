import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../api/client.ts'
import { listRoutes } from '../../api/routes.ts'
import { getOwnOrganization } from '../../api/tenantOrganization.ts'
import type { ApiRoute } from '../../api/types.ts'
import { DeleteRouteDialog } from '../../componenets/routes/DeleteRouteDialog.tsx'
import { RouteFormModal } from '../../componenets/routes/RouteFormModal.tsx'
import { RouteStatusDialog } from '../../componenets/routes/RouteStatusDialog.tsx'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Button } from '../../componenets/ui/Button.tsx'
import { Card, PageHeader } from '../../componenets/ui/Card.tsx'
import { EmptyState } from '../../componenets/ui/EmptyState.tsx'
import { SelectField } from '../../componenets/ui/SelectField.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { FlagBadge } from '../../componenets/ui/StatusBadge.tsx'
import { TextField } from '../../componenets/ui/TextField.tsx'
import { useToast } from '../../componenets/ui/toastContext.ts'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatDateTime, parseTimestamp } from '../../lib/format.ts'

type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'
type SortKey = 'updated-desc' | 'name-asc' | 'name-desc'

type DialogState =
  | { kind: 'create' }
  | { kind: 'edit'; route: ApiRoute }
  | { kind: 'status'; route: ApiRoute }
  | { kind: 'delete'; route: ApiRoute }

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
]

const SORT_OPTIONS = [
  { value: 'updated-desc', label: 'Recently updated' },
  { value: 'name-asc', label: 'Name A to Z' },
  { value: 'name-desc', label: 'Name Z to A' },
]

function isStatusFilter(value: string): value is StatusFilter {
  return STATUS_OPTIONS.some((option) => option.value === value)
}

function isSortKey(value: string): value is SortKey {
  return SORT_OPTIONS.some((option) => option.value === value)
}

function sortRoutes(items: ApiRoute[], sort: SortKey): ApiRoute[] {
  const sorted = [...items]
  const byName = (a: ApiRoute, b: ApiRoute) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
  switch (sort) {
    case 'name-asc':
      return sorted.sort(byName)
    case 'name-desc':
      return sorted.sort((a, b) => byName(b, a))
    case 'updated-desc':
      return sorted.sort((a, b) => (parseTimestamp(b.updatedAt) ?? 0) - (parseTimestamp(a.updatedAt) ?? 0))
  }
}

export default function TenantRoutesPage() {
  useDocumentTitle('Routes')
  const toast = useToast()
  const { data, error, loading, reload } = useAsyncData(listRoutes)
  const organization = useAsyncData(getOwnOrganization)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [sort, setSort] = useState<SortKey>('updated-desc')
  const [dialog, setDialog] = useState<DialogState | null>(null)

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase()
    const filtered = (data ?? []).filter(
      (route) =>
        (statusFilter === 'ALL' || (statusFilter === 'ACTIVE') === route.active) &&
        (needle === '' ||
          route.name.toLowerCase().includes(needle) ||
          route.routeId.toLowerCase().includes(needle) ||
          route.pathPattern.toLowerCase().includes(needle)),
    )
    return sortRoutes(filtered, sort)
  }, [data, query, statusFilter, sort])

  const filtersActive = query.trim() !== '' || statusFilter !== 'ALL'
  const slug = organization.data?.slug ?? null

  function closeDialog() {
    setDialog(null)
  }

  function handleSaved(route: ApiRoute, mode: 'created' | 'updated') {
    toast.success(`Route "${route.name}" ${mode}.`)
    closeDialog()
    reload()
  }

  function handleStatusChanged(route: ApiRoute) {
    toast.success(`"${route.name}" is now ${route.active ? 'active' : 'inactive'}.`)
    closeDialog()
    reload()
  }

  function handleDeleted(route: ApiRoute) {
    toast.success(`Route "${route.name}" deleted.`)
    closeDialog()
    reload()
  }

  function clearFilters() {
    setQuery('')
    setStatusFilter('ALL')
  }

  function renderContent() {
    if (loading && !data) return <LoadingState label="Loading routes..." />
    if (error) {
      return (
        <div className="p-4">
          <Banner
            tone="error"
            title="Could not load routes"
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
          title="No routes yet"
          description="Create your first route to let the gateway forward requests to your services."
          action={<Button onClick={() => setDialog({ kind: 'create' })}>Create route</Button>}
        />
      )
    }
    if (visible.length === 0) {
      return (
        <EmptyState
          title="No routes match your filters"
          action={
            <Button variant="secondary" onClick={clearFilters}>
              Clear filters
            </Button>
          }
        />
      )
    }
    return (
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
          <caption className="sr-only">Routes</caption>
          <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-4 py-3">Name</th>
              <th scope="col" className="px-4 py-3">Path pattern</th>
              <th scope="col" className="px-4 py-3">Target</th>
              <th scope="col" className="px-4 py-3">Methods</th>
              <th scope="col" className="px-4 py-3">Status</th>
              <th scope="col" className="px-4 py-3">Version</th>
              <th scope="col" className="px-4 py-3">Updated</th>
              <th scope="col" className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {visible.map((route) => (
              <tr key={route.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/tenant/routes/${route.id}`} className="font-medium text-indigo-700 hover:underline">
                    {route.name}
                  </Link>
                  <p className="font-mono text-xs text-slate-500">{route.routeId}</p>
                </td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">{route.pathPattern}</td>
                <td className="max-w-56 truncate px-4 py-3 font-mono text-xs text-slate-600" title={route.targetBaseUrl}>
                  {route.targetBaseUrl}
                </td>
                <td className="px-4 py-3 text-xs text-slate-600">{[...route.allowedMethods].sort().join(', ')}</td>
                <td className="px-4 py-3">
                  <FlagBadge on={route.active} onLabel="Active" offLabel="Inactive" />
                </td>
                <td className="px-4 py-3 tabular-nums text-slate-600">v{route.configurationVersion}</td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(route.updatedAt)}</td>
                <td className="whitespace-nowrap px-4 py-3 text-right">
                  <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => setDialog({ kind: 'edit', route })}>
                      Edit<span className="sr-only"> {route.name}</span>
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setDialog({ kind: 'status', route })}>
                      {route.active ? 'Deactivate' : 'Activate'}
                      <span className="sr-only"> {route.name}</span>
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-red-600 hover:bg-red-50 hover:text-red-700"
                      onClick={() => setDialog({ kind: 'delete', route })}
                    >
                      Delete<span className="sr-only"> {route.name}</span>
                    </Button>
                  </div>
                </td>
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
        title="Routes"
        description="API routes the gateway serves for your organization."
        actions={<Button onClick={() => setDialog({ kind: 'create' })}>Create route</Button>}
      />

      {organization.error ? (
        <div className="mb-4">
          <Banner tone="warning">
            Your organization prefix could not be loaded, so the path pattern is only checked by the server when you
            save.
          </Banner>
        </div>
      ) : null}

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_12rem_12rem_auto] lg:items-end">
        <TextField
          label="Search routes"
          hideLabel
          type="search"
          placeholder="Search by name, route ID or path"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <SelectField
          label="Filter by status"
          hideLabel
          value={statusFilter}
          onChange={(event) => {
            if (isStatusFilter(event.target.value)) setStatusFilter(event.target.value)
          }}
          options={STATUS_OPTIONS}
        />
        <SelectField
          label="Sort by"
          hideLabel
          value={sort}
          onChange={(event) => {
            if (isSortKey(event.target.value)) setSort(event.target.value)
          }}
          options={SORT_OPTIONS}
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
          Showing {visible.length} of {data.length} route{data.length === 1 ? '' : 's'}
          {filtersActive ? ' (filtered)' : ''}.
        </p>
      ) : null}

      {dialog?.kind === 'create' ? (
        <RouteFormModal organizationSlug={slug} onClose={closeDialog} onSaved={handleSaved} />
      ) : null}
      {dialog?.kind === 'edit' ? (
        <RouteFormModal route={dialog.route} organizationSlug={slug} onClose={closeDialog} onSaved={handleSaved} />
      ) : null}
      {dialog?.kind === 'status' ? (
        <RouteStatusDialog route={dialog.route} onClose={closeDialog} onChanged={handleStatusChanged} />
      ) : null}
      {dialog?.kind === 'delete' ? (
        <DeleteRouteDialog route={dialog.route} onClose={closeDialog} onDeleted={handleDeleted} />
      ) : null}
    </>
  )
}
