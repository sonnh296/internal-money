<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { APP_ROUTES } from '../../constants/app.constants'
import { useAdminAuthStore } from '../../stores/adminAuth.store'
import { useApiAction } from '../../composables/useApiAction'
import { listAllAccountsAdminApi } from '../../api/account.api'
import { listBillersApi } from '../../api/biller.api'
import { customerHealthApi } from '../../api/customer.api'
import { jwksApi, testPublicApi, testSecureApi } from '../../api/auth.api'
import type { AccountResponse, BillerResponse } from '../../types/api.types'
import StatTile from '../../components/StatTile.vue'

const auth = useAdminAuthStore()
const { run, running } = useApiAction()

const accounts = ref<AccountResponse[]>([])
const billers = ref<BillerResponse[]>([])

const totalsByCurrency = computed(() => {
  const map = new Map<string, number>()
  for (const a of accounts.value) {
    map.set(a.currency, (map.get(a.currency) ?? 0) + Number(a.balance ?? 0))
  }
  return [...map.entries()]
})

const activeCount = computed(() => accounts.value.filter((a) => a.status === 'ACTIVE').length)
const frozenCount = computed(() => accounts.value.filter((a) => a.status === 'FROZEN').length)

async function refresh() {
  try {
    const resp = await run('Admin: list all accounts', () => listAllAccountsAdminApi(), { silent: true })
    accounts.value = Array.isArray(resp.data) ? (resp.data as AccountResponse[]) : []
  } catch {
    accounts.value = []
  }
  try {
    const resp = await run('Admin: list billers', () => listBillersApi(20, 0), { silent: true })
    const body = resp.data
    if (Array.isArray(body)) billers.value = body as BillerResponse[]
    else if (body && Array.isArray((body as { items?: unknown[] }).items))
      billers.value = (body as { items: BillerResponse[] }).items
    else billers.value = []
  } catch {
    billers.value = []
  }
}

async function smoke() {
  await run('Smoke: GET /api/v1/test/public', () => testPublicApi())
  await run('Smoke: GET /api/v1/test/secure (cần JWT)', () => testSecureApi())
  await run('Smoke: GET /.well-known/jwks.json', () => jwksApi())
  await run('Smoke: GET customer service health', () => customerHealthApi())
}

onMounted(refresh)
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Admin overview</h2>
          <p class="lead">
            Xin chào <strong>{{ auth.profile.email }}</strong>. Bạn đang ở cổng quản trị hoàn toàn tách biệt với cổng khách hàng.
          </p>
        </div>
        <div class="inline-row">
          <button class="secondary small" :disabled="running" @click="refresh">↻ Refresh</button>
          <button class="small" :disabled="running" @click="smoke">Smoke test</button>
        </div>
      </div>
    </section>

    <section class="card-grid">
      <StatTile label="Tổng accounts (admin)" :value="accounts.length" hint="GET /api/v1/accounts" />
      <StatTile label="Active accounts" :value="activeCount" :hint="`${frozenCount} đang FROZEN`" trend="up" />
      <StatTile label="Billers (page 1)" :value="billers.length" hint="GET /api/v1/billers" />
      <StatTile
        label="Scopes của bạn"
        :value="auth.profile.scopes.length"
        :hint="auth.profile.scopes.slice(0, 3).join(', ') + (auth.profile.scopes.length > 3 ? '…' : '')"
      />
    </section>

    <section class="card">
      <h2>Tổng số dư theo loại tiền</h2>
      <div class="table-wrap" v-if="totalsByCurrency.length">
        <table class="data">
          <thead><tr><th>Currency</th><th>Tổng số dư</th></tr></thead>
          <tbody>
            <tr v-for="[ccy, total] in totalsByCurrency" :key="ccy">
              <td><span class="pill info">{{ ccy }}</span></td>
              <td><strong>{{ total.toLocaleString() }}</strong></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="muted" v-else>Chưa có account nào để tổng hợp.</p>
    </section>

    <section class="card">
      <h2>Lối tắt vận hành</h2>
      <div class="card-grid three">
        <RouterLink :to="APP_ROUTES.ADMIN_CUSTOMERS" class="card">
          <h2>Customers / KYC</h2>
          <p class="lead">Tạo hồ sơ, tra cứu, cập nhật KYC để bật/tắt cấp tài khoản.</p>
        </RouterLink>
        <RouterLink :to="APP_ROUTES.ADMIN_SERVICES" class="card">
          <h2>Dịch vụ & Hóa đơn</h2>
          <p class="lead">Quản lý catalog dịch vụ và phát hành hóa đơn.</p>
        </RouterLink>
        <RouterLink :to="APP_ROUTES.ADMIN_HEALTH" class="card">
          <h2>System Health</h2>
          <p class="lead">Theo dõi trạng thái sống của các service cốt lõi.</p>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
