import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function loadDotEnv(): void {
  const envPath = path.resolve(__dirname, '../../.env.e2e')
  if (!fs.existsSync(envPath)) return
  const lines = fs.readFileSync(envPath, 'utf8').split('\n')
  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const eq = trimmed.indexOf('=')
    if (eq <= 0) continue
    const key = trimmed.slice(0, eq).trim()
    const value = trimmed.slice(eq + 1).trim()
    if (process.env[key] === undefined) {
      process.env[key] = value
    }
  }
}

loadDotEnv()

export const e2eEnv = {
  baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
  gatewayURL: process.env.E2E_GATEWAY_URL ?? 'http://localhost:8080',
  authURL: process.env.E2E_AUTH_URL ?? 'http://localhost:8094',
  accountURL: process.env.E2E_ACCOUNT_URL ?? 'http://localhost:8084',
  paymentURL: process.env.E2E_PAYMENT_URL ?? 'http://localhost:8086',
  user1: {
    email: process.env.E2E_USER1_EMAIL ?? 'son29062002@gmail.com',
    password: process.env.E2E_USER1_PASSWORD ?? '',
  },
  user2: {
    email: process.env.E2E_USER2_EMAIL ?? 'sonnh296@gmail.com',
    password: process.env.E2E_USER2_PASSWORD ?? '',
  },
  admin: {
    email: process.env.E2E_ADMIN_EMAIL ?? 'admin.demo@mockbank.local',
    password: process.env.E2E_ADMIN_PASSWORD ?? 'Admin@12345',
  },
  transferAmount: Number(process.env.E2E_TRANSFER_AMOUNT ?? '10000'),
}

export const CACHE_DIR = path.resolve(__dirname, '../.cache')
export const USERS_CACHE_FILE = path.join(CACHE_DIR, 'users.json')

export interface CachedUser {
  email: string
  accessToken: string
  refreshToken: string
  customerId: string
  accountId: string
  accountNumber: string
  balance: number
  availableBalance: number
  currency: string
}

export interface UsersCache {
  user1: CachedUser | null
  user2: CachedUser | null
  admin: { email: string; accessToken: string } | null
  fetchedAt: string
}

export function readUsersCache(): UsersCache | null {
  if (!fs.existsSync(USERS_CACHE_FILE)) return null
  return JSON.parse(fs.readFileSync(USERS_CACHE_FILE, 'utf8')) as UsersCache
}

export function writeUsersCache(data: UsersCache): void {
  fs.mkdirSync(CACHE_DIR, { recursive: true })
  fs.writeFileSync(USERS_CACHE_FILE, JSON.stringify(data, null, 2))
}

export function requirePassword(label: string, value: string): void {
  if (!value) {
    throw new Error(
      `${label} chưa cấu hình. Tạo banking-pos-frontend/.env.e2e từ .env.e2e.example và điền mật khẩu.`
    )
  }
}
