import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { env } from '../config/env'
import { getClient } from './httpClient'
import { useAdminAuthStore } from '../stores/adminAuth.store'
import { useNotifyStore } from '../stores/notify.store'
import { useUserAuthStore } from '../stores/userAuth.store'
import type { PortalKind, ServiceName } from '../types/api.types'

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean; _portal?: PortalKind | null }

const services: ServiceName[] = ['auth', 'customer', 'account', 'payment', 'biller']

function isAuthEndpoint(url?: string): boolean {
  if (!url) return false
  if (url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/logout')) {
    return true
  }
  // Cookie mode: /me dùng HttpOnly cookie, không refresh bằng body token
  if (env.useAuthCookies && url.includes('/auth/me')) {
    return true
  }
  return false
}

function sessionInvalidMessage(error: AxiosError): boolean {
  const data = error.response?.data as { message?: string; error?: string } | undefined
  const msg = String(data?.message ?? data?.error ?? '').toUpperCase()
  return msg.includes('SESSION_INVALIDATED') || msg.includes('REFRESH_TOKEN_REVOKED')
}

export function setupHttpInterceptors(): void {
  for (const service of services) {
    const client = getClient(service)
    client.interceptors.request.use((config) => {
      const retryConfig = config as RetryConfig
      if (!retryConfig._portal) {
        retryConfig._portal = (config.headers['X-Portal'] as PortalKind | undefined) ?? null
      }
      return config
    })

    client.interceptors.response.use(
      (response) => response,
      async (error: AxiosError) => {
        const config = error.config as RetryConfig | undefined
        if (!config || config._retry || error.response?.status !== 401 || isAuthEndpoint(config.url)) {
          return Promise.reject(error)
        }

        const portal =
          config._portal ?? (config.headers?.['X-Portal'] as PortalKind | undefined) ?? null
        const userAuth = useUserAuthStore()
        const adminAuth = useAdminAuthStore()
        const notify = useNotifyStore()

        config._retry = true
        try {
          if (portal === 'admin') {
            await adminAuth.refresh()
          } else {
            await userAuth.refresh()
          }
          if (env.useAuthCookies) {
            delete config.headers.Authorization
          } else if (portal === 'admin') {
            if (adminAuth.accessToken) {
              config.headers.Authorization = `Bearer ${adminAuth.accessToken}`
            } else {
              delete config.headers.Authorization
            }
          } else if (userAuth.accessToken) {
            config.headers.Authorization = `Bearer ${userAuth.accessToken}`
          } else {
            delete config.headers.Authorization
          }
          return client(config)
        } catch (refreshError) {
          if (axios.isAxiosError(refreshError) && sessionInvalidMessage(refreshError)) {
            notify.triggerConflict()
            notify.push(
              'info',
              'Phiên đăng nhập đã thay đổi',
              'Mật khẩu hoặc phiên đăng nhập đã thay đổi ở nơi khác. Vui lòng tải lại trang.'
            )
          } else {
            notify.triggerConflict()
            notify.push('info', 'Phiên hết hạn', 'Vui lòng đăng nhập lại.')
          }
          return Promise.reject(refreshError)
        }
      }
    )
  }
}
