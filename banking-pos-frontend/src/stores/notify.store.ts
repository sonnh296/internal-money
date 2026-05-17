import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios, { type AxiosResponse } from 'axios'
import type { LastResultState } from '../types/api.types'

type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  title: string
  message: string
  ts: number
}

export interface RawCall extends LastResultState {
  id: number
  service?: string
}

function formatBody(data: unknown): string {
  if (data === null || data === undefined || data === '') return '(empty body)'
  if (typeof data === 'string') return data
  try {
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
}

type LooseResponse = Partial<AxiosResponse> & { status?: number; statusText?: string; data?: unknown }

export const useNotifyStore = defineStore('notify', () => {
  const toasts = ref<Toast[]>([])
  const recentCalls = ref<RawCall[]>([])
  const conflictDetected = ref(false)
  const maxRecent = 25
  let seq = 1

  function push(kind: ToastKind, title: string, message = ''): void {
    const id = seq++
    toasts.value = [...toasts.value, { id, kind, title, message, ts: Date.now() }]
    setTimeout(() => dismiss(id), kind === 'error' ? 7000 : 3500)
  }

  function dismiss(id: number): void {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  function recordSuccess(title: string, response: LooseResponse, startedAt: number): void {
    const id = seq++
    const statusCode = typeof response.status === 'number' ? response.status : 200
    const statusText = response.statusText ?? 'OK'
    const ok = statusCode >= 200 && statusCode < 400
    recentCalls.value = [
      {
        id,
        title,
        ok,
        status: `${statusCode} ${statusText}`,
        startedAt,
        durationMs: Date.now() - startedAt,
        headers: response.headers ?? {},
        body: formatBody(response.data !== undefined ? response.data : response)
      },
      ...recentCalls.value
    ].slice(0, maxRecent)
  }

  function recordError(title: string, error: unknown, startedAt: number): void {
    const id = seq++
    if (axios.isAxiosError(error)) {
      const code = error.response?.status ?? 0
      const text = error.response?.statusText ?? 'NETWORK_ERROR'
      recentCalls.value = [
        {
          id,
          title,
          ok: false,
          status: `${code || 'ERR'} ${text}`,
          startedAt,
          durationMs: Date.now() - startedAt,
          headers: error.response?.headers ?? {},
          body: formatBody(error.response?.data ?? error.message)
        },
        ...recentCalls.value
      ].slice(0, maxRecent)
      return
    }
    recentCalls.value = [
      {
        id,
        title,
        ok: false,
        status: 'UNKNOWN_ERROR',
        startedAt,
        durationMs: Date.now() - startedAt,
        headers: {},
        body: formatBody(error instanceof Error ? error.message : String(error))
      },
      ...recentCalls.value
    ].slice(0, maxRecent)
  }

  function clearCalls(): void {
    recentCalls.value = []
  }

  function triggerConflict(): void {
    conflictDetected.value = true
  }

  function clearConflict(): void {
    conflictDetected.value = false
  }

  return {
    toasts,
    recentCalls,
    conflictDetected,
    push,
    dismiss,
    recordSuccess,
    recordError,
    clearCalls,
    triggerConflict,
    clearConflict
  }
})
