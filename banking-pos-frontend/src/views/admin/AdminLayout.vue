<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ADMIN_NAV_ITEMS, APP_ROUTES } from '../../constants/app.constants'
import { useAdminAuthStore } from '../../stores/adminAuth.store'

const route = useRoute()
const router = useRouter()
const auth = useAdminAuthStore()

function applyTheme() {
  document.body.classList.remove('theme-app', 'theme-landing')
  document.body.classList.add('theme-admin')
}
onMounted(applyTheme)
onBeforeUnmount(() => document.body.classList.remove('theme-admin'))

const title = computed(() => (route.meta?.title as string) ?? 'Admin Console')
const expiresText = computed(() => {
  if (!auth.expiresAt) return '—'
  const seconds = Math.max(0, Math.round((auth.expiresAt - Date.now()) / 1000))
  return `${seconds}s`
})
const visibleNavItems = computed(() => ADMIN_NAV_ITEMS)

async function doLogout() {
  await auth.logout()
  await router.push(APP_ROUTES.ADMIN_LOGIN)
}
</script>

<template>
  <div class="portal theme-admin">
    <aside class="portal-sidebar">
      <RouterLink :to="APP_ROUTES.ADMIN_DASHBOARD" class="portal-brand">
        <span class="dot"></span>
        <span>MockBank · Admin</span>
      </RouterLink>

      <nav class="portal-nav" aria-label="Admin nav">
        <span class="nav-section">Quản trị</span>
        <RouterLink v-for="item in visibleNavItems" :key="item.to" :to="item.to">
          <span class="icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div style="margin-top:auto; display:grid; gap:8px;">
        <div class="muted" style="font-size:11px;">Đăng nhập với</div>
        <div style="font-size:12px; color: var(--text-strong); word-break: break-all;">
          {{ auth.profile.email || '—' }}
        </div>
        <div class="muted" style="font-size:11px;">
          role: <span class="kbd">{{ auth.role || '—' }}</span>
        </div>
        <button class="ghost small" @click="doLogout">Đăng xuất</button>
        <RouterLink :to="APP_ROUTES.LANDING" class="muted" style="font-size:11px;">← Trang chọn cổng</RouterLink>
      </div>
    </aside>

    <main class="portal-main">
      <header class="portal-header">
        <div>
          <h1>{{ title }}</h1>
          <p class="sub">Admin console</p>
        </div>
        <div class="inline-row">
          <span class="session-chip">
            <span class="dot" :class="{ off: !auth.isAuthenticated }"></span>
            <span>{{ auth.isSuperAdmin ? 'SUPER_ADMIN' : auth.isAdmin ? 'admin' : 'auth' }}</span>
            <span class="muted">expires in {{ expiresText }}</span>
          </span>
        </div>
      </header>

      <div class="portal-content">
        <RouterView />
      </div>
    </main>
  </div>
</template>
