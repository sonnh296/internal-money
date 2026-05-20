import { test, expect } from './fixtures/users'
import { loginCustomer, goToNav, fillTransferForm, annotateTc, e2eEnv } from './helpers/ui'

test.describe('Transfer negative', () => {
  test.beforeEach(async ({ page }) => {
    await loginCustomer(page, e2eEnv.user1.email, e2eEnv.user1.password)
    await goToNav(page, 'Chuyển khoản')
  })

  test('TRF-E01: không chuyển cho chính mình', async ({ page, user1 }, testInfo) => {
    annotateTc(testInfo, 'TRF-E01')
    await page.locator('label:has-text("Số tài khoản người nhận") input').fill(user1.accountNumber)
    await page.waitForTimeout(1_200)
    await expect(page.getByText(/Không thể chuyển cho chính tài khoản nguồn/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Chuyển khoản' })).toBeDisabled()
  })

  test('TRF-E02: số tiền vượt khả dụng', async ({ page, user1, user2 }, testInfo) => {
    annotateTc(testInfo, 'TRF-E02')
    const huge = Math.max(user1.availableBalance + 1_000_000, 999_999_999)
    await fillTransferForm(page, user2.accountNumber, huge)
    await expect(page.getByText(/vượt quá số dư khả dụng/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Chuyển khoản' })).toBeDisabled()
  })

  test('TRF-E03: amount = 0', async ({ page, user2 }, testInfo) => {
    annotateTc(testInfo, 'TRF-E03')
    await fillTransferForm(page, user2.accountNumber, 0)
    await expect(page.getByRole('button', { name: 'Chuyển khoản' })).toBeDisabled()
  })

  test('ACC-E01: lookup STK không tồn tại', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'ACC-E01')
    await page.locator('label:has-text("Số tài khoản người nhận") input').fill('911111111')
    await page.waitForTimeout(1_500)
    await expect(page.getByText(/Không tìm thấy tài khoản/)).toBeVisible()
  })
})
