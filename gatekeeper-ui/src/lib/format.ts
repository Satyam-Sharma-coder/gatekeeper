const LOCAL_DATE_TIME = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(\.\d+)?$/

export function parseTimestamp(value: string): number | null {
  const match = LOCAL_DATE_TIME.exec(value)
  const normalized = match ? `${match[1]}${(match[2] ?? '').slice(0, 4)}` : value
  const time = Date.parse(normalized)
  return Number.isNaN(time) ? null : time
}

const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function formatDateTime(value: string | number): string {
  const time = typeof value === 'number' ? value : parseTimestamp(value)
  return time === null ? 'Unknown' : dateTimeFormat.format(time)
}

export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(rest)}` : `${pad(minutes)}:${pad(rest)}`
}
