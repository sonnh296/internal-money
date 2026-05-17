<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { APP_ROUTES } from '../../constants/app.constants'
import { useAdminAuthStore } from '../../stores/adminAuth.store'
import { useApiAction } from '../../composables/useApiAction'
import PasswordInput from '../../components/PasswordInput.vue'

const router = useRouter()
const auth = useAdminAuthStore()
const { run, running } = useApiAction()

function applyTheme() {
  document.body.classList.remove('theme-app', 'theme-landing')
  document.body.classList.add('theme-admin')
}
onMounted(applyTheme)
onBeforeUnmount(() => document.body.classList.remove('theme-admin'))

const form = reactive({
  email: '',
  password: ''
})
const rejected = ref('')

async function submit() {
  rejected.value = ''
  await run('Admin login', () => auth.login(form), { successToast: 'Đăng nhập admin thành công' })
  if (!auth.isAdmin) {
    rejected.value = 'Tài khoản hợp lệ nhưng không có quyền admin. Vui lòng dùng tài khoản admin.'
    await auth.logout()
    return
  }
  await router.push(APP_ROUTES.ADMIN_DASHBOARD)
}
</script>

<template>
  <main class="auth-screen theme-admin">
    <section class="auth-card">
      <h1>Đăng nhập Admin</h1>
      <p class="lead">Cổng quản trị hệ thống.</p>

      <form class="form-grid single" @submit.prevent="submit">
        <label>Email <input v-model="form.email" autocomplete="email" /></label>
        <label>Password
          <PasswordInput v-model="form.password" autocomplete="current-password" />
        </label>
        <div v-if="rejected" class="pill danger" style="white-space:normal;">{{ rejected }}</div>
        <div class="actions">
          <button :disabled="running">{{ running ? 'Đang đăng nhập…' : 'Đăng nhập admin' }}</button>
        </div>
      </form>

      <div class="alt">
        <RouterLink :to="APP_ROUTES.LANDING" class="muted">← Trang chọn cổng</RouterLink>
        <RouterLink :to="APP_ROUTES.APP_LOGIN" class="muted">Tôi là khách hàng →</RouterLink>
      </div>
    </section>
  </main>
</template>
