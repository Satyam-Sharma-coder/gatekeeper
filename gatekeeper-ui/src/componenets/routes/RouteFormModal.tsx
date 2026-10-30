import { useState } from 'react'
import type { FormEvent } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { createRoute, updateRoute } from '../../api/routes.ts'
import { HEADER_ACTIONS, HEADER_DIRECTIONS, HTTP_METHODS } from '../../api/types.ts'
import type { ApiRoute, HeaderAction, HeaderDirection, RouteRequest } from '../../api/types.ts'
import { focusFirstInvalid } from '../../lib/dom.ts'
import { hasErrors, validateRoute } from '../../lib/validation.ts'
import type { RouteFormErrors } from '../../lib/validation.ts'
import { Banner } from '../ui/Banner.tsx'
import { Button } from '../ui/Button.tsx'
import { CheckboxField } from '../ui/CheckboxField.tsx'
import { Modal } from '../ui/Modal.tsx'
import { SelectField } from '../ui/SelectField.tsx'
import { TextField } from '../ui/TextField.tsx'

interface RuleDraft {
  key: number
  direction: HeaderDirection
  action: HeaderAction
  headerName: string
  headerValue: string
}

interface RouteFormModalProps {
  route?: ApiRoute
  organizationSlug: string | null
  onClose: () => void
  onSaved: (route: ApiRoute, mode: 'created' | 'updated') => void
}

const DIRECTION_OPTIONS = HEADER_DIRECTIONS.map((value) => ({
  value,
  label: value === 'REQUEST' ? 'Request' : 'Response',
}))

const ACTION_OPTIONS = HEADER_ACTIONS.map((value) => ({
  value,
  label: value === 'ADD' ? 'Add / set' : 'Remove',
}))

let ruleKeyCounter = 0
const newKey = () => ruleKeyCounter++

function isDirection(value: string): value is HeaderDirection {
  return (HEADER_DIRECTIONS as readonly string[]).includes(value)
}

function isAction(value: string): value is HeaderAction {
  return (HEADER_ACTIONS as readonly string[]).includes(value)
}

