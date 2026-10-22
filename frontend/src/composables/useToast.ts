import { ref } from 'vue'

export interface ToastAction {
  label: string
  run: () => void
}

interface Toast {
  message: string
  action?: ToastAction
}

const TOAST_MS = 4000

const toast = ref<Toast | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined

function dismiss() {
  clearTimeout(timer)
  toast.value = null
}

function show(message: string, action?: ToastAction) {
  clearTimeout(timer)
  toast.value = { message, action }
  timer = setTimeout(dismiss, TOAST_MS)
}

// A single shared toast, rendered once by ToastHost.
export function useToast() {
  return { toast, show, dismiss }
}
