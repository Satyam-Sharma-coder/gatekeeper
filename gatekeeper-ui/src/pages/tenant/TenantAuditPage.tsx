import { useMemo, useState } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { listAuditRecords } from '../../api/routes.ts'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Button } from '../../componenets/ui/Button.tsx'
import { Card, PageHeader } from '../../componenets/ui/Card.tsx'
import { EmptyState } from '../../componenets/ui/EmptyState.tsx'
import { SelectField } from '../../componenets/ui/SelectField.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { TextField } from '../../componenets/ui/TextField.tsx'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatDateTime } from '../../lib/format.ts'

const ALL = 'ALL'

function humanize(action: string): string {
  const text = action.replace(/_/g, ' ').toLowerCase()
  return text.charAt(0).toUpperCase() + text.slice(1)
}

function ResultBadge({ result }: { result: string }) {
  const success = result.toUpperCase() === 'SUCCESS'
  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${
        success ? 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' : 'bg-red-50 text-red-700 ring-red-600/20'
      }`}
    >
      {humanize(result)}
    </span>
  )
}

export default function TenantAuditPage() {
  useDocumentTitle('Audit log')
  const { data, error, loading, reload } = useAsyncData(listAuditRecords)
  const [query, setQuery] = useState('')
  const [action, setAction] = useState(ALL)

  const actionOptions = useMemo(() => {
    const actions = Array.from(new Set((data ?? []).map((record) => record.action))).sort()
    return [{ value: ALL, label: 'All actions' }, ...actions.map((value) => ({ value, label: humanize(value) }))]
  }, [data])

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase()
    return (data ?? []).filter(
      (record) =>
        (action === ALL || record.action === action) &&
        (needle === '' ||
          record.actor.toLowerCase().includes(needle) ||
          (record.resourceId ?? '').toLowerCase().includes(needle) ||
          (record.details ?? '').toLowerCase().includes(needle)),
    )
  }, [data, query, action])

  const filtersActive = query.trim() !== '' || action !== ALL

  function renderContent() {
    if (loading && !data) return <LoadingState label="Loading audit log..." />
    if (error) {
      return (
        <div className="p-4">
          <Banner
            tone="error"
            title="Could not load the audit log"
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
      return <EmptyState title="No audit records yet" description="Changes to your routes and organization appear here." />
    }
    if (visible.length === 0) {
      return (
        <EmptyState
          title="No records match your filters"
          action={
            <Button
              variant="secondary"
              onClick={() => {
                setQuery('')
                setAction(ALL)
              }}
            >
              Clear filters
            </Button>
          }
        />
      )
    }
    return (
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
          <caption className="sr-only">Audit records, newest first</caption>
          <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-4 py-3">When</th>
              <th scope="col" className="px-4 py-3">Actor</th>
              <th scope="col" className="px-4 py-3">Action</th>
              <th scope="col" className="px-4 py-3">Resource</th>
              <th scope="col" className="px-4 py-3">Result</th>
              <th scope="col" className="px-4 py-3">Duration</th>
              <th scope="col" className="px-4 py-3">Details</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {visible.map((record) => (
              <tr key={record.id} className="align-top hover:bg-slate-50">
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(record.createdAt)}</td>
                <td className="px-4 py-3 text-slate-900">{record.actor}</td>
                <td className="px-4 py-3 text-slate-700">{humanize(record.action)}</td>
                <td className="px-4 py-3">
                  <p className="text-xs text-slate-500">{humanize(record.resourceType)}</p>
                  <p className="break-all font-mono text-xs text-slate-900">{record.resourceId ?? '-'}</p>
                </td>
                <td className="px-4 py-3">
                  <ResultBadge result={record.result} />
                </td>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums text-slate-600">
                  {record.durationMs === null ? '-' : `${record.durationMs} ms`}
                </td>
                <td className="min-w-48 px-4 py-3 text-slate-600">
                  {record.details ?? '-'}
                  {record.correlationId ? (
                    <p className="mt-1 break-all font-mono text-xs text-slate-400">
                      Correlation ID: {record.correlationId}
                    </p>
                  ) : null}
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
      <PageHeader title="Audit log" description="A record of changes made to your organization, newest first." />

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_16rem_auto] lg:items-end">
        <TextField
          label="Search audit records"
          hideLabel
          type="search"
          placeholder="Search by actor, resource or details"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <SelectField label="Filter by action" hideLabel value={action} onChange={(event) => setAction(event.target.value)} options={actionOptions} />
        <Button variant="secondary" onClick={() => reload()} loading={loading && data !== null}>
          Refresh
        </Button>
      </div>

      <Card>
        <div className="-mx-5 -my-4">{renderContent()}</div>
      </Card>

      {data && data.length > 0 ? (
        <p aria-live="polite" className="mt-3 text-xs text-slate-500">
          Showing {visible.length} of {data.length} record{data.length === 1 ? '' : 's'}
          {filtersActive ? ' (filtered)' : ''}.
        </p>
      ) : null}
    </>
  )
}
