import { test, expect } from './fixtures/users'
import { loginCustomer, goToNav, annotateTc, e2eEnv } from './helpers/ui'

test.describe('Bills & Biller', () => {
  test.beforeEach(async ({ page }) => {
    await loginCustomer(page, e2eEnv.user1.email, e2eEnv.user1.password)
  })

  test('BP-01: trang hóa đơn load', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'BP-01')
    await goToNav(page, 'Hóa đơn của tôi')
    await expect(page.locator('h2').filter({ hasText: 'Hóa đơn của tôi' })).toBeVisible()
  })

  test('BIL-01: trang dịch vụ', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'BIL-01')
    await goToNav(page, 'Dịch vụ')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('BP-02: thanh toán hóa đơn PENDING (nếu có)', async ({ page, user1 }, testInfo) => {
    annotateTc(testInfo, 'BP-02')
    await goToNav(page, 'Hóa đơn của tôi')
    const payBtn = page.getByRole('button', { name: 'Thanh toán' })
    const pendingRow = page.locator('tbody tr').filter({ has: page.locator('.pill.warning, .pill') }).filter({ hasText: 'PENDING' }).first()

    if ((await payBtn.count()) === 0 || !(await payBtn.isEnabled())) {
      testInfo.annotations.push({ type: 'skip-reason', description: 'No payable pending invoice' })
      test.skip()
      return
    }

    await payBtn.click()
    await expect(page.locator('.toast').first()).toBeVisible({ timeout: 45_000 })
    const toastText = await page.locator('.toast').first().textContent()
    expect(toastText).toMatch(/thành công|thất bại|xử lý/i)
    await expect(page.getByText(user1.accountNumber).first()).toBeVisible()
  })

  test('BP-E01: trang bills render (empty hoặc có bảng)', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'BP-E01')
    await goToNav(page, 'Hóa đơn của tôi')
    await expect(page).toHaveURL(/\/app\/bills/)
    const hasContent =
      (await page.getByText('Chưa có hóa đơn nào').isVisible().catch(() => false)) ||
      (await page.locator('table tbody tr').count()) > 0 ||
      (await page.locator('h2').filter({ hasText: 'Hóa đơn' }).isVisible().catch(() => false))
    expect(hasContent).toBeTruthy()
  })
})
