import { Suspense, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/authContext.ts'
import { roleLabel } from '../../auth/roles.ts'
import { BrandMark } from '../ui/BrandMark.tsx'
import { Button } from '../ui/Button.tsx'
import { LoadingState } from '../ui/Spinner.tsx'

export interface NavItem {
  to: string
  label: string
  end?: boolean
}

interface AppShellProps {
  areaLabel: string
  navItems: readonly NavItem[]
}

interface NavListProps {
  navItems: readonly NavItem[]
  label: string
  onNavigate?: () => void
}

function NavList({ navItems, label, onNavigate }: NavListProps) {
  return (
    <nav aria-label={label} className="flex flex-col gap-1 px-3">
      {navItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          onClick={onNavigate}
          className={({ isActive }) =>
            `rounded-md px-3 py-2 text-sm font-medium focus-visible:outline-white ${
              isActive ? 'bg-slate-800 text-white' : 'text-slate-300 hover:bg-slate-800/60 hover:text-white'
            }`
          }
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}

export function AppShell({ areaLabel, navItems }: AppShellProps) {
  const { session, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="min-h-screen bg-slate-50 md:flex">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2 focus:text-sm focus:shadow"
      >
        Skip to content
      </a>

      <aside className="hidden w-64 shrink-0 flex-col bg-slate-900 py-4 md:flex">
        <div className="mb-6 flex items-center gap-2 px-6 text-white">
          <BrandMark />
          <span className="text-lg font-semibold">Gatekeeper</span>
        </div>
        <NavList navItems={navItems} label={areaLabel} />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-slate-200 bg-white px-4 sm:px-6">
          <button
            type="button"
            className="rounded-md p-2 text-slate-600 hover:bg-slate-100 md:hidden"
            aria-expanded={menuOpen}
            aria-controls="mobile-nav"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span className="sr-only">{menuOpen ? 'Close menu' : 'Open menu'}</span>
            <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d="M3 5h14a1 1 0 0 1 0 2H3a1 1 0 0 1 0-2zm0 4h14a1 1 0 0 1 0 2H3a1 1 0 0 1 0-2zm0 4h14a1 1 0 0 1 0 2H3a1 1 0 0 1 0-2z" />
            </svg>
          </button>
          <span className="text-sm font-semibold text-slate-700">{areaLabel}</span>
          <div className="ml-auto flex items-center gap-3">
            {session ? (
              <div className="hidden text-right leading-tight sm:block">
                <p className="text-sm font-medium text-slate-900">{session.username}</p>
                <p className="text-xs text-slate-500">{roleLabel(session.role)}</p>
              </div>
            ) : null}
            <Button variant="secondary" size="sm" onClick={logout}>
              Sign out
            </Button>
          </div>
        </header>

        {menuOpen ? (
          <div id="mobile-nav" className="bg-slate-900 py-3 md:hidden">
            <NavList navItems={navItems} label={`${areaLabel} (mobile)`} onNavigate={() => setMenuOpen(false)} />
          </div>
        ) : null}

        <main id="main-content" tabIndex={-1} className="flex-1 p-4 sm:p-6 lg:p-8">
          <div className="mx-auto max-w-6xl">
            <Suspense fallback={<LoadingState />}>
              <Outlet />
            </Suspense>
          </div>
        </main>
      </div>
    </div>
  )
}
