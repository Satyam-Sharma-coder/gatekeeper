import { Link } from 'react-router-dom'
import { useAuth } from '../auth/authContext.ts'
import { roleHome } from '../auth/roles.ts'
import { buttonClasses } from '../componenets/ui/buttonStyles.ts'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'

export default function NotFoundPage() {
  useDocumentTitle('Page not found')
  const { session } = useAuth()

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-3 px-4 text-center">
      <p className="text-sm font-semibold text-indigo-600">404</p>
      <h1 className="text-2xl font-semibold text-slate-900">Page not found</h1>
      <p className="text-sm text-slate-500">The page you are looking for does not exist or has moved.</p>
      <Link to={session ? roleHome(session.role) : '/login'} className={`${buttonClasses('primary')} mt-2`}>
        {session ? 'Go to your home page' : 'Go to sign in'}
      </Link>
    </main>
  )
}
