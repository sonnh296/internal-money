<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { APP_ROUTES } from '../../constants/app.constants'
import { useUserAuthStore } from '../../stores/userAuth.store'
import { useApiAction } from '../../composables/useApiAction'
import PasswordInput from '../../components/PasswordInput.vue'

const router = useRouter()
const auth = useUserAuthStore()
const { run, running } = useApiAction()

function applyTheme() {
  document.body.classList.remove('theme-admin', 'theme-landing')
  document.body.classList.add('theme-app')
}
onMounted(applyTheme)
onBeforeUnmount(() => document.body.classList.remove('theme-app'))

const form = reactive({
  email: '',
  password: ''
})

async function submit() {
  await run('Customer login', () => auth.login(form), { successToast: 'Đăng nhập thành công' })
  await router.push(APP_ROUTES.APP_DASHBOARD)
}
</script>

<template>
  <main class="auth-screen theme-app">
    <section class="auth-card">
      <h1>Đăng nhập</h1>
      <p class="lead">Chào mừng bạn quay lại với MockBank.</p>

      <form class="form-grid single" @submit.prevent="submit">
        <label>Email <input v-model="form.email" autocomplete="email" /></label>
        <label>Mật khẩu
          <PasswordInput v-model="form.password" autocomplete="current-password" />
        </label>
        <div class="actions">
          <button :disabled="running">{{ running ? 'Đang đăng nhập…' : 'Đăng nhập' }}</button>
        </div>
      </form>

      <div class="alt">
        <RouterLink :to="APP_ROUTES.APP_REGISTER">Đăng ký tài khoản mới</RouterLink>
        <RouterLink :to="APP_ROUTES.LANDING" class="muted">← Chọn cổng khác</RouterLink>
      </div>
    </section>
  </main>
</template>
