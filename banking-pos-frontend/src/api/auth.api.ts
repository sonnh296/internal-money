import { getClient } from './httpClient'

const client = getClient('auth')

export function loginApi(payload: { email: string; password: string }) {
  return client.post('/api/v1/auth/login', payload)
}

export function refreshApi(refreshToken: string) {
  return client.post('/api/v1/auth/refresh', { refresh_token: refreshToken })
}

export function logoutApi(refreshToken: string) {
  return client.post('/api/v1/auth/logout', { refresh_token: refreshToken })
}

export interface CreateInternalUserPayload {
  email: string
  temporaryPassword: string
  customerId: string
}

export function createInternalUserApi(payload: CreateInternalUserPayload) {
  return client.post('/api/v1/internal/users', payload)
}

export function createInternalUserLegacyApi(payload: CreateInternalUserPayload) {
  return client.post('/api/v1/iam/users', payload)
}

export function changePasswordApi(payload: { currentPassword: string; newPassword: string }) {
  return client.post('/api/v1/auth/change-password', payload)
}

export function listManagersApi() {
  return client.get('/api/v1/admin/managers')
}

export interface CreateManagerPayload {
  email: string
  customerId: string
  temporaryPassword: string
}

export function createManagerApi(payload: CreateManagerPayload) {
  return client.post('/api/v1/admin/managers', payload)
}

export function toggleManagerApi(id: string) {
  return client.delete(`/api/v1/admin/managers/${id}/toggle`)
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
