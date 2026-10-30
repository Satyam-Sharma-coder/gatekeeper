import { deleteOrganization } from '../../api/organizations.ts'
import type { Organization } from '../../api/types.ts'
import { ConfirmDialog } from '../ui/ConfirmDialog.tsx'

interface DeleteOrganizationDialogProps {
  organization: Organization
  onClose: () => void
  onDeleted: (organization: Organization) => void
}

export function DeleteOrganizationDialog({ organization, onClose, onDeleted }: DeleteOrganizationDialogProps) {
  return (
    <ConfirmDialog
      title="Delete organization"
      confirmLabel="Delete organization"
      onClose={onClose}
      onConfirm={async () => {
        await deleteOrganization(organization.id)
        onDeleted(organization)
      }}
    >
      <p>
        Delete <strong className="text-slate-900">{organization.name}</strong>?
      </p>
      <p>
        This is a soft delete: the organization disappears from the list and its tenant admin can no longer sign in,
        but the record is kept on the server. It cannot be restored from this interface.
      </p>
    </ConfirmDialog>
  )
}
