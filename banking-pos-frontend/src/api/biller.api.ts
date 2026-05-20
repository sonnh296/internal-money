import { getClient } from './httpClient'

const client = getClient('biller')

export interface BillerCreatePayload {
  name: string
  referenceNumber: string
  category: string
}

export function createBillerApi(payload: BillerCreatePayload) {
  return client.post('/api/v1/billers', payload)
}

export function listBillersApi(limit = 10, offset = 0) {
  return client.get('/api/v1/billers', { params: { limit, offset } })
}

export function getBillerApi(id: string) {
  return client.get(`/api/v1/billers/${encodeURIComponent(id)}`)
}

export function deleteBillerApi(id: string) {
  return client.delete(`/api/v1/billers/${encodeURIComponent(id)}`)
}

export function isBillerActiveApi(referenceNumber: string) {
  return client.get(`/api/v1/billers/${encodeURIComponent(referenceNumber)}/active`)
}

// ── Service Packages ─────────────────────────────────────────────────────────

export interface ServicePackagePayload {
  name: string
  category: string
  referenceNumber: string
  monthlyAmount: number
  currency?: string
  description?: string
}

export function createServicePackageApi(payload: ServicePackagePayload) {
  return client.post('/api/v1/services', payload)
}

export function listServicePackagesApi(all = false, limit = 20, offset = 0) {
  return client.get('/api/v1/services', { params: { all, limit, offset } })
}

export function getServicePackageApi(id: string) {
  return client.get(`/api/v1/services/${id}`)
}

export function toggleServicePackageApi(id: string) {
  return client.patch(`/api/v1/services/${id}/toggle`)
}

export function deleteServicePackageApi(id: string) {
  return client.delete(`/api/v1/services/${id}`)
}

// ── Subscriptions ─────────────────────────────────────────────────────────────

export function subscribeApi(packageId: string) {
  return client.post('/api/v1/subscriptions', { packageId })
}

export function listMySubscriptionsApi() {
  return client.get('/api/v1/subscriptions/me')
}

export function listIssuableSubscriptionsApi() {
  return client.get('/api/v1/subscriptions/admin/issuable')
}

export function cancelSubscriptionApi(id: string) {
  return client.delete(`/api/v1/subscriptions/${id}`)
}

// ── Invoices ──────────────────────────────────────────────────────────────────

export interface CreateInvoicePayload {
  packageId: string
  amount?: number
  dueDate: string
}

export interface BulkInvoiceResponse {
  createdCount: number
  skippedCount: number
  invoiceIds: string[]
}

export function createInvoiceApi(payload: CreateInvoicePayload) {
  return client.post('/api/v1/invoices', payload)
}

export function listAllInvoicesApi() {
  return client.get('/api/v1/invoices')
}

export function listMyInvoicesApi(limit = 50, offset = 0) {
  return client.get('/api/v1/invoices/me', { params: { limit, offset } })
}

export function markInvoicePaidApi(id: string) {
  return client.patch(`/api/v1/invoices/${id}/paid`)
}
