import { test, expect } from './fixtures/users'
import { getHealth } from './helpers/api'
import { e2eEnv } from './helpers/env'
import { annotateTc } from './helpers/ui'

test.describe('API smoke', () => {
  test('SM-01: account health', async ({}, testInfo) => {
    annotateTc(testInfo, 'SM-01')
    const status = await getHealth(`${e2eEnv.accountURL}/api/v1/health`)
    expect(status).toBe(200)
  })

  test('SM-02: payment health', async ({}, testInfo) => {
    annotateTc(testInfo, 'SM-02')
    const status = await getHealth(`${e2eEnv.paymentURL}/api/v1/health`)
    if (status !== 200) {
      testInfo.annotations.push({ type: 'skip-reason', description: `PaymentOrchestrator not running (${status})` })
      test.skip()
    }
    expect(status).toBe(200)
  })

  test('SM-03: auth public', async ({}, testInfo) => {
    annotateTc(testInfo, 'SM-03')
    const status = await getHealth(`${e2eEnv.authURL}/api/v1/test/public`)
    expect(status).toBe(200)
  })

  test('ACC-02: accounts/me U1', async ({ user1 }, testInfo) => {
    annotateTc(testInfo, 'ACC-02')
    const res = await fetch(`${e2eEnv.accountURL}/api/v1/accounts/me`, {
      headers: { Authorization: `Bearer ${user1.accessToken}` },
    })
    expect(res.status).toBe(200)
    const body = (await res.json()) as { accountNumber?: string; balance: number }
    expect(body.accountNumber).toBeTruthy()
    expect(typeof body.balance).toBe('number')
  })

  test('API-E01: transfer without token', async ({ user1, user2 }, testInfo) => {
    annotateTc(testInfo, 'API-E01')
    const res = await fetch(`${e2eEnv.accountURL}/api/v1/accounts/transfer`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': `e2e-unauth-${Date.now()}`,
      },
      body: JSON.stringify({
        fromAccountId: user1.accountId,
        toAccountNumber: user2.accountNumber,
        amount: 1,
        reason: 'unauth test',
      }),
    })
    expect(res.status).toBe(401)
  })
})
