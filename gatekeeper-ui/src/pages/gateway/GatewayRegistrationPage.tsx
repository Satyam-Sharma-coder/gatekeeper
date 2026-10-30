import { PageHeader } from '../../componenets/ui/Card.tsx'
import { ComingSoonCard } from '../../componenets/ui/ComingSoonCard.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'

export default function GatewayRegistrationPage() {
  useDocumentTitle('Gateway registration')

  return (
    <>
      <PageHeader title="Registration" description="Register a gateway instance with the control plane." />
      <ComingSoonCard
        title="Gateway registration"
        description="The backend does not expose a gateway registration endpoint yet, so there is nothing to submit here. Gateway instances currently only sign in with the seeded gateway account."
      />
    </>
  )
}
