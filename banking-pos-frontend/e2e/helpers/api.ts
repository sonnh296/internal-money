import { e2eEnv } from './env'

export interface TokenResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  token_type: string
}

export interface AccountMe {
  id: string
  customerId: string
  accountNumber?: string
  balance: number
  availableBalance?: number
  currency: string
  status: string
}

async function parseJson<T>(res: Response): Promise<T> {
  const text = await res.text()
  if (!text) return {} as T
  return JSON.parse(text) as T
}

export async function login(email: string, password: string): Promise<TokenResponse> {
  const loginUrl = process.env.E2E_USE_GATEWAY === 'true'
    ? `${e2eEnv.gatewayURL}/auth-api/api/v1/auth/login`
    : `${e2eEnv.authURL}/api/v1/auth/login`
  const res = await fetch(loginUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(`Login failed for ${email}: ${res.status} ${body}`)
  }
  return parseJson<TokenResponse>(res)
}

export async function fetchMyAccount(accessToken: string): Promise<AccountMe> {
  const res = await fetch(`${e2eEnv.accountURL}/api/v1/accounts/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(`accounts/me failed: ${res.status} ${body}`)
  }
  return parseJson<AccountMe>(res)
}

export async function fetchBalance(
  accessToken: string,
  accountId: string
): Promise<{ balance: number; availableBalance: number }> {
  const res = await fetch(`${e2eEnv.accountURL}/api/v1/accounts/${accountId}/balance`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(`balance failed: ${res.status} ${body}`)
  }
  const data = await parseJson<{ balance: number; holds?: number; available?: number }>(res)
  const holds = Number(data.holds ?? 0)
  const balance = Number(data.balance ?? 0)
  const available = data.available != null ? Number(data.available) : balance - holds
  return { balance, availableBalance: available }
}

export async function getHealth(path: string): Promise<number> {
  try {
    const res = await fetch(path)
    return res.status
  } catch {
    return 0
  }
}
