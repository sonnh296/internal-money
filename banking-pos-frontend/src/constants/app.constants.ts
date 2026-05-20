export const APP_ROUTES = {
  LANDING: '/',

  APP_ROOT: '/app',
  APP_LOGIN: '/app/login',
  APP_REGISTER: '/app/register',
  APP_DASHBOARD: '/app/dashboard',
  APP_ACCOUNTS: '/app/accounts',
  APP_ACCOUNT_DETAIL: '/app/accounts/:id',
  APP_TRANSFER: '/app/transfer',
  APP_BILLERS: '/app/billers',
  APP_BILLS: '/app/bills',
  APP_PROFILE: '/app/profile',

  ADMIN_ROOT: '/admin',
  ADMIN_LOGIN: '/admin/login',
  ADMIN_DASHBOARD: '/admin/dashboard',
  ADMIN_CUSTOMERS: '/admin/customers',
  ADMIN_SERVICES: '/admin/services',
  ADMIN_HEALTH: '/admin/health'
} as const

export interface NavItem {
  label: string
  to: string
  icon?: string
  hint?: string
}

export const APP_NAV_ITEMS: ReadonlyArray<NavItem> = [
  { label: 'Tổng quan', to: APP_ROUTES.APP_DASHBOARD, icon: '◎', hint: 'Dashboard' },
  { label: 'Dịch vụ', to: APP_ROUTES.APP_BILLERS, icon: '☷', hint: 'Services' },
  { label: 'Hóa đơn của tôi', to: APP_ROUTES.APP_BILLS, icon: '⊟', hint: 'My Bills' },
  { label: 'Chuyển khoản', to: APP_ROUTES.APP_TRANSFER, icon: '↭', hint: 'Transfer' },
  { label: 'Hồ sơ', to: APP_ROUTES.APP_PROFILE, icon: '◉', hint: 'Profile' }
]

export const ADMIN_NAV_ITEMS: ReadonlyArray<NavItem> = [
  { label: 'Dashboard', to: APP_ROUTES.ADMIN_DASHBOARD, icon: '◫' },
  { label: 'Khách hàng & Tài khoản', to: APP_ROUTES.ADMIN_CUSTOMERS, icon: '☰' },
  { label: 'Dịch vụ & Hóa đơn', to: APP_ROUTES.ADMIN_SERVICES, icon: '☷' },
  { label: 'System Health', to: APP_ROUTES.ADMIN_HEALTH, icon: '♥' }
]

/** Default ISO 4217 currency for new accounts, payments, and display fallbacks. */
export const DEFAULT_CURRENCY = 'VND' as const

export const ACCOUNT_ENUMS = {
  type: ['CHEQUING', 'SAVINGS', 'CREDIT', 'LOAN'],
  subType: ['PERSONAL', 'BUSINESS'],
  status: ['ACTIVE', 'FROZEN', 'CLOSED']
} as const

export const KYC_STATUSES = ['PENDING', 'VERIFIED', 'REJECTED'] as const

export const ADMIN_SCOPE_HINTS = [
  'admin:users.write',
  'admin:accounts',
  'admin:accounts.read',
  'admin:accounts.write'
] as const
