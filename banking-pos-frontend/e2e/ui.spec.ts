import { test, expect } from '@playwright/test'
import { loginCustomer, goToNav, annotateTc, e2eEnv } from './helpers/ui'

test.describe('UI navigation', () => {
  test('UI-01: landing', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'UI-01')
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /MockBank/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /cổng khách hàng/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /cổng quản trị/i })).toBeVisible()
  })

  test('UI-02: sidebar navigation', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'UI-02')
    await loginCustomer(page, e2eEnv.user1.email, e2eEnv.user1.password)
    const routes: { label: string; path: RegExp }[] = [
      { label: 'Tổng quan', path: /\/app\/dashboard/ },
      { label: 'Dịch vụ', path: /\/app\/billers/ },
      { label: 'Hóa đơn của tôi', path: /\/app\/bills/ },
      { label: 'Chuyển khoản', path: /\/app\/transfer/ },
      { label: 'Hồ sơ', path: /\/app\/profile/ },
    ]
    for (const r of routes) {
      await goToNav(page, r.label)
      await expect(page).toHaveURL(r.path)
    }
  })
})
