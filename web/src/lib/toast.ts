/**
 * Toast wrapper — thin layer over `sonner` so callers depend on this file,
 * not the library directly. Makes a future swap (or removal) a one-file
 * change and gives us a place to add per-call instrumentation.
 *
 * S271 — added to replace 14+ `window.alert()` calls scattered across the
 * admin tool. Operator-only tool, so we lean on visible richColors + a
 * 5-second auto-dismiss; no blocking modal alert.
 *
 * Usage:
 *   import { toastError, toastSuccess, toastInfo } from '../lib/toast'
 *   toastError('Save failed: ' + err.message)
 *
 * The <Toaster /> component must be mounted once at the admin root —
 * see AdminLayout.tsx.
 */
import { toast } from 'sonner'

export function toastError(msg: string): void {
  toast.error(msg)
}

export function toastSuccess(msg: string): void {
  toast.success(msg)
}

export function toastInfo(msg: string): void {
  toast.info(msg)
}

export function toastWarning(msg: string): void {
  toast.warning(msg)
}
