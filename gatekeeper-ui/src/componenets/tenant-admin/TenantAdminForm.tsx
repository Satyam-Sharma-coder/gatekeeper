import { useState } from 'react'
import type { FormEvent } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import type { TenantAdmin, TenantAdminRequest } from '../../api/types.ts'
import { focusFirstInvalid } from '../../lib/dom.ts'
import { hasErrors, validateTenantAdmin } from '../../lib/validation.ts'
import type { FieldErrors } from '../../lib/validation.ts'
import { Banner } from '../ui/Banner.tsx'
import { Button } from '../ui/Button.tsx'
import { CheckboxField } from '../ui/CheckboxField.tsx'
import { PasswordField } from '../ui/PasswordField.tsx'
import { TextField } from '../ui/TextField.tsx'

interface TenantAdminFormProps {
  existing?: TenantAdmin
  onSubmit: (values: TenantAdminRequest) => Promise<void>
  onCancel?: () => void
}

export function TenantAdminForm({ existing, onSubmit, onCancel }: TenantAdminFormProps) {
  const isEdit = existing !== undefined
  const [username, setUsername] = useState(existing?.username ?? '')
  const [password, setPassword] = useState('')
  const [enabled, setEnabled] = useState(existing?.enabled ?? true)
  const [errors, setErrors] = useState<FieldErrors<'username' | 'password'>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const found = validateTenantAdmin(username, password)
    setErrors(found)
    if (hasErrors(found)) {
      focusFirstInvalid(form)
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      await onSubmit({ username: username.trim(), password, enabled })
    } catch (error) {
      setSubmitError(getErrorMessage(error))
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      {submitError ? <Banner tone="error">{submitError}</Banner> : null}
      <TextField
        label="Username"
        value={username}
        onChange={(event) => setUsername(event.target.value)}
        error={errors.username}
        maxLength={100}
        autoComplete="off"
        spellCheck={false}
        required
      />
      <PasswordField
        label={isEdit ? 'New password' : 'Password'}
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        error={errors.password}
        hint={
          isEdit
            ? 'The server requires a password on every update, so enter a new one to save changes.'
            : '8 to 100 characters.'
        }
        maxLength={100}
        autoComplete="new-password"
        required
      />
      <CheckboxField
        label="Enabled"
        description="Disabled administrators cannot sign in."
        checked={enabled}
        onChange={(event) => setEnabled(event.target.checked)}
      />
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        {onCancel ? (
          <Button variant="secondary" onClick={onCancel} disabled={submitting}>
            Cancel
          </Button>
        ) : null}
        <Button type="submit" loading={submitting}>
          {isEdit ? 'Save tenant admin' : 'Create tenant admin'}
        </Button>
      </div>
    </form>
  )
}
