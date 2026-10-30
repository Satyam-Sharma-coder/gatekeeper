import { useState } from 'react'
import type { FormEvent } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { getOwnOrganization, updateOwnOrganization } from '../../api/tenantOrganization.ts'
import type { Organization } from '../../api/types.ts'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { Button } from '../../componenets/ui/Button.tsx'
import { Card, DetailList, PageHeader } from '../../componenets/ui/Card.tsx'
import { Modal } from '../../componenets/ui/Modal.tsx'
import { LoadingState } from '../../componenets/ui/Spinner.tsx'
import { StatusBadge } from '../../componenets/ui/StatusBadge.tsx'
import { TextField } from '../../componenets/ui/TextField.tsx'
import { useToast } from '../../componenets/ui/toastContext.ts'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { focusFirstInvalid } from '../../lib/dom.ts'
import { formatDateTime } from '../../lib/format.ts'
import { hasErrors, validateOwnOrganizationName } from '../../lib/validation.ts'
import type { FieldErrors } from '../../lib/validation.ts'

interface RenameModalProps {
  organization: Organization
  onClose: () => void
  onSaved: (organization: Organization) => void
}

function RenameModal({ organization, onClose, onSaved }: RenameModalProps) {
  const [name, setName] = useState(organization.name)
  const [errors, setErrors] = useState<FieldErrors<'name'>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const found = validateOwnOrganizationName(name)
    setErrors(found)
    if (hasErrors(found)) {
      focusFirstInvalid(form)
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      onSaved(await updateOwnOrganization({ name: name.trim() }))
    } catch (error) {
      setSubmitError(getErrorMessage(error))
      setSubmitting(false)
    }
  }

  const formId = 'own-organization-form'

  return (
    <Modal
      title="Rename organization"
      description="The slug and status can only be changed by a platform administrator."
      onClose={onClose}
      dismissible={!submitting}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={submitting}>
            Save changes
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} noValidate className="space-y-4">
        {submitError ? <Banner tone="error">{submitError}</Banner> : null}
        <TextField
          label="Name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={errors.name}
          maxLength={150}
          autoComplete="off"
          required
        />
      </form>
    </Modal>
  )
}

export default function TenantOrganizationPage() {
  useDocumentTitle('Organization')
  const toast = useToast()
  const { data, error, loading, reload } = useAsyncData(getOwnOrganization)
  const [renaming, setRenaming] = useState(false)

  return (
    <>
      <PageHeader
        title="Organization"
        description="Details of your organization."
        actions={
          data ? (
            <Button variant="secondary" onClick={() => setRenaming(true)}>
              Rename
            </Button>
          ) : null
        }
      />

      {loading && !data ? <LoadingState label="Loading organization..." /> : null}

      {error && !data ? (
        <Banner
          tone="error"
          title="Could not load your organization"
          actions={
            <Button variant="secondary" size="sm" onClick={() => reload()}>
              Retry
            </Button>
          }
        >
          {getErrorMessage(error)}
        </Banner>
      ) : null}

      {data ? (
        <div className="space-y-6">
          {data.status !== 'ACTIVE' ? (
            <Banner tone="warning">
              Your organization is {data.status.toLowerCase()}. The gateway only serves routes of active organizations.
            </Banner>
          ) : null}
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
        </div>
      ) : null}

      {renaming && data ? (
        <RenameModal
          organization={data}
          onClose={() => setRenaming(false)}
          onSaved={(organization) => {
            toast.success(`Organization renamed to "${organization.name}".`)
            setRenaming(false)
            reload()
          }}
        />
      ) : null}
    </>
  )
}
