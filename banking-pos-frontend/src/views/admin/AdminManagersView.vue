<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createManagerApi,
  listManagersApi,
  toggleManagerApi,
  type CreateManagerPayload
} from '../../api/auth.api'
import { useApiAction } from '../../composables/useApiAction'
import EmptyState from '../../components/EmptyState.vue'
import PasswordInput from '../../components/PasswordInput.vue'

const { run, running } = useApiAction()

interface Manager {
  id: string
  email: string
  customerId: string
  role: string
  enabled: boolean
  createdAt: string
}

const managers = ref<Manager[]>([])

const form = reactive<CreateManagerPayload>({
  email: '',
  customerId: '',
  temporaryPassword: 'Welcome@123'
})

function genCustomerId() {
  form.customerId = `mgr-${Date.now().toString(36)}`
}

async function load() {
  const resp = await run('Lấy danh sách managers', () => listManagersApi(), { silent: true })
  managers.value = Array.isArray(resp.data) ? (resp.data as Manager[]) : []
}

async function create() {
  if (!form.email || !form.customerId) return
  await run('Tạo manager', () => createManagerApi({ ...form }), {
    successToast: `Đã tạo manager: ${form.email}`
  })
  form.email = ''
  form.customerId = ''
  form.temporaryPassword = 'Welcome@123'
  await load()
}

async function toggle(mgr: Manager) {
  await run(`${mgr.enabled ? 'Vô hiệu' : 'Kích hoạt'} manager`, () => toggleManagerApi(mgr.id), {
    successToast: `Đã cập nhật trạng thái manager ${mgr.email}`
  })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Quản lý Managers</h2>
          <p class="lead">
            Chỉ <span class="kbd">SUPER_ADMIN</span> mới thấy trang này. Manager có quyền xem khách hàng
            và tài khoản, nhưng không thể tạo manager khác hoặc thay đổi cấu hình hệ thống.
          </p>
        </div>
        <button class="secondary" :disabled="running" @click="load">Làm mới</button>
      </div>
    </section>

    <section class="card">
      <h3>Tạo Manager mới</h3>
      <div class="form-grid">
        <label>Email
          <input v-model="form.email" type="email" placeholder="manager@mockbank.local" />
        </label>
        <label>
          Customer ID
          <div class="inline-row">
            <input v-model="form.customerId" placeholder="mgr-xxxx" />
            <button class="ghost small" @click="genCustomerId">Tạo tự động</button>
          </div>
        </label>
        <label>Mật khẩu tạm thời
          <PasswordInput v-model="form.temporaryPassword" placeholder="Welcome@123" autocomplete="new-password" />
        </label>
      </div>
      <div class="actions">
        <button :disabled="running || !form.email || !form.customerId" @click="create">
          Tạo Manager
        </button>
      </div>
      <p class="hint">
        Manager sẽ đăng nhập tại <span class="kbd">/admin/login</span> với email và mật khẩu tạm thời trên.
        Quyền hạn: <span class="kbd">fdx:customers.read fdx:accounts.read admin:accounts.read admin:accounts.write fdx:bill.read</span>
      </p>
    </section>

    <section class="card" v-if="managers.length > 0">
      <h3>Danh sách Managers ({{ managers.length }})</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>Customer ID</th>
              <th>Role</th>
              <th>Trạng thái</th>
              <th>Ngày tạo</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="mgr in managers" :key="mgr.id">
              <td>{{ mgr.email }}</td>
              <td><span class="kbd">{{ mgr.customerId }}</span></td>
              <td><span class="pill">{{ mgr.role }}</span></td>
              <td>
                <span class="pill" :class="mgr.enabled ? 'success' : 'danger'">
                  {{ mgr.enabled ? 'Hoạt động' : 'Vô hiệu' }}
                </span>
              </td>
              <td class="muted">{{ mgr.createdAt ? new Date(mgr.createdAt).toLocaleDateString('vi') : '—' }}</td>
              <td>
                <button class="ghost small" :disabled="running" @click="toggle(mgr)">
                  {{ mgr.enabled ? 'Vô hiệu hóa' : 'Kích hoạt' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <EmptyState
      v-else
      icon="⚙"
      title="Chưa có manager nào"
      hint="Tạo manager đầu tiên bằng form phía trên."
    />
  </div>
</template>
