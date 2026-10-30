import type { OrganizationStatus } from '../../api/types.ts'

const STYLES: Readonly<Record<OrganizationStatus, { badge: string; dot: string; label: string }>> = {
  ACTIVE: { badge: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', dot: 'bg-emerald-500', label: 'Active' },
  PENDING: { badge: 'bg-amber-50 text-amber-800 ring-amber-600/20', dot: 'bg-amber-500', label: 'Pending' },
  SUSPENDED: { badge: 'bg-red-50 text-red-700 ring-red-600/20', dot: 'bg-red-500', label: 'Suspended' },
  DELETED: { badge: 'bg-slate-100 text-slate-600 ring-slate-500/20', dot: 'bg-slate-400', label: 'Deleted' },
}

interface StatusBadgeProps {
  status: OrganizationStatus
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const style = STYLES[status] ?? STYLES.DELETED
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${style.badge}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} aria-hidden="true" />
      {style.label}
    </span>
  )
}

interface FlagBadgeProps {
  on: boolean
  onLabel: string
  offLabel: string
}

export function FlagBadge({ on, onLabel, offLabel }: FlagBadgeProps) {
  const style = on ? STYLES.ACTIVE : STYLES.DELETED
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${style.badge}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} aria-hidden="true" />
      {on ? onLabel : offLabel}
    </span>
  )
}
