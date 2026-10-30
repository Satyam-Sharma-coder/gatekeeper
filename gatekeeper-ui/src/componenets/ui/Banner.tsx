import type { ReactNode } from 'react'

export type BannerTone = 'error' | 'success' | 'info' | 'warning'

const TONES: Readonly<Record<BannerTone, string>> = {
  error: 'border-red-200 bg-red-50 text-red-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  info: 'border-sky-200 bg-sky-50 text-sky-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
}

interface BannerProps {
  tone?: BannerTone
  title?: string
  actions?: ReactNode
  children: ReactNode
}

export function Banner({ tone = 'info', title, actions, children }: BannerProps) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`flex flex-col gap-3 rounded-lg border px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between ${TONES[tone]}`}
    >
      <div>
        {title ? <p className="font-semibold">{title}</p> : null}
        <div>{children}</div>
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
    </div>
  )
}
