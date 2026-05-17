<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { APP_ROUTES } from '../../constants/app.constants'
import { createCustomerApi } from '../../api/customer.api'
import { useApiAction } from '../../composables/useApiAction'

const router = useRouter()
const { run, running } = useApiAction()

function applyTheme() {
  document.body.classList.remove('theme-admin', 'theme-landing')
  document.body.classList.add('theme-app')
}
onMounted(applyTheme)
onBeforeUnmount(() => document.body.classList.remove('theme-app'))

const form = reactive({
  firstName: '',
  lastName: '',
  email: '',
  phone: '+84',
  address: '',
  externalId: `cust-${Date.now().toString(36)}`
})

async function submit() {
  await run('Customer self-register', () => createCustomerApi(form), {
    successToast: 'Tạo hồ sơ thành công — chờ admin xác minh KYC để được cấp tài khoản đăng nhập.'
  })
  await router.push(APP_ROUTES.APP_LOGIN)
}
</script>

<template>
  <main class="auth-screen theme-app">
    <section class="auth-card" style="max-width: 540px;">
      <h1>Đăng ký khách hàng mới</h1>
      <p class="lead">
        Tạo hồ sơ khách hàng. Sau khi gửi, đội ngân hàng sẽ xác minh KYC. Khi KYC chuyển sang <span class="kbd">VERIFIED</span>,
        tài khoản đăng nhập sẽ được cấp tự động.
      </p>

      <form class="form-grid" @submit.prevent="submit">
        <label>Họ <input v-model="form.firstName" required /></label>
        <label>Tên <input v-model="form.lastName" required /></label>
        <label>Email <input v-model="form.email" type="email" required /></label>
        <label>Số điện thoại (E.164) <input v-model="form.phone" placeholder="+84..." required /></label>
        <label style="grid-column: 1 / -1;">Địa chỉ <input v-model="form.address" required /></label>
        <label>External ID <input v-model="form.externalId" required /></label>
      </form>

      <div class="actions">
        <button :disabled="running" @click="submit">{{ running ? 'Đang gửi…' : 'Gửi đăng ký' }}</button>
        <RouterLink :to="APP_ROUTES.APP_LOGIN" class="muted" style="align-self:center;">← Quay lại đăng nhập</RouterLink>
      </div>

    </section>
  </main>
</template>
