import { useState } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { updateOrganizationStatus } from '../../api/organizations.ts'
import { SETTABLE_STATUSES } from '../../api/types.ts'
import type { Organization, SettableOrganizationStatus } from '../../api/types.ts'
import { Banner } from '../ui/Banner.tsx'
import { Button } from '../ui/Button.tsx'
import { Modal } from '../ui/Modal.tsx'
import { SelectField } from '../ui/SelectField.tsx'

const OPTIONS = SETTABLE_STATUSES.map((status) => ({
  value: status,
  label: status.charAt(0) + status.slice(1).toLowerCase(),
}))

function isSettable(value: string): value is SettableOrganizationStatus {
  return (SETTABLE_STATUSES as readonly string[]).includes(value)
}

interface OrganizationStatusModalProps {
  organization: Organization
  onClose: () => void
  onSaved: (organization: Organization) => void
}

export function OrganizationStatusModal({ organization, onClose, onSaved }: OrganizationStatusModalProps) {
  const initial: SettableOrganizationStatus = isSettable(organization.status) ? organization.status : 'PENDING'
  const [status, setStatus] = useState<SettableOrganizationStatus>(initial)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setSubmitting(true)
    setError(null)
    try {
      onSaved(await updateOrganizationStatus(organization.id, status))
    } catch (caught) {
      setError(getErrorMessage(caught))
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="Change status"
      description={`Organization: ${organization.name}`}
      size="sm"
      onClose={onClose}
      dismissible={!submitting}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button onClick={handleConfirm} loading={submitting} disabled={status === initial}>
            Confirm change
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {error ? <Banner tone="error">{error}</Banner> : null}
        <SelectField
          label="Status"
          value={status}
          onChange={(event) => {
            if (isSettable(event.target.value)) setStatus(event.target.value)
          }}
          options={OPTIONS}
          hint="Deleting is a separate action. The tenant admin can only sign in while the organization is Active."
        />
      </div>
    </Modal>
  )
}
