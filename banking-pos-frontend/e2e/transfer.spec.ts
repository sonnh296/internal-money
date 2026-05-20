import { test, expect } from './fixtures/users'
import { fetchBalance } from './helpers/api'
import {
  loginCustomer,
  goToNav,
  fillTransferForm,
  waitForToast,
  annotateTc,
  e2eEnv,
} from './helpers/ui'

test.describe.configure({ mode: 'serial' })

test.describe('Transfer happy path', () => {
  test('TRF-01: U1 → U2', async ({ page, user1, user2 }, testInfo) => {
    annotateTc(testInfo, 'TRF-01')
    const amount = 12_345
    const u2Before = await fetchBalance(user2.accessToken, user2.accountId)

    await loginCustomer(page, user1.email, e2eEnv.user1.password)
    await goToNav(page, 'Chuyển khoản')
    await fillTransferForm(page, user2.accountNumber, amount, 'E2E TRF-01')
    await expect(page.getByText(/Người nhận:/)).toBeVisible({ timeout: 10_000 })
    await page.getByRole('button', { name: 'Chuyển khoản' }).click()
    await waitForToast(page, 'thành công')

    await expect
      .poll(async () => {
        const b = await fetchBalance(user2.accessToken, user2.accountId)
        return b.balance
      }, { timeout: 10_000 })
      .toBeGreaterThanOrEqual(u2Before.balance + amount - 0.01)
  })

  test('TRF-02: U2 → U1', async ({ page, user1, user2 }, testInfo) => {
    annotateTc(testInfo, 'TRF-02')
    const amount = 5_432
    const u1Before = await fetchBalance(user1.accessToken, user1.accountId)

    await loginCustomer(page, user2.email, e2eEnv.user2.password)
    await goToNav(page, 'Chuyển khoản')
    await fillTransferForm(page, user1.accountNumber, amount, 'E2E TRF-02')
    await expect(page.getByText(/Người nhận:/)).toBeVisible({ timeout: 10_000 })
    await page.getByRole('button', { name: 'Chuyển khoản' }).click()
    await waitForToast(page, 'thành công')

    await expect
      .poll(async () => {
        const b = await fetchBalance(user1.accessToken, user1.accountId)
        return b.balance
      }, { timeout: 10_000 })
      .toBeGreaterThanOrEqual(u1Before.balance + amount - 0.01)
  })
})
