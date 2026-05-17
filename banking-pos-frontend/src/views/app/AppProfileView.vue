<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getCustomerApi, updateCustomerApi } from '../../api/customer.api'
import { getMyAccountApi } from '../../api/account.api'
import { changePasswordApi } from '../../api/auth.api'
import { useUserAuthStore } from '../../stores/userAuth.store'
import { useApiAction } from '../../composables/useApiAction'
import type { AccountResponse, CustomerResponse } from '../../types/api.types'
import EmptyState from '../../components/EmptyState.vue'
import PasswordInput from '../../components/PasswordInput.vue'

const auth = useUserAuthStore()
const { run, running } = useApiAction()

const profile = ref<CustomerResponse | null>(null)
const myAccount = ref<AccountResponse | null>(null)
const lookup = reactive({ externalId: '' })
const editForm = reactive({ fullName: '', email: '', phone: '' })
const pwForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const pwError = ref('')
const pwSuccess = ref(false)

const customerId = computed(() => lookup.externalId || auth.profile.customerId)

async function load() {
  if (!customerId.value) return
  const [profileResp, accountResp] = await Promise.all([
    run('Lấy hồ sơ', () => getCustomerApi(customerId.value), { silent: true }),
    run('Lấy tài khoản', () => getMyAccountApi(), { silent: true }).catch(() => ({ data: null }))
  ])
  profile.value = profileResp.data as CustomerResponse
  myAccount.value = accountResp.data as AccountResponse | null
  editForm.fullName = `${profile.value.firstName ?? ''} ${profile.value.lastName ?? ''}`.trim()
  editForm.email = profile.value.email ?? ''
  editForm.phone = profile.value.phone ?? ''
}

async function save() {
  if (!profile.value) return
  await run(
    'Cập nhật hồ sơ',
    () => updateCustomerApi(profile.value!.externalId, editForm, Number(profile.value!.version)),
    { successToast: 'Đã cập nhật hồ sơ' }
  )
  await load()
}

async function changePassword() {
  pwError.value = ''
  pwSuccess.value = false
  if (!pwForm.currentPassword || !pwForm.newPassword) {
    pwError.value = 'Vui lòng điền đầy đủ thông tin.'
    return
  }
  if (pwForm.newPassword !== pwForm.confirmPassword) {
    pwError.value = 'Mật khẩu mới không khớp.'
    return
  }
  if (pwForm.newPassword.length < 8) {
    pwError.value = 'Mật khẩu mới phải có ít nhất 8 ký tự.'
    return
  }
  const resp = await run(
    'Đổi mật khẩu',
    () => changePasswordApi({ currentPassword: pwForm.currentPassword, newPassword: pwForm.newPassword }),
    { successToast: 'Đã đổi mật khẩu thành công' }
  )
  auth.applyToken(resp.data as import('../../types/api.types').TokenResponseDto)
  pwSuccess.value = true
  pwForm.currentPassword = ''
  pwForm.newPassword = ''
  pwForm.confirmPassword = ''
}

onMounted(load)

function formatMoney(currency: string, value: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND' }).format(value)
}
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Hồ sơ của tôi</h2>
          <p class="lead">Xem và cập nhật thông tin cá nhân. Đổi mật khẩu đăng nhập ở phía dưới.</p>
        </div>
        <div class="inline-row">
          <label>External ID
            <input v-model="lookup.externalId" :placeholder="auth.profile.customerId || 'externalId'" />
          </label>
          <button class="secondary" :disabled="running" @click="load">Tải</button>
        </div>
      </div>
    </section>

    <section class="card" v-if="profile">
      <div class="row-between">
        <h2>Thông tin cá nhân</h2>
        <span class="pill" :class="{
          success: profile.kycStatus === 'VERIFIED',
          warning: profile.kycStatus === 'PENDING',
          danger: profile.kycStatus === 'REJECTED'
        }">KYC: {{ profile.kycStatus }}</span>
      </div>

      <div class="form-grid">
        <label>Họ tên <input v-model="editForm.fullName" /></label>
        <label>Email <input v-model="editForm.email" type="email" /></label>
        <label>Điện thoại <input v-model="editForm.phone" /></label>
        <label>Địa chỉ <input :value="profile.address" disabled /></label>
        <label>External ID <input :value="profile.externalId" disabled /></label>
        <label>Version <input :value="profile.version" disabled /></label>
      </div>
      <div class="actions">
        <button :disabled="running" @click="save">Lưu thay đổi</button>
      </div>
    </section>

    <section class="card" v-if="profile">
      <h2>Tài khoản ngân hàng</h2>
      <div v-if="myAccount" class="form-grid">
        <label>Số tài khoản
          <input :value="myAccount.accountNumber" readonly />
        </label>
        <label>Số dư
          <input :value="formatMoney(myAccount.currency, Number(myAccount.balance))" readonly />
        </label>
        <label>Trạng thái <input :value="myAccount.status" readonly /></label>
      </div>
      <p v-else class="lead">Hiện chưa có tài khoản khả dụng. Liên hệ ngân hàng sau khi KYC được duyệt.</p>
    </section>

    <!-- Change Password Section -->
    <section class="card">
      <h2>Đổi mật khẩu</h2>
      <p class="lead">
        Mật khẩu mới phải có ít nhất 8 ký tự. Nếu mật khẩu đang được thay đổi ở tab/thiết bị khác,
        bạn sẽ nhận được thông báo cần tải lại trang.
      </p>

      <div v-if="pwSuccess" class="alert success" style="margin-bottom:16px;">
        ✓ Đổi mật khẩu thành công. Phiên đăng nhập hiện tại đã được làm mới.
      </div>
      <div v-if="pwError" class="alert danger" style="margin-bottom:16px;">{{ pwError }}</div>

      <div class="form-grid">
        <label>Mật khẩu hiện tại
          <PasswordInput v-model="pwForm.currentPassword" placeholder="••••••••" autocomplete="current-password" />
        </label>
        <label>Mật khẩu mới
          <PasswordInput v-model="pwForm.newPassword" placeholder="••••••••" autocomplete="new-password" />
        </label>
        <label>Xác nhận mật khẩu mới
          <PasswordInput v-model="pwForm.confirmPassword" placeholder="••••••••" autocomplete="new-password" />
        </label>
      </div>
      <div class="actions">
        <button :disabled="running || !pwForm.currentPassword || !pwForm.newPassword" @click="changePassword">
          Đổi mật khẩu
        </button>
      </div>
    </section>

    <EmptyState
      v-if="!profile"
      icon="◯"
      title="Chưa có hồ sơ"
      hint="Nhập externalId ở trên và bấm Tải, hoặc đăng ký mới từ trang Đăng ký."
    />
  </div>
</template>

<style scoped>
.alert {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 0.9rem;
}
.alert.success {
  background: rgba(34, 197, 94, 0.1);
  color: #15803d;
  border: 1px solid #86efac;
}
.alert.danger {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  border: 1px solid #fca5a5;
}
</style>
