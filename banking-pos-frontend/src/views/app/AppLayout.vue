<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { APP_NAV_ITEMS, APP_ROUTES } from '../../constants/app.constants'
import { useUserAuthStore } from '../../stores/userAuth.store'

const route = useRoute()
const router = useRouter()
const auth = useUserAuthStore()

function applyTheme() {
  document.body.classList.remove('theme-admin', 'theme-landing')
  document.body.classList.add('theme-app')
}
onMounted(applyTheme)
onBeforeUnmount(() => document.body.classList.remove('theme-app'))

const title = computed(() => (route.meta?.title as string) ?? 'MockBank')

async function doLogout() {
  await auth.logout()
  await router.push(APP_ROUTES.APP_LOGIN)
}
</script>

<template>
  <div class="portal theme-app">
    <aside class="portal-sidebar">
      <RouterLink :to="APP_ROUTES.APP_DASHBOARD" class="portal-brand">
        <span class="dot"></span>
        <span>MockBank</span>
      </RouterLink>

      <nav class="portal-nav" aria-label="App nav">
        <span class="nav-section">Tài chính cá nhân</span>
        <RouterLink v-for="item in APP_NAV_ITEMS" :key="item.to" :to="item.to">
          <span class="icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div style="margin-top:auto; display:grid; gap:8px;">
        <div class="muted" style="font-size:11px;">Xin chào</div>
        <div style="font-size:13px; color: var(--text-strong); word-break: break-all;">
          {{ auth.profile.email || '—' }}
        </div>
        <div class="muted" style="font-size:11px;">
          customerId: <span class="kbd">{{ auth.profile.customerId || '—' }}</span>
        </div>
        <button class="ghost small" @click="doLogout">Đăng xuất</button>
        <RouterLink :to="APP_ROUTES.LANDING" class="muted" style="font-size:11px;">← Trang chọn cổng</RouterLink>
      </div>
    </aside>

    <main class="portal-main">
      <header class="portal-header">
        <div>
          <h1>{{ title }}</h1>
          <p class="sub">Ứng dụng ngân hàng cho khách hàng</p>
        </div>
        <span class="session-chip">
          <span class="dot" :class="{ off: !auth.isAuthenticated }"></span>
          <span>{{ auth.isAuthenticated ? 'Đang đăng nhập' : 'Khách' }}</span>
        </span>
      </header>

      <div class="portal-content">
        <RouterView />
      </div>
    </main>
  </div>
</template>
