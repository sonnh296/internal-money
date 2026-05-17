import { getClient } from './httpClient'

const client = getClient('account')

export interface AccountCreatePayload {
  customerId: string
  accountType: string
  accountSubType: string
  status: string
  currency: string
  nickname: string
  displayName: string
  openingBalance: number
}

function ifMatchHeader(expectedVersion?: number): Record<string, string> {
  const headers: Record<string, string> = {}
  if (expectedVersion !== undefined && expectedVersion !== null && !Number.isNaN(expectedVersion)) {
    headers['If-Match'] = `"${expectedVersion}"`
  }
  return headers
}

function postingHeaders(
  expectedVersion?: number,
  idempotencyKey?: string
): Record<string, string> {
  const headers = ifMatchHeader(expectedVersion)
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey
  }
  return headers
}

export function createAccountApi(payload: AccountCreatePayload, idemKey: string) {
  return client.post('/api/v1/accounts', payload, {
    headers: { 'Idempotency-Key': idemKey }
  })
}

export function getMyAccountApi() {
  return client.get('/api/v1/my-account')
}

export function lookupAccountApi(accountNumber: string) {
  return client.get('/api/v1/accounts/lookup', { params: { accountNumber } })
}

export function listCustomerAccountsApi(customerId: string) {
  return client.get(`/api/v1/customer/${encodeURIComponent(customerId)}/accounts`)
}

export function listAllAccountsAdminApi(params: { status?: string; currency?: string } = {}) {
  return client.get('/api/v1/accounts', { params })
}

export function getAccountApi(accountId: string) {
  return client.get(`/api/v1/accounts/${encodeURIComponent(accountId)}`)
}

export function updateAccountStatusApi(accountId: string, status: string) {
  return client.patch(`/api/v1/accounts/${encodeURIComponent(accountId)}/status`, null, {
    params: { status }
  })
}

export function getAccountOwnerApi(accountId: string) {
  return client.get(`/api/v1/accounts/${encodeURIComponent(accountId)}/owner`)
}

export function getBalanceApi(accountId: string) {
  return client.get(`/api/v1/accounts/${encodeURIComponent(accountId)}/balance`)
}

export function creditApi(
  accountId: string,
  payload: { amount: number; reason: string; idempotencyKey?: string; externalTransactionId?: string },
  expectedVersion?: number
) {
  const idemKey = payload.idempotencyKey ?? crypto.randomUUID()
  return client.post(`/api/v1/accounts/${encodeURIComponent(accountId)}/credit`, payload, {
    headers: postingHeaders(expectedVersion, idemKey)
  })
}

export function debitApi(
  accountId: string,
  payload: { amount: number; reason: string; idempotencyKey?: string; externalTransactionId?: string },
  expectedVersion?: number
) {
  const idemKey = payload.idempotencyKey ?? crypto.randomUUID()
  return client.post(`/api/v1/accounts/${encodeURIComponent(accountId)}/debit`, payload, {
    headers: postingHeaders(expectedVersion, idemKey)
  })
}

export function transferApi(
  payload: { toAccountNumber: string; amount: number; reason: string; fromAccountId?: string },
  idemKey: string
) {
  return client.post('/api/v1/accounts/transfer', payload, {
    headers: { 'Idempotency-Key': idemKey }
  })
}

export function holdApi(
  accountId: string,
  payload: { amount: number; reason: string; releaseAt: string | null },
  idemKey: string
) {
  return client.post(`/api/v1/accounts/${encodeURIComponent(accountId)}/holds`, payload, {
    headers: { 'Idempotency-Key': idemKey }
  })
}

export function releaseHoldApi(accountId: string, holdId: string) {
  return client.post(
    `/api/v1/accounts/${encodeURIComponent(accountId)}/holds/${encodeURIComponent(holdId)}/release`
  )
}

export function listTransactionsApi(
  accountId: string,
  params: Record<string, string | number | undefined> = {}
) {
  return client.get(`/api/v1/accounts/${encodeURIComponent(accountId)}/transactions`, { params })
}

export function getTransactionApi(transactionId: string) {
  return client.get(`/api/v1/transactions/${encodeURIComponent(transactionId)}`)
}
