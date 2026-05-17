<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { creditApi, debitApi, getAccountApi, getBalanceApi, listTransactionsApi } from '../../api/account.api'
import { useApiAction } from '../../composables/useApiAction'
import type { AccountBalanceResponse, AccountResponse, TransactionResponse } from '../../types/api.types'
import { counterpartyLine, flowLabel, typeLabel } from '../../utils/transactionLabels'
import StatTile from '../../components/StatTile.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const { run, running } = useApiAction()

const accountId = computed(() => String(route.params.id ?? ''))
const account = ref<AccountResponse | null>(null)
const balance = ref<AccountBalanceResponse | null>(null)
const transactions = ref<TransactionResponse[]>([])

const txQuery = reactive({ limit: 10, offset: 0, type: '' })
const moveForm = reactive({ amount: 100, reason: 'Customer demo movement' })

function format(currency: string, value: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND' }).format(value)
}

async function loadAccount() {
  if (!accountId.value) return
  const resp = await run('Lấy chi tiết tài khoản', () => getAccountApi(accountId.value), { silent: true })
  account.value = resp.data as AccountResponse
}

async function loadBalance() {
  if (!accountId.value) return
  const resp = await run('Lấy số dư', () => getBalanceApi(accountId.value), { silent: true })
  balance.value = resp.data as AccountBalanceResponse
}

async function loadTransactions() {
  if (!accountId.value) return
  const resp = await run(
    'Lấy lịch sử giao dịch',
    () =>
      listTransactionsApi(accountId.value, {
        limit: txQuery.limit,
        offset: txQuery.offset,
        type: txQuery.type || undefined
      }),
    { silent: true }
  )
  const data = resp.data
  if (Array.isArray(data)) transactions.value = data as TransactionResponse[]
  else if (data && Array.isArray((data as { items?: unknown[] }).items))
    transactions.value = (data as { items: TransactionResponse[] }).items
  else transactions.value = []
}

async function deposit() {
  await run(
    'Nạp tiền (credit)',
    () =>
      creditApi(accountId.value, {
        amount: moveForm.amount,
        reason: moveForm.reason,
        idempotencyKey: `dep-${Date.now()}`
      }),
    { successToast: 'Nạp tiền thành công' }
  )
  await Promise.all([loadAccount(), loadBalance(), loadTransactions()])
}

async function withdraw() {
  await run(
    'Rút tiền (debit)',
    () =>
      debitApi(accountId.value, {
        amount: moveForm.amount,
        reason: moveForm.reason,
        idempotencyKey: `wd-${Date.now()}`
      }),
    { successToast: 'Rút tiền thành công' }
  )
  await Promise.all([loadAccount(), loadBalance(), loadTransactions()])
}

watch(accountId, async () => {
  await Promise.all([loadAccount(), loadBalance(), loadTransactions()])
})

onMounted(async () => {
  await Promise.all([loadAccount(), loadBalance(), loadTransactions()])
})
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2 v-if="account">{{ account.displayName || account.nickname || account.maskedAccountNumber || account.id }}</h2>
          <h2 v-else>Chi tiết tài khoản</h2>
          <p class="lead">
            <span class="kbd">{{ accountId }}</span>
            <span v-if="account"> · {{ account.accountType }} · {{ account.currency }}</span>
          </p>
        </div>
        <span v-if="account" class="pill" :class="{
          success: account.status === 'ACTIVE',
          warning: account.status === 'FROZEN',
          danger: account.status === 'CLOSED'
        }">{{ account.status }}</span>
      </div>
    </section>

    <section class="card-grid three" v-if="balance && account">
      <StatTile label="Số dư ledger" :value="format(account.currency, Number(balance.balance))" hint="balance" />
      <StatTile label="Đang hold" :value="format(account.currency, Number(balance.totalHolds))" hint="totalHolds" />
      <StatTile
        label="Khả dụng để chi"
        :value="format(account.currency, Number(balance.available))"
        hint="balance − totalHolds"
        trend="up"
      />
    </section>

    <section class="card">
      <h2>Nạp / Rút tiền (demo)</h2>
      <p class="lead">Backend cho phép credit/debit trực tiếp tài khoản. Idempotency-Key được sinh tự động cho mỗi lần bấm.</p>
      <div class="form-grid">
        <label>Số tiền <input v-model.number="moveForm.amount" type="number" min="0.01" step="0.01" /></label>
        <label>Nội dung <input v-model="moveForm.reason" /></label>
      </div>
      <div class="actions">
        <button :disabled="running" @click="deposit">Nạp (credit)</button>
        <button :disabled="running" class="danger" @click="withdraw">Rút (debit)</button>
      </div>
    </section>

    <section class="card">
      <div class="row-between">
        <h2>Lịch sử giao dịch</h2>
        <div class="inline-row">
          <label>Type
            <select v-model="txQuery.type">
              <option value="">Tất cả</option>
              <option>CREDIT</option>
              <option>DEBIT</option>
              <option>HOLD_PLACED</option>
              <option>HOLD_RELEASED</option>
            </select>
          </label>
          <label>Limit <input v-model.number="txQuery.limit" type="number" min="1" max="100" /></label>
          <label>Offset <input v-model.number="txQuery.offset" type="number" min="0" /></label>
          <button class="secondary" :disabled="running" @click="loadTransactions">Lọc</button>
        </div>
      </div>

      <div v-if="transactions.length" class="table-wrap">
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
            <tr v-for="t in transactions" :key="t.id">
              <td><span class="kbd">{{ String(t.id).slice(0, 10) }}…</span></td>
              <td>
                <span class="pill" :class="{
                  success: t.flowDirection === 'IN',
                  danger: t.flowDirection === 'OUT'
                }">{{ flowLabel(t.flowDirection) }}</span>
              </td>
              <td>
                <span class="pill muted">{{ typeLabel(t.type) }}</span>
              </td>
              <td>{{ account ? format(account.currency, Number(t.amount)) : t.amount }}</td>
              <td>{{ counterpartyLine(t) }}</td>
              <td>{{ t.reason ?? '—' }}</td>
              <td>{{ t.balanceAfter != null && account ? format(account.currency, Number(t.balanceAfter)) : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="∅" title="Chưa có giao dịch" hint="Thử nạp/rút tiền hoặc thực hiện thanh toán hóa đơn." />
    </section>
  </div>
</template>
