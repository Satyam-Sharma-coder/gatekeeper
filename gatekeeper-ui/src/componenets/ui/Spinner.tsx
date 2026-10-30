interface SpinnerProps {
  className?: string
}

export function Spinner({ className = 'h-4 w-4' }: SpinnerProps) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" className="opacity-25" />
      <path d="M4 12a8 8 0 0 1 8-8" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

interface LoadingStateProps {
  label?: string
}

export function LoadingState({ label = 'Loading...' }: LoadingStateProps) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 px-4 py-12 text-sm text-slate-500">
      <Spinner className="h-5 w-5 text-indigo-600" />
      <span>{label}</span>
    </div>
  )
}
