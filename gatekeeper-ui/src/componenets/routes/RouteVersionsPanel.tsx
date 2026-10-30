import { useCallback, useState } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { listRouteVersions } from '../../api/routes.ts'
import { useAsyncData } from '../../hooks/useAsyncData.ts'
import { formatDateTime } from '../../lib/format.ts'
import { Banner } from '../ui/Banner.tsx'
import { Button } from '../ui/Button.tsx'
import { Card } from '../ui/Card.tsx'
import { EmptyState } from '../ui/EmptyState.tsx'
import { LoadingState } from '../ui/Spinner.tsx'

interface RouteVersionsPanelProps {
  routeId: string
  currentVersion: number
}

function prettySnapshot(snapshot: string): string {
  try {
    return JSON.stringify(JSON.parse(snapshot), null, 2)
  } catch {
    return snapshot
  }
}

function changeLabel(changeType: string): string {
  return changeType.charAt(0) + changeType.slice(1).toLowerCase()
}

export function RouteVersionsPanel({ routeId, currentVersion }: RouteVersionsPanelProps) {
  // currentVersion changes after every edit, which gives the loader a new identity and reloads the history
  const loader = useCallback(
    (signal: AbortSignal) => listRouteVersions(routeId, signal).then((versions) => ({ versions, currentVersion })),
    [routeId, currentVersion],
  )
  const { data, error, loading, reload } = useAsyncData(loader)
  const [expanded, setExpanded] = useState<number | null>(null)

  function renderContent() {
    if (loading && !data) return <LoadingState label="Loading version history..." />
    if (error) {
      return (
        <Banner
          tone="error"
          title="Could not load version history"
          actions={
            <Button variant="secondary" size="sm" onClick={() => reload()}>
              Retry
            </Button>
          }
        >
          {getErrorMessage(error)}
        </Banner>
      )
    }
    if (!data || data.versions.length === 0) return <EmptyState title="No versions recorded" />
    return (
      <ul className="divide-y divide-slate-100">
        {data.versions.map((version) => {
          const open = expanded === version.id
          const panelId = `version-snapshot-${version.id}`
          return (
            <li key={version.id} className="py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                  <span className="font-semibold text-slate-900">Version {version.versionNumber}</span>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                    {changeLabel(version.changeType)}
                  </span>
                  <span className="text-slate-500">{formatDateTime(version.createdAt)}</span>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  aria-expanded={open}
                  aria-controls={panelId}
                  onClick={() => setExpanded(open ? null : version.id)}
                >
                  {open ? 'Hide snapshot' : 'View snapshot'}
                  <span className="sr-only"> of version {version.versionNumber}</span>
                </Button>
              </div>
              {open ? (
                <pre
                  id={panelId}
                  tabIndex={0}
                  className="mt-3 max-h-80 overflow-auto rounded-md bg-slate-900 p-3 text-xs text-slate-100"
                >
                  {prettySnapshot(version.configurationSnapshot)}
                </pre>
              ) : null}
            </li>
          )
        })}
      </ul>
    )
  }

  return (
    <Card
      title="Version history"
      description="Every change to this route is recorded as a new configuration version."
      actions={
        <Button variant="secondary" size="sm" onClick={() => reload()} loading={loading && data !== null}>
          Refresh
        </Button>
      }
    >
      {renderContent()}
    </Card>
  )
}
