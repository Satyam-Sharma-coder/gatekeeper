import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getErrorMessage } from '../../api/client.ts'
import { getOrganization } from '../../api/organizations.ts'
import type { Organization } from '../../api/types.ts'
import { DeleteOrganizationDialog } from '../../componenets/organizations/DeleteOrganizationDialog.tsx'
import { OrganizationFormModal } from '../../componenets/organizations/OrganizationFormModal.tsx'
import { OrganizationStatusModal } from '../../componenets/organizations/OrganizationStatusModal.tsx'
import { TenantAdminPanel } from '../../componenets/tenant-admin/TenantAdminPanel.tsx'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Button } from '../../componenets/ui/Button.tsx'
import { Card, DetailList, PageHeader } from '../../componenets/ui/Card.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { StatusBadge } from '../../componenets/ui/StatusBadge.tsx'
import { useToast } from '../../componenets/ui/toastContext.ts'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatDateTime } from '../../lib/format.ts'

type DialogKind = 'edit' | 'status' | 'delete'

function BackLink() {
  return (
    <Link to="/platform/organizations" className="mb-2 inline-block text-sm text-indigo-700 hover:underline">
      &larr; All organizations
    </Link>
  )
}

export default function OrganizationDetailPage() {
  const { id: idParam } = useParams()
  const id = Number(idParam)
  const validId = Number.isInteger(id) && id > 0
  const navigate = useNavigate()
  const toast = useToast()
  const loader = useCallback(
    (signal: AbortSignal): Promise<Organization> =>
      validId ? getOrganization(id, signal) : Promise.reject(new Error('Invalid organization id')),
    [id, validId],
  )
  const { data, error, loading, reload } = useAsyncData(loader)
  const [dialog, setDialog] = useState<DialogKind | null>(null)

  useDocumentTitle(data?.name ?? 'Organization')

  if (!validId) {
    return (
      <>
        <BackLink />
        <Banner tone="error">That organization address is not valid.</Banner>
      </>
    )
  }

  if (loading && !data) {
    return (
      <>
        <BackLink />
        <LoadingState label="Loading organization..." />
      </>
    )
  }

  if (error || !data) {
    return (
      <>
        <BackLink />
        <Banner
          tone="error"
          title="Could not load the organization"
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

  function handleUpdated(organization: Organization, message: string) {
    toast.success(message.replace('{name}', organization.name))
    closeDialog()
    reload()
  }

  return (
    <>
      <PageHeader
        title={data.name}
        description={`Slug: ${data.slug}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => setDialog('edit')}>
              Edit
            </Button>
            <Button variant="secondary" onClick={() => setDialog('status')}>
              Change status
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
        <Card title="Organization details">
          <DetailList
            items={[
              { label: 'Name', value: data.name },
              { label: 'Slug', value: <span className="font-mono text-xs">{data.slug}</span> },
              { label: 'Status', value: <StatusBadge status={data.status} /> },
              { label: 'Organization ID', value: data.id },
              { label: 'Created', value: formatDateTime(data.createdAt) },
              { label: 'Last updated', value: formatDateTime(data.updatedAt) },
            ]}
          />
        </Card>

        <TenantAdminPanel organizationId={data.id} organizationActive={data.status === 'ACTIVE'} />
      </div>

      {dialog === 'edit' ? (
        <OrganizationFormModal
          organization={data}
          onClose={closeDialog}
          onSaved={(organization) => handleUpdated(organization, 'Organization "{name}" updated.')}
        />
      ) : null}
      {dialog === 'status' ? (
        <OrganizationStatusModal
          organization={data}
          onClose={closeDialog}
          onSaved={(organization) =>
            handleUpdated(organization, `"{name}" is now ${organization.status.toLowerCase()}.`)
          }
        />
      ) : null}
      {dialog === 'delete' ? (
        <DeleteOrganizationDialog
          organization={data}
          onClose={closeDialog}
          onDeleted={(organization) => {
            toast.success(`Organization "${organization.name}" deleted.`)
            navigate('/platform/organizations', { replace: true })
          }}
        />
      ) : null}
    </>
  )
}
