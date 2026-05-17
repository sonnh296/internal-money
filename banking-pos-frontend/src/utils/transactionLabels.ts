import type { TransactionResponse } from '../types/api.types'

export function flowLabel(flow?: string): string {
  if (flow === 'IN') return 'Tiền vào'
  if (flow === 'OUT') return 'Tiền ra'
  return '—'
}

export function typeLabel(type?: string): string {
  const t = (type ?? '').toUpperCase()
  if (t === 'CREDIT' || t.includes('TRANSFER_CREDIT')) return 'Ghi có'
  if (t === 'DEBIT' || t.includes('TRANSFER_DEBIT')) return 'Ghi nợ'
  if (t.includes('HOLD_PLACED')) return 'Giữ tiền'
  if (t.includes('HOLD_RELEASED')) return 'Giải phóng'
  return type ?? '—'
}

export function counterpartyLine(t: TransactionResponse): string {
  const name = t.counterpartyName?.trim()
  const acct = t.counterpartyAccountNumber?.trim()
  if (name && acct) return `${name} · ${acct}`
  if (name) return name
  if (acct) return acct
  return '—'
}
