import axios from 'axios'

export function httpStatus(error: unknown): number | null {
  if (axios.isAxiosError(error)) {
    return error.response?.status ?? null
  }
  return null
}

export function isNotFound(error: unknown): boolean {
  return httpStatus(error) === 404
}

const VI_ERROR_CODES: Record<string, string> = {
  BILLER_INACTIVE: 'Dịch vụ thanh toán không khả dụng.',
  INSUFFICIENT_FUNDS: 'Không đủ số dư khả dụng.',
  INTERNAL_ERROR: 'Đã xảy ra lỗi hệ thống.',
  VALIDATION_ERROR: 'Dữ liệu không hợp lệ.',
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
  INVALID_JWT: 'Phiên đăng nhập đã hết hạn.',
  NOT_FOUND: 'Không tìm thấy dữ liệu.',
  CONFLICT: 'Dữ liệu đã thay đổi, vui lòng tải lại.'
}

function preferVietnamese(msg: string, code?: string): string {
  if (code && VI_ERROR_CODES[code]) return VI_ERROR_CODES[code]
  if (/[\u00C0-\u1EF9]/.test(msg)) return msg
  if (msg.includes('exceeds available balance')) return 'Không đủ số dư khả dụng.'
  if (msg === 'Something went wrong') return 'Đã xảy ra lỗi hệ thống.'
  return msg
}

export function errorDetail(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data
    if (data && typeof data === 'object') {
      const envelope = data as Record<string, unknown>
      const nested = envelope.error
      if (nested && typeof nested === 'object') {
        const detail = nested as Record<string, unknown>
        const code = typeof detail.code === 'string' ? detail.code : undefined
        const msg = detail.message ?? detail.details
        if (typeof msg === 'string' && msg) return preferVietnamese(msg, code)
      }
      if (typeof envelope.message === 'string') return envelope.message
    }
    if (typeof data === 'string') return data
    return error.message
  }
  if (error instanceof Error) return error.message
  return String(error)
}
