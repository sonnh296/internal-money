<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listCustomerAccountsApi } from '../../api/account.api'
import { useUserAuthStore } from '../../stores/userAuth.store'
import { useApiAction } from '../../composables/useApiAction'
import type { AccountResponse } from '../../types/api.types'
import EmptyState from '../../components/EmptyState.vue'

const auth = useUserAuthStore()
const { run, running } = useApiAction()

const accounts = ref<AccountResponse[]>([])
const overrides = reactive({
  customerId: ''
})

function format(currency: string, value: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND' }).format(value)
}

async function load() {
  const cid = overrides.customerId || auth.profile.customerId
  if (!cid) return
  const resp = await run('Lấy danh sách tài khoản', () => listCustomerAccountsApi(cid), { silent: true })
  accounts.value = Array.isArray(resp.data) ? (resp.data as AccountResponse[]) : []
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Tài khoản của tôi</h2>
          <p class="lead">Hiển thị danh sách tài khoản gắn với customerId của bạn.</p>
        </div>
        <div class="inline-row">
          <input
            v-model="overrides.customerId"
            :placeholder="`Override customerId (mặc định: ${auth.profile.customerId || '—'})`"
            style="min-width: 280px;"
          />
          <button class="secondary" :disabled="running" @click="load">↻ Tải lại</button>
        </div>
      </div>
    </section>

    <section class="card">
      <div v-if="accounts.length" class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>Tên hiển thị</th>
              <th>Account #</th>
              <th>Loại</th>
              <th>Tiền tệ</th>
              <th>Số dư</th>
              <th>Trạng thái</th>
              <th>Version</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in accounts" :key="a.id">
              <td>{{ a.displayName || a.nickname || '—' }}</td>
              <td><span class="kbd">{{ a.maskedAccountNumber || a.accountNumber || a.id }}</span></td>
              <td>{{ a.accountType }} · {{ a.accountSubType }}</td>
              <td>{{ a.currency }}</td>
              <td><strong>{{ format(a.currency, Number(a.balance)) }}</strong></td>
              <td>
                <span class="pill" :class="{
                  success: a.status === 'ACTIVE',
                  warning: a.status === 'FROZEN',
                  danger: a.status === 'CLOSED'
                }">{{ a.status }}</span>
              </td>
              <td>{{ a.version }}</td>
              <td>
                <RouterLink :to="`/app/accounts/${a.id}`" class="pill info">Chi tiết →</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-else
        icon="◯"
        title="Không có tài khoản nào"
        hint="customerId chưa có tài khoản hoặc backend chưa trả về kết quả."
      />
    </section>
  </div>
</template>
