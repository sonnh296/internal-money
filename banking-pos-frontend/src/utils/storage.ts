import type { StoredAuthPayload } from '../types/api.types'

export function loadAuth(key: string): StoredAuthPayload | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as StoredAuthPayload) : null
  } catch {
    return null
  }
}

export function saveAuth(key: string, payload: StoredAuthPayload): void {
  localStorage.setItem(key, JSON.stringify(payload))
}

export function clearAuth(key: string): void {
  localStorage.removeItem(key)
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
