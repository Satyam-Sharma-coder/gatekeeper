export type FieldErrors<K extends string> = Partial<Record<K, string>>

export const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 100)
    .replace(/-+$/g, '')
}

export function hasErrors(errors: object): boolean {
  return Object.keys(errors).length > 0
}

export function validateLogin(username: string, password: string): FieldErrors<'username' | 'password'> {
  const errors: FieldErrors<'username' | 'password'> = {}
  if (!username.trim()) errors.username = 'Enter your username.'
  if (!password) errors.password = 'Enter your password.'
  return errors
}

export function validateOrganization(name: string, slug: string): FieldErrors<'name' | 'slug'> {
  const errors: FieldErrors<'name' | 'slug'> = {}
  if (!name.trim()) errors.name = 'Name is required.'
  else if (name.trim().length > 150) errors.name = 'Name must be 150 characters or fewer.'
  if (!slug.trim()) errors.slug = 'Slug is required.'
  else if (slug.trim().length > 100) errors.slug = 'Slug must be 100 characters or fewer.'
  else if (!SLUG_PATTERN.test(slug.trim())) {
    errors.slug = 'Use lowercase letters and digits, separated by single hyphens (for example acme-corp).'
  }
  return errors
}

export function validateTenantAdmin(username: string, password: string): FieldErrors<'username' | 'password'> {
  const errors: FieldErrors<'username' | 'password'> = {}
  if (!username.trim()) errors.username = 'Username is required.'
  else if (username.trim().length > 100) errors.username = 'Username must be 100 characters or fewer.'
  if (!password) errors.password = 'Password is required.'
  else if (password.length < 8) errors.password = 'Password must be at least 8 characters.'
  else if (password.length > 100) errors.password = 'Password must be 100 characters or fewer.'
  return errors
}

export function validateOwnOrganizationName(name: string): FieldErrors<'name'> {
  const errors: FieldErrors<'name'> = {}
  if (!name.trim()) errors.name = 'Name is required.'
  else if (name.trim().length > 150) errors.name = 'Name must be 150 characters or fewer.'
  return errors
}

export interface RouteFormValues {
  routeId: string
  name: string
  pathPattern: string
  targetBaseUrl: string
  targetPathPrefix: string
  requestsPerMinute: string
  responseTimeoutMs: string
  allowedMethods: readonly string[]
  headerRules: readonly { action: string; headerName: string; headerValue: string }[]
}

export type RouteFieldKey =
  | 'routeId'
  | 'name'
  | 'pathPattern'
  | 'targetBaseUrl'
  | 'targetPathPrefix'
  | 'requestsPerMinute'
  | 'responseTimeoutMs'
  | 'allowedMethods'

export type HeaderRuleErrors = Record<number, { headerName?: string; headerValue?: string }>

export type RouteFormErrors = FieldErrors<RouteFieldKey> & { headerRules?: HeaderRuleErrors }

function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function isPositiveInteger(value: string): boolean {
  return /^\d+$/.test(value) && Number(value) >= 1 && Number.isSafeInteger(Number(value))
}

export function validateRoute(values: RouteFormValues, organizationSlug: string | null): RouteFormErrors {
  const errors: RouteFormErrors = {}
  const routeId = values.routeId.trim()
  const name = values.name.trim()
  const pathPattern = values.pathPattern.trim()
  const baseUrl = values.targetBaseUrl.trim()
  const prefix = values.targetPathPrefix.trim()

  if (!routeId) errors.routeId = 'Route ID is required.'
  else if (routeId.length > 100) errors.routeId = 'Route ID must be 100 characters or fewer.'

  if (!name) errors.name = 'Name is required.'
  else if (name.length > 200) errors.name = 'Name must be 200 characters or fewer.'

  if (!pathPattern) errors.pathPattern = 'Path pattern is required.'
  else if (pathPattern.length > 500) errors.pathPattern = 'Path pattern must be 500 characters or fewer.'
  else if (organizationSlug && !pathPattern.startsWith(`/${organizationSlug}/`)) {
    errors.pathPattern = `Path pattern must start with your organization prefix /${organizationSlug}/.`
  }

  if (!baseUrl) errors.targetBaseUrl = 'Target base URL is required.'
  else if (baseUrl.length > 1000) errors.targetBaseUrl = 'Target base URL must be 1000 characters or fewer.'
  else if (!isHttpUrl(baseUrl)) errors.targetBaseUrl = 'Enter a valid http:// or https:// URL.'

  if (!prefix) errors.targetPathPrefix = 'Target path prefix is required.'
  else if (prefix.length > 500) errors.targetPathPrefix = 'Target path prefix must be 500 characters or fewer.'
  else if (!prefix.startsWith('/')) errors.targetPathPrefix = 'Target path prefix must start with /.'

  if (values.requestsPerMinute.trim() && !isPositiveInteger(values.requestsPerMinute.trim())) {
    errors.requestsPerMinute = 'Enter a whole number of 1 or more, or leave empty for no limit.'
  }

  if (!values.responseTimeoutMs.trim()) errors.responseTimeoutMs = 'Response timeout is required.'
  else if (!isPositiveInteger(values.responseTimeoutMs.trim())) {
    errors.responseTimeoutMs = 'Enter a whole number of milliseconds, 1 or more.'
  }

  if (values.allowedMethods.length === 0) errors.allowedMethods = 'Select at least one HTTP method.'

  const ruleErrors: HeaderRuleErrors = {}
  values.headerRules.forEach((rule, index) => {
    const found: { headerName?: string; headerValue?: string } = {}
    const headerName = rule.headerName.trim()
    if (!headerName) found.headerName = 'Header name is required.'
    else if (headerName.length > 200) found.headerName = 'Header name must be 200 characters or fewer.'
    if (rule.action === 'ADD') {
      if (!rule.headerValue.trim()) found.headerValue = 'A value is required when adding a header.'
      else if (rule.headerValue.length > 2000) found.headerValue = 'Value must be 2000 characters or fewer.'
    }
    if (hasErrors(found)) ruleErrors[index] = found
  })
  if (hasErrors(ruleErrors)) errors.headerRules = ruleErrors

  return errors
}
