import { test, expect } from './fixtures/users'
import { loginCustomer, goToNav, annotateTc } from './helpers/ui'
import { e2eEnv } from './helpers/env'

test.describe('Account', () => {
  test.beforeEach(async ({ page }) => {
    await loginCustomer(page, e2eEnv.user1.email, e2eEnv.user1.password)
  })

  test('ACC-01: dashboard hiển thị tài khoản', async ({ page, user1 }, testInfo) => {
    annotateTc(testInfo, 'ACC-01')
    await expect(page.getByRole('heading', { level: 1 })).toContainText(/Tổng quan/i)
    const hasAccount =
      (await page.getByText(user1.accountNumber).count()) > 0 ||
      (await page.locator('.stat, .stat-tile, section').filter({ hasText: /số dư|balance/i }).count()) > 0
    expect(hasAccount).toBeTruthy()
  })

  test('ACC-03: trang chuyển khoản có STK nguồn', async ({ page, user1 }, testInfo) => {
    annotateTc(testInfo, 'ACC-03')
    await goToNav(page, 'Chuyển khoản')
    await expect(page.locator('section.card h2').filter({ hasText: 'Chuyển khoản nội bộ' })).toBeVisible()
    await expect(page.locator('.hint .kbd, .kbd').filter({ hasText: user1.accountNumber }).first()).toBeVisible()
  })

  test('PRF-01: trang hồ sơ', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'PRF-01')
    await goToNav(page, 'Hồ sơ')
    await expect(page.getByRole('heading', { level: 1 })).toContainText(/Hồ sơ/i)
    await expect(page.getByText(e2eEnv.user1.email)).toBeVisible()
  })
})
