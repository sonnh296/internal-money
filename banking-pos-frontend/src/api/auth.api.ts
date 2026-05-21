import type { PortalKind } from '../types/api.types'
import { env } from '../config/env'
import { getClient } from './httpClient'

const client = getClient('auth')

function portalHeaders(portal: PortalKind): Record<string, string> {
  return { 'X-Portal': portal }
}

export function loginApi(payload: { email: string; password: string }, portal: PortalKind = 'user') {
  return client.post('/api/v1/auth/login', payload, { headers: portalHeaders(portal) })
}

export function refreshApi(refreshToken: string, portal: PortalKind = 'user') {
  const body = env.useAuthCookies && !refreshToken ? {} : { refresh_token: refreshToken }
  return client.post('/api/v1/auth/refresh', body, { headers: portalHeaders(portal) })
}

export function logoutApi(refreshToken: string, portal: PortalKind = 'user') {
  const body = env.useAuthCookies && !refreshToken ? {} : { refresh_token: refreshToken }
  return client.post('/api/v1/auth/logout', body, { headers: portalHeaders(portal) })
}

export function meApi(portal: PortalKind = 'user') {
  return client.get('/api/v1/auth/me', { headers: portalHeaders(portal) })
}

export function changePasswordApi(payload: { currentPassword: string; newPassword: string }, portal: PortalKind = 'user') {
  return client.post('/api/v1/auth/change-password', payload, { headers: portalHeaders(portal) })
}

export function testPublicApi() {
  return client.get('/api/v1/test/public')
}

export function testSecureApi() {
  return client.get('/api/v1/test/secure')
}

export function jwksApi() {
  return client.get('/.well-known/jwks.json')
}

export function openidConfigApi() {
  return client.get('/.well-known/openid-configuration')
}
