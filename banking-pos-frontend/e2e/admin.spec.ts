import { test, expect } from '@playwright/test'
import { loginAdmin, annotateTc, e2eEnv } from './helpers/ui'
import { readUsersCache } from './helpers/env'

test.describe('Admin', () => {
  test.beforeEach(async ({ page }) => {
    const cache = readUsersCache()
    test.skip(!cache?.admin, 'Admin login unavailable in global-setup')
    await loginAdmin(page, e2eEnv.admin.email, e2eEnv.admin.password)
  })

  test('ADM-01: customers page', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'ADM-01')
    await page.goto('/admin/customers')
    await expect(page.getByRole('heading', { level: 1 })).toContainText(/Khách hàng/i)
    await expect(page.getByPlaceholder(/Tìm theo externalId/i)).toBeVisible()
  })

  test('ADM-02: health page', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'ADM-02')
    await page.goto('/admin/health')
    await expect(page.getByRole('heading', { level: 1 })).toContainText(/Health|hệ thống/i)
  })
})
