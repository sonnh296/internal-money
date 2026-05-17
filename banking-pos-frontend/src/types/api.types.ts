import type { AxiosHeaders, AxiosResponseHeaders, RawAxiosResponseHeaders } from 'axios'

export type ServiceName = 'auth' | 'customer' | 'account' | 'payment' | 'biller'

export type PortalKind = 'admin' | 'user'

export interface AuthProfile {
  email: string
  customerId: string
  scopes: string[]
}

export interface StoredAuthPayload {
  accessToken: string
  refreshToken: string
  expiresIn: number
  issuedAt: number
  profile: AuthProfile
}

export interface TokenResponseDto {
  access_token: string
  refresh_token: string
  expires_in: number
  token_type: string
}

export interface LastResultState {
  title: string
  ok: boolean
  status: string
  startedAt: number
  durationMs: number
  headers: RawAxiosResponseHeaders | AxiosResponseHeaders | AxiosHeaders | Record<string, string>
  body: string
}

export interface CustomerResponse {
  id?: number | string
  firstName: string
  lastName: string
  email: string
  phone: string
  address: string
  externalId: string
  active?: boolean
  kycStatus: string
  createdAt?: string
  updatedAt?: string
  version: number
}

export interface AccountResponse {
  id: string
  customerId: string
  accountNumber?: string
  maskedAccountNumber?: string
  accountType: string
  accountSubType: string
  status: string
  currency: string
  nickname?: string
  displayName?: string
  balance: number
  version: number
  /** Số dư khả dụng (sau khi trừ hold). */
  availableBalance?: number
  /** Tổng tiền đang hold. */
  totalHolds?: number
}

export interface AccountLookupResponse {
  accountNumber: string
  displayName: string
  currency: string
  status: string
}

export interface AccountBalanceResponse {
  balance: number
  totalHolds: number
  available: number
}

export interface HoldResponse {
  holdId: string
  amount: number
  status: string
  createdAt?: string
  releaseAt?: string | null
}

export interface TransactionResponse {
  id: string
  status?: string
  type?: string
  amount: number
  reason?: string
  balanceAfter?: number
  flowDirection?: 'IN' | 'OUT' | string
  counterpartyName?: string
  counterpartyAccountNumber?: string
  createdAt?: string
}

export interface BillerResponse {
  id: string
  name: string
  referenceNumber: string
  category: string
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  limit: number
  offset: number
}

export interface PaymentAcceptedResponse {
  paymentId: string
  state: string
  statusUrl?: string
}

export interface InternalTransferResponse {
  transferId: string
  fromAccountId: string
  toAccountId: string
  amount: number
  currency: string
  fromBalanceAfter: number
  toBalanceAfter: number
  occurredAt: string
}

export interface RewardPointsResponse {
  customerId: string
  points: number
  source: string
  inSync: boolean
}
