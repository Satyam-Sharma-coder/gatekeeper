import { changeRouteStatus } from '../../api/routes.ts'
import type { ApiRoute } from '../../api/types.ts'
import { ConfirmDialog } from '../ui/ConfirmDialog.tsx'

interface RouteStatusDialogProps {
  route: ApiRoute
  onClose: () => void
  onChanged: (route: ApiRoute) => void
}

export function RouteStatusDialog({ route, onClose, onChanged }: RouteStatusDialogProps) {
  const activating = !route.active

  return (
    <ConfirmDialog
      title={activating ? 'Activate route' : 'Deactivate route'}
      confirmLabel={activating ? 'Activate route' : 'Deactivate route'}
      tone={activating ? 'primary' : 'danger'}
      onClose={onClose}
      onConfirm={async () => onChanged(await changeRouteStatus(route.id, activating))}
    >
      <p>
        {activating
          ? `"${route.name}" will start being served by the gateway.`
          : `"${route.name}" will stop being served by the gateway. Requests matching its path will no longer be routed.`}
      </p>
      <p>This creates a new configuration version.</p>
    </ConfirmDialog>
  )
}
