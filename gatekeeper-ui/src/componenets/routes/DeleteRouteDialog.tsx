import { deleteRoute } from '../../api/routes.ts'
import type { ApiRoute } from '../../api/types.ts'
import { ConfirmDialog } from '../ui/ConfirmDialog.tsx'

interface DeleteRouteDialogProps {
  route: ApiRoute
  onClose: () => void
  onDeleted: (route: ApiRoute) => void
}

export function DeleteRouteDialog({ route, onClose, onDeleted }: DeleteRouteDialogProps) {
  return (
    <ConfirmDialog
      title="Delete route"
      confirmLabel="Delete route"
      onClose={onClose}
      onConfirm={async () => {
        await deleteRoute(route.id)
        onDeleted(route)
      }}
    >
      <p>
        Delete <strong>{route.name}</strong> ({route.routeId})? The gateway will stop serving it.
      </p>
      <p>The final configuration stays available in the version history and audit log.</p>
    </ConfirmDialog>
  )
}
