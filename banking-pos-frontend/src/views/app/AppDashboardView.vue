<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { APP_ROUTES } from '../../constants/app.constants'
import { useUserAuthStore } from '../../stores/userAuth.store'
import { useApiAction } from '../../composables/useApiAction'
import { getCustomerApi } from '../../api/customer.api'
import { getMyAccountApi, listTransactionsApi } from '../../api/account.api'
import type { AccountResponse, CustomerResponse, TransactionResponse } from '../../types/api.types'
import { counterpartyLine, flowLabel, typeLabel } from '../../utils/transactionLabels'
import StatTile from '../../components/StatTile.vue'
import EmptyState from '../../components/EmptyState.vue'
import { errorDetail, httpStatus, isNotFound } from '../../utils/httpError'

const auth = useUserAuthStore()
const { run, running } = useApiAction()

const profile = ref<CustomerResponse | null>(null)
const profileMissing = ref(false)
const profileError = ref('')
const myAccount = ref<AccountResponse | null>(null)
const recentTx = ref<TransactionResponse[]>([])
const customerId = computed(() => auth.profile.customerId || '')

const totalBalance = computed(() => Number(myAccount.value?.balance ?? 0))

async function refresh() {
  if (!customerId.value) return
  profileMissing.value = false
  profileError.value = ''
  try {
    const profileResp = await run('Lấy hồ sơ khách hàng', () => getCustomerApi(customerId.value), { silent: true })
    profile.value = profileResp.data as CustomerResponse
  } catch (err) {
    profile.value = null
    const status = httpStatus(err)
    profileMissing.value = isNotFound(err) || status === 500
    profileError.value = errorDetail(err)
  }
  try {
    const accResp = await run('Lấy tài khoản', () => getMyAccountApi(), { silent: true })
    myAccount.value = accResp.data as AccountResponse
  } catch {
    myAccount.value = null
  }

  recentTx.value = []
  if (myAccount.value) {
    const txResp = await run(
      'Lấy giao dịch gần đây',
      () => listTransactionsApi(myAccount.value!.id, { limit: 5, offset: 0 }),
      { silent: true }
    )
    const data = txResp.data
    if (Array.isArray(data)) recentTx.value = data as TransactionResponse[]
    else if (data && Array.isArray((data as { items?: unknown[] }).items))
      recentTx.value = (data as { items: TransactionResponse[] }).items
  }
}

function format(currency: string, value: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND' }).format(value)
}

onMounted(refresh)
</script>

<template>
  <div class="stack">
    <section v-if="profileMissing" class="card" style="border-color: #f59e0b;">
      <h2>Chưa có hồ sơ khách hàng</h2>
      <p class="lead">
        Bạn đã đăng nhập với <span class="kbd">customerId = {{ customerId }}</span>, nhưng CustomerService chưa có hồ sơ
        tương ứng. Tài khoản đăng nhập (Auth) và hồ sơ khách hàng (Customer DB) là hai bước riêng.
      </p>
      <ul class="lead" style="padding-left: 18px; margin: 8px 0;">
        <li>Admin: vào <strong>Cổng quản trị → Customers / KYC</strong>, tạo customer với cùng <span class="kbd">externalId</span>.</li>
        <li>Hoặc bạn: <RouterLink :to="APP_ROUTES.APP_REGISTER">Đăng ký hồ sơ mới</RouterLink> (dùng đúng externalId).</li>
      </ul>
      <p v-if="profileError" class="hint">Chi tiết lỗi: {{ profileError }}</p>
    </section>

    <section class="card">
      <div class="row-between">
        <div>
          <h2 v-if="profile">Xin chào, {{ profile.firstName }} {{ profile.lastName }}</h2>
          <h2 v-else>Xin chào!</h2>
          <p class="lead" v-if="profile">
            Email: {{ profile.email }} · KYC:
            <span class="pill" :class="{
              success: profile.kycStatus === 'VERIFIED',
              warning: profile.kycStatus === 'PENDING',
              danger: profile.kycStatus === 'REJECTED'
            }">{{ profile.kycStatus }}</span>
          </p>
          <p class="lead" v-else>Tổng quan tài khoản và giao dịch gần đây.</p>
        </div>
        <button class="secondary small" :disabled="running" @click="refresh">↻ Làm mới</button>
      </div>
    </section>

    <section class="card-grid">
      <StatTile
        label="Số tài khoản"
        :value="myAccount?.accountNumber ?? '—'"
        :hint="myAccount ? 'Tài khoản thanh toán chính' : 'Chưa có tài khoản'"
      />
      <StatTile
        label="Tổng số dư"
        :value="format(myAccount?.currency ?? 'VND', totalBalance)"
        :hint="myAccount ? 'Số dư hiện tại' : 'Chưa có tài khoản'"
      />
      <StatTile
        label="Trạng thái phiên"
        :value="auth.isAuthenticated ? 'Hoạt động' : 'Hết hạn'"
        :hint="auth.profile.email || 'Chưa rõ email'"
      />
    </section>

    <section class="card">
      <div class="row-between">
        <div>
          <h2>Tài khoản của bạn</h2>
          <p class="lead">Bấm vào một tài khoản để xem chi tiết, số dư và lịch sử giao dịch.</p>
        </div>
        <RouterLink :to="APP_ROUTES.APP_ACCOUNTS" class="kbd">Xem tất cả →</RouterLink>
      </div>

      <div v-if="myAccount" class="form-grid">
        <label>Số tài khoản <input :value="myAccount.accountNumber" readonly /></label>
        <label>Loại <input :value="`${myAccount.accountType} · ${myAccount.accountSubType}`" readonly /></label>
        <label>Số dư <input :value="format(myAccount.currency, Number(myAccount.balance))" readonly /></label>
        <label>Trạng thái <input :value="myAccount.status" readonly /></label>
      </div>
      <EmptyState
        v-else
        icon="◯"
        title="Bạn chưa có tài khoản nào"
        hint="Liên hệ ngân hàng để mở tài khoản, hoặc dùng cổng admin để tạo demo."
      />
    </section>

    <section class="card">
      <div class="row-between">
        <h2>Giao dịch gần đây</h2>
        <span class="muted" v-if="myAccount">trên tài khoản {{ myAccount.accountNumber }}</span>
      </div>
      <div v-if="recentTx.length" class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Chiều</th>
              <th>Loại</th>
              <th>Số tiền</th>
              <th>Đối tác / STK</th>
              <th>Nội dung</th>
              <th>Số dư sau</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in recentTx" :key="t.id">
              <td><span class="kbd">{{ String(t.id).slice(0, 8) }}…</span></td>
              <td>
                <span class="pill" :class="{ success: t.flowDirection === 'IN', danger: t.flowDirection === 'OUT' }">
                  {{ flowLabel(t.flowDirection) }}
                </span>
              </td>
              <td><span class="pill muted">{{ typeLabel(t.type) }}</span></td>
              <td>{{ format(myAccount?.currency ?? 'VND', Number(t.amount)) }}</td>
              <td>{{ counterpartyLine(t) }}</td>
              <td>{{ t.reason ?? '—' }}</td>
              <td>{{ t.balanceAfter != null ? format(myAccount?.currency ?? 'VND', Number(t.balanceAfter)) : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="∅" title="Chưa có giao dịch nào" hint="Thực hiện một giao dịch thanh toán để xem ở đây." />
    </section>
  </div>
</template>
