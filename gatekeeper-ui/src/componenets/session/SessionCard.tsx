import { roleLabel } from '../../auth/roles.ts'
import type { Session } from '../../auth/session.ts'
import { useCountdown } from '../../hooks/useCountdown.ts'
import { formatDateTime, formatDuration } from '../../lib/format.ts'
import { Card, DetailList } from '../ui/Card.tsx'

interface SessionCardProps {
  session: Session
  organizationNote?: string
}

export function SessionCard({ session, organizationNote }: SessionCardProps) {
  const remaining = useCountdown(session.expiresAt)

  return (
    <Card title="Your session" description="Taken from your sign-in response. The token itself is never displayed.">
      <DetailList
        items={[
          { label: 'Username', value: session.username },
          { label: 'Role', value: roleLabel(session.role) },
          {
            label: 'Organization ID',
            value:
              session.organizationId === null ? (
                'None (account is not tied to an organization)'
              ) : (
                <>
                  {session.organizationId}
                  {organizationNote ? <span className="block text-xs text-slate-500">{organizationNote}</span> : null}
                </>
              ),
          },
          { label: 'Signed in', value: formatDateTime(session.issuedAt) },
          { label: 'Session expires', value: formatDateTime(session.expiresAt) },
          {
            label: 'Time remaining',
            value: (
              <span role="timer" className="font-mono tabular-nums">
                {formatDuration(remaining)}
              </span>
            ),
          },
        ]}
      />
    </Card>
  )
}
