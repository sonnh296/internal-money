import { getClient } from './httpClient'

const client = getClient('customer')

export interface CustomerCreatePayload {
  firstName: string
  lastName: string
  email: string
  phone: string
  address: string
  externalId: string
}

export function createCustomerApi(payload: CustomerCreatePayload) {
  return client.post('/api/v1/customers', payload)
}

export function getCustomerApi(externalId: string) {
  return client.get(`/api/v1/customers/${encodeURIComponent(externalId)}`)
}

export function existsByEmailApi(email: string) {
  return client.get('/api/v1/customers/exists', { params: { email } })
}

export function existsByExternalIdApi(externalId: string) {
  return client.get(`/api/v1/customers/${encodeURIComponent(externalId)}/exists`)
}

export function customerHealthApi() {
  return client.get('/api/v1/customers/health')
}

export function updateCustomerApi(
  id: string,
  payload: { fullName: string; email: string; phone: string },
  expectedVersion?: number
) {
  const headers: Record<string, string> = {}
  if (expectedVersion !== undefined && expectedVersion !== null) {
    headers['If-Match'] = `"${expectedVersion}"`
  }
  return client.patch(`/api/v1/customers/${encodeURIComponent(id)}`, payload, { headers })
}

export function updateKycApi(id: string, kycStatus: string) {
  return client.patch(`/api/v1/customers/${encodeURIComponent(id)}/kyc-status`, { kycStatus })
}

export function listAllCustomersAdminApi(page = 0, size = 100) {
  return client.get('/api/v1/admin/customers', { params: { page, size } })
}

export function notifyBalanceAdjustmentApi(payload: {
  customerId: string
  amount: number
  type: 'CREDIT' | 'DEBIT'
  reason: string
  balanceAfter: number
}) {
  return client.post('/api/v1/admin/notifications/balance-adjustment', payload)
}
