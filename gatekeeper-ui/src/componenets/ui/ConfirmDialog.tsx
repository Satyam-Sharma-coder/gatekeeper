import { useState } from 'react'
import type { ReactNode } from 'react'
import { getErrorMessage } from '../../api/client.ts'
import { Banner } from './Banner.tsx'
import { Button } from './Button.tsx'
import { Modal } from './Modal.tsx'

interface ConfirmDialogProps {
  title: string
  confirmLabel: string
  tone?: 'danger' | 'primary'
  onConfirm: () => Promise<void>
  onClose: () => void
  children: ReactNode
}

export function ConfirmDialog({
  title,
  confirmLabel,
  tone = 'danger',
  onConfirm,
  onClose,
  children,
}: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
    } catch (caught) {
      setError(getErrorMessage(caught))
      setBusy(false)
    }
  }

  return (
    <Modal
      title={title}
      size="sm"
      onClose={onClose}
      dismissible={!busy}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button variant={tone} onClick={handleConfirm} loading={busy}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm text-slate-600">
        {children}
        {error ? <Banner tone="error">{error}</Banner> : null}
      </div>
    </Modal>
  )
}
