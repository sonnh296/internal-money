import axios from 'axios'
import { ref } from 'vue'
import { useNotifyStore } from '../stores/notify.store'
import { errorDetail } from '../utils/httpError'

export interface RunOptions {
  silent?: boolean
  successToast?: string
  errorToast?: string
}

export function useApiAction() {
  const running = ref(false)
  const notify = useNotifyStore()

  async function run<T>(title: string, requestFn: () => Promise<T>, options: RunOptions = {}): Promise<T> {
    running.value = true
    const startedAt = Date.now()
    try {
      const response = await requestFn()
      notify.recordSuccess(title, response as object, startedAt)
      if (!options.silent) {
        notify.push('success', options.successToast ?? 'Thành công', title)
      }
      return response
    } catch (error) {
      notify.recordError(title, error, startedAt)
      let detail = options.errorToast ?? ''
      if (!detail) {
        if (axios.isAxiosError(error)) {
          const status = error.response?.status
          if (status === 409) {
            notify.triggerConflict()
          }
          detail = errorDetail(error)
          if (!detail) detail = status ? `HTTP ${status}` : 'Lỗi mạng'
        } else if (error instanceof Error) {
          detail = error.message
        } else {
          detail = String(error)
        }
      }
      notify.push('error', 'Thất bại', `${title} — ${detail}`)
      throw error
    } finally {
      running.value = false
    }
  }

  return { running, run }
}
