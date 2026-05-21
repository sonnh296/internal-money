import type { StoredAuthPayload } from '../types/api.types'
import { env } from '../config/env'

/** Cookie mode: không lưu JWT; legacy: sessionStorage (không dùng localStorage). */
function storage(): Storage {
  return sessionStorage
}

export function loadAuth(key: string): StoredAuthPayload | null {
  try {
    const raw = storage().getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredAuthPayload
    if (env.useAuthCookies) {
      return {
        accessToken: '',
        refreshToken: '',
        expiresIn: parsed.expiresIn ?? 0,
        issuedAt: parsed.issuedAt ?? 0,
        profile: parsed.profile ?? { email: '', customerId: '', scopes: [] }
      }
    }
    return parsed
  } catch {
    return null
  }
}

export function saveAuth(key: string, payload: StoredAuthPayload): void {
  if (env.useAuthCookies) {
    storage().setItem(
      key,
      JSON.stringify({
        expiresIn: payload.expiresIn,
        issuedAt: payload.issuedAt,
        profile: payload.profile
      })
    )
    return
  }
  storage().setItem(key, JSON.stringify(payload))
}

export function clearAuth(key: string): void {
  storage().removeItem(key)
}

export function decodeJwtClaims(token: string): Record<string, unknown> {
  if (!token) return {}
  const parts = token.split('.')
  if (parts.length < 2) return {}
  try {
    const padded = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = atob(padded + '==='.slice((padded.length + 3) % 4))
    return JSON.parse(json)
  } catch {
    return {}
  }
}

export function extractScopes(token: string): string[] {
  const claims = decodeJwtClaims(token)
  const scopes = new Set<string>()
  const permissions = claims.permissions
  if (Array.isArray(permissions)) {
    for (const p of permissions) scopes.add(String(p))
  }
  const scope = claims.scope
  if (typeof scope === 'string') {
    for (const s of scope.split(' ').filter(Boolean)) scopes.add(s)
  }
  return [...scopes]
}

export function extractCustomerId(token: string): string {
  const claims = decodeJwtClaims(token)
  return typeof claims.customer_id === 'string' ? claims.customer_id : ''
}

export function extractRole(token: string): string {
  const claims = decodeJwtClaims(token)
  return typeof claims.role === 'string' ? claims.role : ''
}
