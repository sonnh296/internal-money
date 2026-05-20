import { login, fetchMyAccount, fetchBalance } from './helpers/api'
import {
  e2eEnv,
  requirePassword,
  writeUsersCache,
  type CachedUser,
  type UsersCache,
} from './helpers/env'

async function buildUser(
  email: string,
  password: string
): Promise<CachedUser> {
  const tokens = await login(email, password)
  const account = await fetchMyAccount(tokens.access_token)
  if (!account.accountNumber) {
    throw new Error(`User ${email} has no accountNumber — complete KYC and provision account.`)
  }
  const bal = await fetchBalance(tokens.access_token, account.id)
  return {
    email,
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    customerId: account.customerId,
    accountId: account.id,
    accountNumber: account.accountNumber,
    balance: bal.balance,
    availableBalance: bal.availableBalance,
    currency: account.currency,
  }
}

export default async function globalSetup(): Promise<void> {
  requirePassword('E2E_USER1_PASSWORD', e2eEnv.user1.password)
  requirePassword('E2E_USER2_PASSWORD', e2eEnv.user2.password)

  const cache: UsersCache = {
    user1: null,
    user2: null,
    admin: null,
    fetchedAt: new Date().toISOString(),
  }

  try {
    cache.user1 = await buildUser(e2eEnv.user1.email, e2eEnv.user1.password)
    cache.user2 = await buildUser(e2eEnv.user2.email, e2eEnv.user2.password)
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    throw new Error(
      `E2E global-setup failed. Ensure AuthUser, AccountService, gateway :8080 are running.\n${msg}`
    )
  }

  try {
    const adminTokens = await login(e2eEnv.admin.email, e2eEnv.admin.password)
    cache.admin = { email: e2eEnv.admin.email, accessToken: adminTokens.access_token }
  } catch {
    console.warn('[e2e] Admin login skipped — admin tests may fail.')
  }

  writeUsersCache(cache)
  console.log('[e2e] Cached users:', {
    user1: cache.user1?.accountNumber,
    user2: cache.user2?.accountNumber,
    admin: cache.admin?.email ?? 'n/a',
  })
}
