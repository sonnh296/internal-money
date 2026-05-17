import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { APP_ROUTES } from '../constants/app.constants'
import { useAdminAuthStore } from '../stores/adminAuth.store'
import { useUserAuthStore } from '../stores/userAuth.store'
import { useNotifyStore } from '../stores/notify.store'

import LandingView from '../views/LandingView.vue'

import AdminLayout from '../views/admin/AdminLayout.vue'
import AdminLoginView from '../views/admin/AdminLoginView.vue'
import AdminDashboardView from '../views/admin/AdminDashboardView.vue'
import AdminManagersView from '../views/admin/AdminManagersView.vue'
import AdminCustomersView from '../views/admin/AdminCustomersView.vue'
import AdminServicesView from '../views/admin/AdminServicesView.vue'
import AdminHealthView from '../views/admin/AdminHealthView.vue'
import AdminPosBenchmarkView from '../views/admin/AdminPosBenchmarkView.vue'

import AppLayout from '../views/app/AppLayout.vue'
import AppLoginView from '../views/app/AppLoginView.vue'
import AppRegisterView from '../views/app/AppRegisterView.vue'
import AppDashboardView from '../views/app/AppDashboardView.vue'
import AppAccountDetailView from '../views/app/AppAccountDetailView.vue'
import AppTransferView from '../views/app/AppTransferView.vue'
import AppRewardsView from '../views/app/AppRewardsView.vue'
import AppBillersView from '../views/app/AppBillersView.vue'
import AppBillsView from '../views/app/AppBillsView.vue'
import AppProfileView from '../views/app/AppProfileView.vue'

const routes: RouteRecordRaw[] = [
  {
    path: APP_ROUTES.LANDING,
    component: LandingView,
    meta: { portal: null }
  },

  {
    path: APP_ROUTES.ADMIN_LOGIN,
    component: AdminLoginView,
    meta: { portal: 'admin' }
  },
  {
    path: APP_ROUTES.ADMIN_ROOT,
    component: AdminLayout,
    meta: { portal: 'admin', requiresAdmin: true },
    children: [
      { path: '', redirect: APP_ROUTES.ADMIN_DASHBOARD },
      { path: 'dashboard', component: AdminDashboardView, meta: { portal: 'admin', requiresAdmin: true, title: 'Dashboard' } },
      { path: 'managers', component: AdminManagersView, meta: { portal: 'admin', requiresAdmin: true, title: 'Quản lý Managers' } },
      { path: 'customers', component: AdminCustomersView, meta: { portal: 'admin', requiresAdmin: true, title: 'Khách hàng & Tài khoản' } },
      { path: 'services', component: AdminServicesView, meta: { portal: 'admin', requiresAdmin: true, title: 'Dịch vụ & Hóa đơn' } },
      { path: 'pos-benchmark', component: AdminPosBenchmarkView, meta: { portal: 'admin', requiresAdmin: true, title: 'POS Benchmark' } },
      { path: 'health', component: AdminHealthView, meta: { portal: 'admin', requiresAdmin: true, title: 'System Health' } }
    ]
  },

  {
    path: APP_ROUTES.APP_LOGIN,
    component: AppLoginView,
    meta: { portal: 'user' }
  },
  {
    path: APP_ROUTES.APP_REGISTER,
    component: AppRegisterView,
    meta: { portal: 'user' }
  },
  {
    path: APP_ROUTES.APP_ROOT,
    component: AppLayout,
    meta: { portal: 'user', requiresUser: true },
    children: [
      { path: '', redirect: APP_ROUTES.APP_DASHBOARD },
      { path: 'dashboard', component: AppDashboardView, meta: { portal: 'user', requiresUser: true, title: 'Tổng quan' } },
      { path: 'accounts', redirect: APP_ROUTES.APP_PROFILE },
      { path: 'accounts/:id', component: AppAccountDetailView, meta: { portal: 'user', requiresUser: true, title: 'Chi tiết tài khoản' } },
      { path: 'transfer', component: AppTransferView, meta: { portal: 'user', requiresUser: true, title: 'Chuyển khoản nội bộ' } },
      { path: 'rewards', component: AppRewardsView, meta: { portal: 'user', requiresUser: true, title: 'Điểm thưởng' } },
      { path: 'billers', component: AppBillersView, meta: { portal: 'user', requiresUser: true, title: 'Dịch vụ' } },
      { path: 'bills', component: AppBillsView, meta: { portal: 'user', requiresUser: true, title: 'Hóa đơn của tôi' } },
      { path: 'profile', component: AppProfileView, meta: { portal: 'user', requiresUser: true, title: 'Hồ sơ của tôi' } }
    ]
  },

  { path: '/:catchAll(.*)', redirect: APP_ROUTES.LANDING }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  if (to.meta.requiresAdmin) {
    const adminAuth = useAdminAuthStore()
    if (!adminAuth.isAuthenticated) {
      return { path: APP_ROUTES.ADMIN_LOGIN, query: { from: to.fullPath } }
    }
    if (!adminAuth.isAdmin) {
      const notify = useNotifyStore()
      notify.push('error', 'Không đủ quyền', 'Tài khoản đăng nhập không có quyền admin.')
      return { path: APP_ROUTES.ADMIN_LOGIN }
    }
  }
  if (to.meta.requiresUser) {
    const userAuth = useUserAuthStore()
    if (!userAuth.isAuthenticated) {
      return { path: APP_ROUTES.APP_LOGIN, query: { from: to.fullPath } }
    }
  }
  return true
})

export default router
