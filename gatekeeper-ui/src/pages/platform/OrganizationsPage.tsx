import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../api/client.ts'
import { listOrganizations } from '../../api/organizations.ts'
import type { Organization, OrganizationStatus } from '../../api/types.ts'
import { DeleteOrganizationDialog } from '../../componenets/organizations/DeleteOrganizationDialog.tsx'
import { OrganizationFormModal } from '../../componenets/organizations/OrganizationFormModal.tsx'
import { OrganizationStatusModal } from '../../componenets/organizations/OrganizationStatusModal.tsx'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Button } from '../../componenets/ui/Button.tsx'
import { Card, PageHeader } from '../../componenets/ui/Card.tsx'
import { EmptyState } from '../../componenets/ui/EmptyState.tsx'
import { SelectField } from '../../componenets/ui/SelectField.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { StatusBadge } from '../../componenets/ui/StatusBadge.tsx'
import { TextField } from '../../componenets/ui/TextField.tsx'
import { useToast } from '../../componenets/ui/toastContext.ts'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatDateTime, parseTimestamp } from '../../lib/format.ts'

type StatusFilter = 'ALL' | Exclude<OrganizationStatus, 'DELETED'>
type SortKey = 'name-asc' | 'name-desc' | 'created-desc' | 'created-asc'

type DialogState =
  | { kind: 'create' }
  | { kind: 'edit'; organization: Organization }
  | { kind: 'status'; organization: Organization }
  | { kind: 'delete'; organization: Organization }

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'SUSPENDED', label: 'Suspended' },
]

const SORT_OPTIONS = [
  { value: 'created-desc', label: 'Newest first' },
  { value: 'created-asc', label: 'Oldest first' },
  { value: 'name-asc', label: 'Name A to Z' },
  { value: 'name-desc', label: 'Name Z to A' },
]

function isStatusFilter(value: string): value is StatusFilter {
  return STATUS_OPTIONS.some((option) => option.value === value)
}

function isSortKey(value: string): value is SortKey {
  return SORT_OPTIONS.some((option) => option.value === value)
}

function sortOrganizations(items: Organization[], sort: SortKey): Organization[] {
  const sorted = [...items]
  const byName = (a: Organization, b: Organization) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
  const byCreated = (a: Organization, b: Organization) =>
    (parseTimestamp(a.createdAt) ?? 0) - (parseTimestamp(b.createdAt) ?? 0)
  switch (sort) {
    case 'name-asc':
      return sorted.sort(byName)
    case 'name-desc':
      return sorted.sort((a, b) => byName(b, a))
    case 'created-asc':
      return sorted.sort(byCreated)
    case 'created-desc':
      return sorted.sort((a, b) => byCreated(b, a))
  }
}

export default function OrganizationsPage() {
  useDocumentTitle('Organizations')
  const toast = useToast()
  const { data, error, loading, reload } = useAsyncData(listOrganizations)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [sort, setSort] = useState<SortKey>('created-desc')
  const [dialog, setDialog] = useState<DialogState | null>(null)

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase()
    const filtered = (data ?? []).filter(
      (org) =>
        (statusFilter === 'ALL' || org.status === statusFilter) &&
        (needle === '' || org.name.toLowerCase().includes(needle) || org.slug.toLowerCase().includes(needle)),
    )
    return sortOrganizations(filtered, sort)
  }, [data, query, statusFilter, sort])

  const filtersActive = query.trim() !== '' || statusFilter !== 'ALL'

  function closeDialog() {
    setDialog(null)
  }

  function handleSaved(organization: Organization, mode: 'created' | 'updated') {
    toast.success(`Organization "${organization.name}" ${mode}.`)
    closeDialog()
    reload()
  }

  function handleStatusSaved(organization: Organization) {
    toast.success(`"${organization.name}" is now ${organization.status.toLowerCase()}.`)
    closeDialog()
    reload()
  }

  function handleDeleted(organization: Organization) {
    toast.success(`Organization "${organization.name}" deleted.`)
    closeDialog()
    reload()
  }

  function clearFilters() {
    setQuery('')
    setStatusFilter('ALL')
  }

  function renderContent() {
    if (loading && !data) return <LoadingState label="Loading organizations..." />
    if (error) {
      return (
        <div className="p-4">
          <Banner
            tone="error"
            title="Could not load organizations"
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
          title="No organizations yet"
          description="Create the first organization (tenant) to get started."
          action={<Button onClick={() => setDialog({ kind: 'create' })}>Create organization</Button>}
        />
      )
    }
    if (visible.length === 0) {
      return (
        <EmptyState
          title="No organizations match your filters"
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
          <caption className="sr-only">Organizations</caption>
          <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-4 py-3">Name</th>
              <th scope="col" className="px-4 py-3">Slug</th>
              <th scope="col" className="px-4 py-3">Status</th>
              <th scope="col" className="px-4 py-3">Created</th>
              <th scope="col" className="px-4 py-3">Updated</th>
              <th scope="col" className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {visible.map((org) => (
              <tr key={org.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-medium text-slate-900">
                  <Link to={`/platform/organizations/${org.id}`} className="text-indigo-700 hover:underline">
                    {org.name}
                  </Link>
                </td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">{org.slug}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={org.status} />
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(org.createdAt)}</td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(org.updatedAt)}</td>
                <td className="whitespace-nowrap px-4 py-3 text-right">
                  <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => setDialog({ kind: 'edit', organization: org })}>
                      Edit<span className="sr-only"> {org.name}</span>
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setDialog({ kind: 'status', organization: org })}>
                      Status<span className="sr-only"> of {org.name}</span>
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-red-600 hover:bg-red-50 hover:text-red-700"
                      onClick={() => setDialog({ kind: 'delete', organization: org })}
                    >
                      Delete<span className="sr-only"> {org.name}</span>
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
        title="Organizations"
        description="Manage tenant organizations and their administrators."
        actions={<Button onClick={() => setDialog({ kind: 'create' })}>Create organization</Button>}
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_12rem_12rem_auto] lg:items-end">
        <TextField
          label="Search organizations"
          hideLabel
          type="search"
          placeholder="Search by name or slug"
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
          Showing {visible.length} of {data.length} organization{data.length === 1 ? '' : 's'}
          {filtersActive ? ' (filtered)' : ''}. Deleted organizations are not listed.
        </p>
      ) : null}

      {dialog?.kind === 'create' ? <OrganizationFormModal onClose={closeDialog} onSaved={handleSaved} /> : null}
      {dialog?.kind === 'edit' ? (
        <OrganizationFormModal organization={dialog.organization} onClose={closeDialog} onSaved={handleSaved} />
      ) : null}
      {dialog?.kind === 'status' ? (
        <OrganizationStatusModal organization={dialog.organization} onClose={closeDialog} onSaved={handleStatusSaved} />
      ) : null}
      {dialog?.kind === 'delete' ? (
        <DeleteOrganizationDialog organization={dialog.organization} onClose={closeDialog} onDeleted={handleDeleted} />
      ) : null}
    </>
  )
}