export function RouteFormModal({ route, organizationSlug, onClose, onSaved }: RouteFormModalProps) {
  const isEdit = route !== undefined


  const [routeId, setRouteId] = useState(route?.routeId ?? '')
  const [name, setName] = useState(route?.name ?? '')
  const [pathPattern, setPathPattern] = useState(route?.pathPattern ?? (organizationSlug ? `/${organizationSlug}/` : ''))
  const [targetBaseUrl, setTargetBaseUrl] = useState(route?.targetBaseUrl ?? '')
  const [targetPathPrefix, setTargetPathPrefix] = useState(route?.targetPathPrefix ?? '/')
  const [requestsPerMinute, setRequestsPerMinute] = useState(
    route?.requestsPerMinute != null ? String(route.requestsPerMinute) : '',
  )
  const [responseTimeoutMs, setResponseTimeoutMs] = useState(String(route?.responseTimeoutMs ?? 5000))
  const [idempotencyEnabled, setIdempotencyEnabled] = useState(route?.idempotencyEnabled ?? false)
  const [active, setActive] = useState(route?.active ?? false)
  const [methods, setMethods] = useState<string[]>(route ? [...route.allowedMethods] : ['GET'])
  const [rules, setRules] = useState<RuleDraft[]>(() =>
    (route?.headerRules ?? []).map((rule) => ({
      key: newKey(),
      direction: isDirection(rule.direction) ? rule.direction : 'REQUEST',
      action: isAction(rule.action) ? rule.action : 'ADD',
      headerName: rule.headerName,
      headerValue: rule.headerValue ?? '',
    })),
  )
  const [errors, setErrors] = useState<RouteFormErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function toggleMethod(method: string, checked: boolean) {
    setMethods((current) => (checked ? [...current, method] : current.filter((item) => item !== method)))
  }

  function updateRule(key: number, patch: Partial<Omit<RuleDraft, 'key'>>) {
    setRules((current) => current.map((rule) => (rule.key === key ? { ...rule, ...patch } : rule)))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const found = validateRoute(
      {
        routeId,
        name,
        pathPattern,
        targetBaseUrl,
        targetPathPrefix,
        requestsPerMinute,
        responseTimeoutMs,
        allowedMethods: methods,
        headerRules: rules,
      },
      organizationSlug,
    )
    setErrors(found)
    if (hasErrors(found)) {
      focusFirstInvalid(form)
      return
    }

    const payload: RouteRequest = {
      routeId: routeId.trim(),
      name: name.trim(),
      pathPattern: pathPattern.trim(),
      targetBaseUrl: targetBaseUrl.trim(),
      targetPathPrefix: targetPathPrefix.trim(),
      requestsPerMinute: requestsPerMinute.trim() ? Number(requestsPerMinute.trim()) : null,
      responseTimeoutMs: Number(responseTimeoutMs.trim()),
      idempotencyEnabled,
      active,
      allowedMethods: HTTP_METHODS.filter((method) => methods.includes(method)),
      headerRules: rules.map((rule) => ({
        direction: rule.direction,
        action: rule.action,
        headerName: rule.headerName.trim(),
        headerValue: rule.action === 'ADD' ? rule.headerValue : null,
      })),
    }

    setSubmitting(true)
    setSubmitError(null)
    try {
      const saved = isEdit ? await updateRoute(route.id, payload) : await createRoute(payload)
      onSaved(saved, isEdit ? 'updated' : 'created')
    } catch (error) {
      setSubmitError(getErrorMessage(error))
      setSubmitting(false)
    }
  }

  const formId = 'route-form'
  const ruleErrors = errors.headerRules ?? {}

  return (
    <Modal
      title={isEdit ? `Edit route "${route.name}"` : 'Create route'}
      description={
        isEdit
          ? 'Saving creates a new configuration version.'
          : 'New routes are inactive until you activate them. The gateway only serves active routes.'
      }
      size="lg"
      onClose={onClose}
      dismissible={!submitting}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={submitting}>
            {isEdit ? 'Save changes' : 'Create route'}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} noValidate className="space-y-4">
        {submitError ? <Banner tone="error">{submitError}</Banner> : null}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Route ID"
            value={routeId}
            onChange={(event) => setRouteId(event.target.value)}
            error={errors.routeId}
            hint={isEdit ? 'The Route ID cannot be changed.' : 'A unique identifier within your organization.'}
            maxLength={100}
            disabled={isEdit}
            autoComplete="off"
            spellCheck={false}
            required
          />
          <TextField
            label="Name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            error={errors.name}
            maxLength={200}
            autoComplete="off"
            required
          />
        </div>

        <TextField
          label="Path pattern"
          value={pathPattern}
          onChange={(event) => setPathPattern(event.target.value)}
          error={errors.pathPattern}
          hint={
            organizationSlug
              ? `Incoming path the gateway matches. Must start with /${organizationSlug}/.`
              : 'Incoming path the gateway matches. Must start with your organization prefix.'
          }
          maxLength={500}
          autoComplete="off"
          spellCheck={false}
          required
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Target base URL"
            type="url"
            value={targetBaseUrl}
            onChange={(event) => setTargetBaseUrl(event.target.value)}
            error={errors.targetBaseUrl}
            placeholder="https://service.example.com"
            maxLength={1000}
            autoComplete="off"
            spellCheck={false}
            required
          />
          <TextField
            label="Target path prefix"
            value={targetPathPrefix}
            onChange={(event) => setTargetPathPrefix(event.target.value)}
            error={errors.targetPathPrefix}
            hint="Must start with /."
            maxLength={500}
            autoComplete="off"
            spellCheck={false}
            required
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Requests per minute"
            inputMode="numeric"
            value={requestsPerMinute}
            onChange={(event) => setRequestsPerMinute(event.target.value)}
            error={errors.requestsPerMinute}
            hint="Leave empty for no rate limit."
            autoComplete="off"
          />
          <TextField
            label="Response timeout (ms)"
            inputMode="numeric"
            value={responseTimeoutMs}
            onChange={(event) => setResponseTimeoutMs(event.target.value)}
            error={errors.responseTimeoutMs}
            autoComplete="off"
            required
          />
        </div>

        <fieldset
          tabIndex={-1}
          aria-invalid={errors.allowedMethods ? true : undefined}
          aria-describedby={errors.allowedMethods ? 'route-methods-error' : undefined}
          className="rounded-md"
        >
          <legend className="mb-1 text-sm font-medium text-slate-700">Allowed methods</legend>
          <div className="flex flex-wrap gap-x-5 gap-y-2">
            {HTTP_METHODS.map((method) => (
              <CheckboxField
                key={method}
                label={method}
                checked={methods.includes(method)}
                onChange={(event) => toggleMethod(method, event.target.checked)}
              />
            ))}
          </div>
          {errors.allowedMethods ? (
            <p id="route-methods-error" className="mt-1 text-xs font-medium text-red-600">
              {errors.allowedMethods}
            </p>
          ) : null}
        </fieldset>

        <div className="space-y-3">
          <CheckboxField
            label="Idempotency enabled"
            description="Repeated requests with the same idempotency key are treated as one."
            checked={idempotencyEnabled}
            onChange={(event) => setIdempotencyEnabled(event.target.checked)}
          />
          <CheckboxField
            label="Active"
            description="Only active routes are served by the gateway."
            checked={active}
            onChange={(event) => setActive(event.target.checked)}
          />
        </div>

        <section aria-labelledby="route-header-rules" className="space-y-3 border-t border-slate-200 pt-4">
          <div className="flex items-center justify-between gap-3">
            <h3 id="route-header-rules" className="text-sm font-semibold text-slate-900">
              Header rules
            </h3>
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                setRules((current) => [
                  ...current,
                  { key: newKey(), direction: 'REQUEST', action: 'ADD', headerName: '', headerValue: '' },
                ])
              }
            >
              Add header rule
            </Button>
          </div>
          {rules.length === 0 ? (
            <p className="text-sm text-slate-500">No header rules. Headers pass through unchanged.</p>
          ) : null}
          {rules.map((rule, index) => (
            <div key={rule.key} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <SelectField
                  label={`Rule ${index + 1} direction`}
                  value={rule.direction}
                  onChange={(event) => {
                    if (isDirection(event.target.value)) updateRule(rule.key, { direction: event.target.value })
                  }}
                  options={DIRECTION_OPTIONS}
                />
                <SelectField
                  label={`Rule ${index + 1} action`}
                  value={rule.action}
                  onChange={(event) => {
                    if (isAction(event.target.value)) updateRule(rule.key, { action: event.target.value })
                  }}
                  options={ACTION_OPTIONS}
                />
                <TextField
                  label={`Rule ${index + 1} header name`}
                  value={rule.headerName}
                  onChange={(event) => updateRule(rule.key, { headerName: event.target.value })}
                  error={ruleErrors[index]?.headerName}
                  placeholder="X-Example"
                  maxLength={200}
                  autoComplete="off"
                  spellCheck={false}
                />
                {rule.action === 'ADD' ? (
                  <TextField
                    label={`Rule ${index + 1} header value`}
                    value={rule.headerValue}
                    onChange={(event) => updateRule(rule.key, { headerValue: event.target.value })}
                    error={ruleErrors[index]?.headerValue}
                    maxLength={2000}
                    autoComplete="off"
                    spellCheck={false}
                  />
                ) : null}
              </div>
              <div className="mt-2 flex justify-end">
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-red-600 hover:bg-red-50 hover:text-red-700"
                  onClick={() => setRules((current) => current.filter((item) => item.key !== rule.key))}
                >
                  Remove rule {index + 1}
                </Button>
              </div>
            </div>
          ))}
        </section>
      </form>
    </Modal>
  )
}
