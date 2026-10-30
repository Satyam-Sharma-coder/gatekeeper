interface BrandMarkProps {
  className?: string
}

export function BrandMark({ className = 'h-7 w-7' }: BrandMarkProps) {
  return (
    <svg className={className} viewBox="0 0 32 32" aria-hidden="true">
      <path fill="#6366f1" d="M16 2 4 6.5v8.2c0 7.2 5 12.6 12 15.3 7-2.7 12-8.1 12-15.3V6.5L16 2z" />
      <path
        fill="none"
        stroke="#fff"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2.5"
        d="m10.5 16 4 4 7-8"
      />
    </svg>
  )
}
