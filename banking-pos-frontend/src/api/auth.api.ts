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

export function changePasswordApi(payload: { currentPassword: string; newPassword: string }) {
  return client.post('/api/v1/auth/change-password', payload)
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
