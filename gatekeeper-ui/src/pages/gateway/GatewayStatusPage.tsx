import { useAuth } from '../../auth/authContext.ts'
import { SessionCard } from '../../componenets/session/SessionCard.tsx'
import { Banner } from '../../componenets/ui/Banner.tsx'
import { PageHeader } from '../../componenets/ui/Card.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'

export default function GatewayStatusPage() {
  useDocumentTitle('Gateway status')
  const { session } = useAuth()
  if (!session) return null

  return (
    <>
      <PageHeader title="Gateway service" description={`Signed in as ${session.username}.`} />
      <div className="space-y-6">
        <Banner tone="info">
          Gateway services normally authenticate programmatically. This page shows the session of the account you
          signed in with. See Active routes for the configuration the gateway pulls from the control plane.
        </Banner>
        <SessionCard session={session} />
      </div>
    </>
  )
}
