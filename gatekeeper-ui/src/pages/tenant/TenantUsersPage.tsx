import { PageHeader } from '../../componenets/ui/Card.tsx'
import { ComingSoonCard } from '../../componenets/ui/ComingSoonCard.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'

export default function TenantUsersPage() {
  useDocumentTitle('Users')

  return (
    <>
      <PageHeader title="Users" description="People who can access your organization." />
      <ComingSoonCard
        title="User management"
        description="The backend does not expose a user management endpoint for tenants yet. Once it does, you will manage the users of your own organization here."
        planned={['User list', 'Create and disable users']}
      />
    </>
  )
}
