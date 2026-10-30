import { useState } from 'react'
import type { FormEvent } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { createOrganization, updateOrganization } from '../../api/organizations.ts'
import type { Organization } from '../../api/types.ts'
import { focusFirstInvalid } from '../../lib/dom.ts'
import { hasErrors, slugify, validateOrganization } from '../../lib/validation.ts'
import type { FieldErrors } from '../../lib/validation.ts'
import { Banner } from '../ui/Banner.tsx'
import { Button } from '../ui/Button.tsx'
import { Modal } from '../ui/Modal.tsx'
import { TextField } from '../ui/TextField.tsx'

interface OrganizationFormModalProps {
  organization?: Organization
  onClose: () => void
  onSaved: (organization: Organization, mode: 'created' | 'updated') => void
}

export function OrganizationFormModal({ organization, onClose, onSaved }: OrganizationFormModalProps) {
  const isEdit = organization !== undefined
  const [name, setName] = useState(organization?.name ?? '')
  const [slug, setSlug] = useState(organization?.slug ?? '')
  const [slugTouched, setSlugTouched] = useState(isEdit)
  const [errors, setErrors] = useState<FieldErrors<'name' | 'slug'>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function handleNameChange(value: string) {
    setName(value)
    if (!slugTouched) setSlug(slugify(value))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const found = validateOrganization(name, slug)
    setErrors(found)
    if (hasErrors(found)) {
      focusFirstInvalid(form)
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    const payload = { name: name.trim(), slug: slug.trim() }
    try {
      const saved = isEdit
        ? await updateOrganization(organization.id, payload)
        : await createOrganization(payload)
      onSaved(saved, isEdit ? 'updated' : 'created')
    } catch (error) {
      setSubmitError(getErrorMessage(error))
      setSubmitting(false)
    }
  }

  const formId = 'organization-form'

  return (
    <Modal
      title={isEdit ? 'Edit organization' : 'Create organization'}
      description={isEdit ? undefined : 'New organizations start in Pending status.'}
      onClose={onClose}
      dismissible={!submitting}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={submitting}>
            {isEdit ? 'Save changes' : 'Create organization'}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} noValidate className="space-y-4">
        {submitError ? <Banner tone="error">{submitError}</Banner> : null}
        <TextField
          label="Name"
          value={name}
          onChange={(event) => handleNameChange(event.target.value)}
          error={errors.name}
          maxLength={150}
          autoComplete="off"
          required
        />
        <TextField
          label="Slug"
          value={slug}
          onChange={(event) => {
            setSlugTouched(true)
            setSlug(event.target.value)
          }}
          error={errors.slug}
          hint="Lowercase letters, digits and single hyphens, for example acme-corp."
          maxLength={100}
          autoComplete="off"
          spellCheck={false}
          required
        />
      </form>
    </Modal>
  )
}
