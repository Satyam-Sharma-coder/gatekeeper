import { useId } from 'react'
import type { InputHTMLAttributes } from 'react'

interface CheckboxFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  label: string
  description?: string
}

export function CheckboxField({ label, description, ...rest }: CheckboxFieldProps) {
  const id = useId()
  const descriptionId = `${id}-description`

  return (
    <div className="flex items-start gap-3">
      <input
        id={id}
        type="checkbox"
        aria-describedby={description ? descriptionId : undefined}
        className="mt-0.5 h-4 w-4 rounded border-slate-300 text-indigo-600"
        {...rest}
      />
      <div>
        <label htmlFor={id} className="text-sm font-medium text-slate-700">
          {label}
        </label>
        {description ? (
          <p id={descriptionId} className="text-xs text-slate-500">
            {description}
          </p>
        ) : null}
      </div>
    </div>
  )
}
