import { getClient } from './httpClient'

const client = getClient('payment')

export interface BillPayPayload {
  debtorAccountId: string
  billerReferenceNumber: string
  invoiceReference: string
  executionDate: string
  amount: { value: number; currency: string }
  note: string
  pointsToRedeem?: number
}

export function createBillPayApi(payload: BillPayPayload, idemKey: string) {
  return client.post('/api/v1/payments/billpay', payload, {
    headers: { 'Idempotency-Key': idemKey }
  })
}

export function getPaymentApi(paymentId: string) {
  return client.get(`/api/v1/payments/${encodeURIComponent(paymentId)}`)
}

export function getMyRewardPointsApi() {
  return client.get('/api/v1/rewards/me')
}
