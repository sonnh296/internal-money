import axios from 'axios'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { env } from '../config/env'
import { loginApi, logoutApi, refreshApi } from '../api/auth.api'
import { fetchAuthProfile } from './authProfileSync'
import { ADMIN_SCOPE_HINTS } from '../constants/app.constants'
import type { AuthProfile, TokenResponseDto } from '../types/api.types'
import {
  clearAuth,
  extractCustomerId,
  extractRole,
  extractScopes,
  loadAuth,
  saveAuth
} from '../utils/storage'
import { errorDetail } from '../utils/httpError'

const STORAGE_KEY = 'bp_admin_session'

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const accessToken = ref('')
  const refreshToken = ref('')
  const expiresIn = ref(0)
  const issuedAt = ref(0)
  const profile = ref<AuthProfile>({ email: '', customerId: '', scopes: [] })
  const loading = ref(false)
  const error = ref('')

  const isAuthenticated = computed(() =>
    env.useAuthCookies ? Boolean(profile.value.email) : Boolean(accessToken.value)
  )
  const isAdmin = computed(() => {
    if (!isAuthenticated.value) return false
    if (profile.value.customerId.startsWith('admin-')) return true
    return profile.value.scopes.some((s) => ADMIN_SCOPE_HINTS.includes(s as never))
  })
  const role = computed(() =>
    env.useAuthCookies
      ? (profile.value.scopes.includes('SUPER_ADMIN') ? 'SUPER_ADMIN' : 'ADMIN')
      : extractRole(accessToken.value)
  )
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const expiresAt = computed(() =>
    issuedAt.value && expiresIn.value ? issuedAt.value + expiresIn.value * 1000 : 0
  )

  function hydrate(): void {
    const cached = loadAuth(STORAGE_KEY)
    if (!cached) return
    accessToken.value = cached.accessToken ?? ''
    refreshToken.value = cached.refreshToken ?? ''
    expiresIn.value = cached.expiresIn ?? 0
    issuedAt.value = cached.issuedAt ?? 0
    profile.value = cached.profile ?? { email: '', customerId: '', scopes: [] }
  }

  function persist(): void {
    saveAuth(STORAGE_KEY, {
      accessToken: accessToken.value,
      refreshToken: refreshToken.value,
      expiresIn: expiresIn.value,
      issuedAt: issuedAt.value,
      profile: profile.value
    })
  }

  async function syncProfileFromServer(): Promise<void> {
    profile.value = await fetchAuthProfile('admin')
    issuedAt.value = Date.now()
    persist()
  }

  function applyToken(data: TokenResponseDto, email?: string): void {
    accessToken.value = data.access_token ?? ''
    refreshToken.value = data.refresh_token ?? ''
    expiresIn.value = data.expires_in
    issuedAt.value = Date.now()
    if (env.useAuthCookies) {
      if (email) {
        profile.value.email = email
      }
      persist()
      return
    }
    const scopes = extractScopes(data.access_token)
    const tokenCustomerId = extractCustomerId(data.access_token)
    profile.value = {
      email: email ?? profile.value.email,
      customerId: tokenCustomerId || profile.value.customerId,
      scopes
    }
    persist()
  }

  async function login(payload: { email: string; password: string }): Promise<TokenResponseDto> {
    loading.value = true
    error.value = ''
    try {
      const { data } = await loginApi(payload, 'admin')
      applyToken(data as TokenResponseDto, payload.email)
      if (env.useAuthCookies) {
        await syncProfileFromServer()
      }
      return data as TokenResponseDto
    } catch (err) {
      if (axios.isAxiosError(err)) {
        error.value = errorDetail(err)
      } else {
        error.value = 'Unknown login error'
      }
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refresh(): Promise<TokenResponseDto | null> {
    if (!env.useAuthCookies && !refreshToken.value) return null
    const { data } = await refreshApi(refreshToken.value, 'admin')
    applyToken(data as TokenResponseDto)
    if (env.useAuthCookies) {
      await syncProfileFromServer()
    }
    return data as TokenResponseDto
  }

  async function logout(): Promise<void> {
    const stale = refreshToken.value
    accessToken.value = ''
    refreshToken.value = ''
    expiresIn.value = 0
    issuedAt.value = 0
    profile.value = { email: '', customerId: '', scopes: [] }
    clearAuth(STORAGE_KEY)
    try {
      await logoutApi(stale, 'admin')
    } catch {
      // swallow
    }
  }

  hydrate()

  return {
    accessToken,
    refreshToken,
    expiresIn,
    issuedAt,
    profile,
    loading,
    error,
    isAuthenticated,
    isAdmin,
    role,
    isSuperAdmin,
    expiresAt,
    login,
    refresh,
    logout,
    applyToken
  }
})
