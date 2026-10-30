import { useState } from 'react'
import type { InputHTMLAttributes } from 'react'
import { TextField } from './TextField.tsx'

interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  label: string
  error?: string
  hint?: string
}

export function PasswordField(props: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)

  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((value) => !value)}
          aria-pressed={visible}
          className="rounded px-2 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-50"
        >
          {visible ? 'Hide' : 'Show'}
          <span className="sr-only"> password</span>
        </button>
      }
    />
  )
}
