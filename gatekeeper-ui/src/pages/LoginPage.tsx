import { useState } from 'react'
import type { FormEvent } from 'react'
import { getErrorMessage } from '../api/client.ts'
import { useAuth } from '../auth/authContext.ts'
import { Banner } from '../componenets/ui/Banner.tsx'
import { BrandMark } from '../componenets/ui/BrandMark.tsx'
import { Button } from '../componenets/ui/Button.tsx'
import { PasswordField } from '../componenets/ui/PasswordField.tsx'
import { TextField } from '../componenets/ui/TextField.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { focusFirstInvalid } from '../lib/dom.ts'
import { hasErrors, validateLogin } from '../lib/validation.ts'
import type { FieldErrors } from '../lib/validation.ts'

export default function LoginPage() {
  useDocumentTitle('Sign in')
  const { login, notice } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors<'username' | 'password'>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const found = validateLogin(username, password)
    setErrors(found)
    if (hasErrors(found)) {
      focusFirstInvalid(form)
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      await login(username.trim(), password)
    } catch (error) {
      setSubmitError(getErrorMessage(error))
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-2 text-center">
          <BrandMark className="h-10 w-10" />
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Sign in to Gatekeeper</h1>
          <p className="text-sm text-slate-500">Use your platform, tenant or gateway account.</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {notice && !submitError ? <Banner tone="warning">{notice}</Banner> : null}
            {submitError ? <Banner tone="error">{submitError}</Banner> : null}
            <TextField
              label="Username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              error={errors.username}
              autoComplete="username"
              autoFocus
              required
            />
            <PasswordField
              label="Password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={errors.password}
              autoComplete="current-password"
              required
            />
            <Button type="submit" loading={submitting} className="w-full">
              {submitting ? 'Signing in...' : 'Sign in'}
            </Button>
          </form>
        </div>
      </div>
    </main>
  )
}
