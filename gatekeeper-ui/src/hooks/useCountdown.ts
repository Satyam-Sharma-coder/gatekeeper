import { useEffect, useState } from 'react'

export function useCountdown(targetMs: number): number {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  return Math.max(0, Math.ceil((targetMs - now) / 1000))
}
