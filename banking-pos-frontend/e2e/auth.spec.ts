import { test, expect } from '@playwright/test'
import { e2eEnv } from './helpers/env'
import { loginCustomer, loginAdmin, logoutCustomer, annotateTc } from './helpers/ui'

test.describe('Auth', () => {
  test('AUTH-01: đăng nhập U1', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'AUTH-01')
    await loginCustomer(page, e2eEnv.user1.email, e2eEnv.user1.password)
    await expect(page.locator('.portal-sidebar')).toContainText(e2eEnv.user1.email)
    await expect(page).toHaveURL(/\/app\/dashboard/)
  })

  test('AUTH-02: đăng nhập U2', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'AUTH-02')
    await loginCustomer(page, e2eEnv.user2.email, e2eEnv.user2.password)
    await expect(page).toHaveURL(/\/app\/dashboard/)
  })

  test('AUTH-03: đăng nhập admin', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'AUTH-03')
    await loginAdmin(page, e2eEnv.admin.email, e2eEnv.admin.password)
    await expect(page).toHaveURL(/\/admin\/dashboard/)
  })

  test('AUTH-E01: sai mật khẩu', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'AUTH-E01')
    await page.goto('/app/login')
    await page.locator('label:has-text("Email") input').fill(e2eEnv.user1.email)
    await page.locator('label:has-text("Mật khẩu") input').fill('wrong-password-xyz')
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(page).toHaveURL(/\/app\/login/)
    await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
  })

  test('AUTH-E02: route protected', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'AUTH-E02')
    await page.goto('/app/transfer')
    await expect(page).toHaveURL(/\/app\/login/)
  })

  test('AUTH-E03: customer không vào admin', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'AUTH-E03')
    await loginCustomer(page, e2eEnv.user1.email, e2eEnv.user1.password)
    await page.goto('/admin/customers')
    await expect(page).toHaveURL(/\/admin\/login/)
  })

  test('AUTH-04: đăng xuất', async ({ page }, testInfo) => {
    annotateTc(testInfo, 'AUTH-04')
    await loginCustomer(page, e2eEnv.user1.email, e2eEnv.user1.password)
    await logoutCustomer(page)
    await page.goto('/app/dashboard')
    await expect(page).toHaveURL(/\/app\/login/)
  })
})
