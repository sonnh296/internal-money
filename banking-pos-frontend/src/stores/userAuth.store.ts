import axios from 'axios'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { loginApi, logoutApi, refreshApi } from '../api/auth.api'
import type { AuthProfile, TokenResponseDto } from '../types/api.types'
import {
  clearAuth,
  extractCustomerId,
  extractScopes,
  loadAuth,
  saveAuth
} from '../utils/storage'
import { errorDetail } from '../utils/httpError'

const STORAGE_KEY = 'bp_user_session'

export const useUserAuthStore = defineStore('userAuth', () => {
  const accessToken = ref('')
  const refreshToken = ref('')
  const expiresIn = ref(0)
  const issuedAt = ref(0)
  const profile = ref<AuthProfile>({ email: '', customerId: '', scopes: [] })
  const loading = ref(false)
  const error = ref('')

  const isAuthenticated = computed(() => Boolean(accessToken.value))
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

  function applyToken(data: TokenResponseDto, email?: string): void {
    accessToken.value = data.access_token
    refreshToken.value = data.refresh_token
    expiresIn.value = data.expires_in
    issuedAt.value = Date.now()
    const scopes = extractScopes(data.access_token)
    const tokenCustomerId = extractCustomerId(data.access_token)
    profile.value = {
      email: email ?? profile.value.email,
      customerId: tokenCustomerId || profile.value.customerId,
      scopes
    }
    persist()
  }

  function setCustomerId(customerId: string): void {
    profile.value.customerId = customerId
    persist()
  }

  async function login(payload: { email: string; password: string }): Promise<TokenResponseDto> {
    loading.value = true
    error.value = ''
    try {
      const { data } = await loginApi(payload)
      applyToken(data as TokenResponseDto, payload.email)
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
    if (!refreshToken.value) return null
    const { data } = await refreshApi(refreshToken.value)
    applyToken(data as TokenResponseDto)
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
    if (stale) {
      try {
        await logoutApi(stale)
      } catch {
        // swallow
      }
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
    expiresAt,
    login,
    refresh,
    logout,
    setCustomerId,
    applyToken
  }
})
