import { useCallback, useState } from 'react'
import { getErrorMessage, getErrorStatus } from '../../api/client.ts'
import { createTenantAdmin, deleteTenantAdmin, getTenantAdmin, updateTenantAdmin } from '../../api/tenantAdmin.ts'
import type { TenantAdminRequest } from '../../api/types.ts'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { Banner } from '../ui/Banner.tsx'
import { Button } from '../ui/Button.tsx'
import { Card, DetailList } from '../ui/Card.tsx'
import { ConfirmDialog } from '../ui/ConfirmDialog.tsx'
import { FlagBadge } from '../ui/StatusBadge.tsx'
import { LoadingState } from '../ui/Spinner.tsx'
import { useToast } from '../ui/toastContext.ts'
import { TenantAdminForm } from './TenantAdminForm.tsx'

interface TenantAdminPanelProps {
  organizationId: number
  organizationActive: boolean
}

export function TenantAdminPanel({ organizationId, organizationActive }: TenantAdminPanelProps) {
  const toast = useToast()
  const loader = useCallback((signal: AbortSignal) => getTenantAdmin(organizationId, signal), [organizationId])
  const { data, error, loading, reload } = useAsyncData(loader)
  const [editing, setEditing] = useState(false)
  const [createAnyway, setCreateAnyway] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const status = getErrorStatus(error)
  const notFound = error !== null && status === 404
  const serverUncertain = error !== null && (status === null || status === 0 || status >= 500)

  async function handleCreate(values: TenantAdminRequest) {
    await createTenantAdmin(organizationId, values)
    toast.success('Tenant admin created.')
    setCreateAnyway(false)
    reload({ clear: true })
  }

  async function handleUpdate(values: TenantAdminRequest) {
    await updateTenantAdmin(organizationId, values)
    toast.success('Tenant admin updated.')
    setEditing(false)
    reload({ clear: true })
  }

  async function handleDelete() {
    await deleteTenantAdmin(organizationId)
    toast.success('Tenant admin deleted.')
    setDeleting(false)
    reload({ clear: true })
  }

  function renderBody() {
    if (loading && !data) return <LoadingState label="Loading tenant admin..." />

    if (data) {
      if (editing) {
        return (
          <TenantAdminForm
            key={`${data.id}-${data.username}`}
            existing={data}
            onSubmit={handleUpdate}
            onCancel={() => setEditing(false)}
          />
        )
      }
      return (
        <div className="space-y-4">
          <DetailList
            items={[
              { label: 'Username', value: data.username },
              {
                label: 'Account',
                value: <FlagBadge on={data.enabled} onLabel="Enabled" offLabel="Disabled" />,
              },
              { label: 'Admin ID', value: data.id },
            ]}
          />
          {!organizationActive ? (
            <Banner tone="warning">
              This administrator can only sign in while the organization is Active.
            </Banner>
          ) : null}
        </div>
      )
    }

    if (notFound || createAnyway) {
      return (
        <div className="space-y-4">
          <div>
            <p className="text-sm font-semibold text-slate-900">No tenant admin yet</p>
            <p className="text-sm text-slate-500">
              Create the administrator who will manage this organization. There can be one per organization.
            </p>
          </div>
          <TenantAdminForm onSubmit={handleCreate} />
        </div>
      )
    }

    return (
      <Banner
        tone="error"
        title="Could not load the tenant admin"
        actions={
          <>
            <Button variant="secondary" size="sm" onClick={() => reload()}>
              Retry
            </Button>
            {serverUncertain ? (
              <Button variant="ghost" size="sm" onClick={() => setCreateAnyway(true)}>
                Create one anyway
              </Button>
            ) : null}
          </>
        }
      >
        {getErrorMessage(error)}
        {serverUncertain ? ' If this organization has no tenant admin, the server may be reporting that as an error.' : ''}
      </Banner>
    )
  }

  return (
    <>
      <Card
        title="Tenant admin"
        description="The administrator account that signs in to the tenant interface."
        actions={
          data && !editing ? (
            <>
              <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
                Edit
              </Button>
              <Button variant="danger" size="sm" onClick={() => setDeleting(true)}>
                Delete
              </Button>
            </>
          ) : undefined
        }
      >
        {renderBody()}
      </Card>
      {deleting && data ? (
        <ConfirmDialog
          title="Delete tenant admin"
          confirmLabel="Delete tenant admin"
          onClose={() => setDeleting(false)}
          onConfirm={handleDelete}
        >
          <p>
            Delete <strong className="text-slate-900">{data.username}</strong>? The account is removed permanently and
            can no longer sign in. You can create a new tenant admin afterwards.
          </p>
        </ConfirmDialog>
      ) : null}
    </>
  )
}
