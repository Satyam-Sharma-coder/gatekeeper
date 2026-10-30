export function focusFirstInvalid(form: HTMLFormElement | null): void {
  if (!form) return
  window.requestAnimationFrame(() => {
    form.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  })
}
