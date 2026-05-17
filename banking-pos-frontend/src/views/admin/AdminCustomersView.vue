<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createCustomerApi,
  listAllCustomersAdminApi,
  notifyBalanceAdjustmentApi,
  updateKycApi,
  type CustomerCreatePayload
} from '../../api/customer.api'
import {
  createAccountApi,
  creditApi,
  debitApi,
  listCustomerAccountsApi,
  updateAccountStatusApi
} from '../../api/account.api'
import { useApiAction } from '../../composables/useApiAction'
import type { AccountResponse, CustomerResponse } from '../../types/api.types'
import EmptyState from '../../components/EmptyState.vue'
import MoneyInput from '../../components/MoneyInput.vue'

const { run, running } = useApiAction()

interface CustomerRow {
  customer: CustomerResponse
  accounts: AccountResponse[]
  expanded: boolean
  loadingAccounts: boolean
}

const rows = ref<CustomerRow[]>([])
const searchQuery = ref('')
const appliedSearch = ref('')
const showCreateForm = ref(false)
const emailNotice = ref<{ type: 'verify' | 'reject'; email: string } | null>(null)
const postingPanel = ref<{ row: CustomerRow; account: AccountResponse } | null>(null)
const postingDraft = reactive({
  amount: 0,
  type: 'CREDIT' as 'CREDIT' | 'DEBIT',
  reason: ''
})

const createForm = reactive<CustomerCreatePayload>({
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  address: '',
  externalId: ''
})

function applySearch() {
  appliedSearch.value = searchQuery.value.trim().toLowerCase()
}

const filteredRows = computed(() => {
  const q = appliedSearch.value
  if (!q) return rows.value
  return rows.value.filter((row) => {
    const c = row.customer
    const haystack = [
      c.externalId,
      c.email,
      c.firstName,
      c.lastName,
      c.phone,
      `${c.firstName ?? ''} ${c.lastName ?? ''}`
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return haystack.includes(q)
  })
})

function genId() {
  const ts = Date.now().toString(36)
  createForm.externalId = `cust-${ts}`
  createForm.email = `cust.${ts}@mockbank.local`
}

async function loadAll() {
  const resp = await run('Lấy tất cả khách hàng', () => listAllCustomersAdminApi(), { silent: true })
  const customers = Array.isArray(resp.data) ? (resp.data as CustomerResponse[]) : []
  rows.value = customers.map((c) => ({
    customer: c,
    accounts: [],
    expanded: false,
    loadingAccounts: false
  }))
}

async function reloadAccounts(row: CustomerRow) {
  const accResp = await run('Lấy tài khoản', () => listCustomerAccountsApi(row.customer.externalId), {
    silent: true
  })
  row.accounts = Array.isArray(accResp.data) ? (accResp.data as AccountResponse[]) : []
}

async function toggleExpand(row: CustomerRow) {
  if (row.expanded) {
    row.expanded = false
    return
  }
  row.loadingAccounts = true
  try {
    await reloadAccounts(row)
    row.expanded = true
  } finally {
    row.loadingAccounts = false
  }
}

async function createCustomer() {
  const resp = await run('Tạo khách hàng', () => createCustomerApi({ ...createForm }), {
    successToast: `Hồ sơ "${createForm.firstName} ${createForm.lastName}" đã được tạo`
  })
  const created = resp.data as { externalId?: string }
  if (created?.externalId) {
    searchQuery.value = created.externalId
  }
  Object.assign(createForm, { firstName: '', lastName: '', email: '', phone: '', address: '', externalId: '' })
  showCreateForm.value = false
  await loadAll()
}

async function verifyKyc(row: CustomerRow) {
  await run('Xác minh KYC', () => updateKycApi(row.customer.externalId, 'VERIFIED'), {
    successToast: 'KYC xác minh thành công — email thông báo đã được ghi nhận'
  })
  row.customer.kycStatus = 'VERIFIED'
  row.customer.active = true
  emailNotice.value = { type: 'verify', email: row.customer.email }
  setTimeout(() => {
    emailNotice.value = null
  }, 8000)
}

async function rejectKyc(row: CustomerRow) {
  await run('Từ chối KYC', () => updateKycApi(row.customer.externalId, 'REJECTED'), {
    successToast: 'Đã từ chối KYC — email thông báo đã được ghi nhận'
  })
  row.customer.kycStatus = 'REJECTED'
  emailNotice.value = { type: 'reject', email: row.customer.email }
  setTimeout(() => {
    emailNotice.value = null
  }, 8000)
}

async function freezeAccount(account: AccountResponse, row: CustomerRow) {
  await run('Đóng băng tài khoản', () => updateAccountStatusApi(account.id, 'FROZEN'), {
    successToast: 'Đã đóng băng tài khoản'
  })
  account.status = 'FROZEN'
  if (postingPanel.value?.account.id === account.id) closePosting()
}

async function activateAccount(account: AccountResponse) {
  await run('Kích hoạt tài khoản', () => updateAccountStatusApi(account.id, 'ACTIVE'), {
    successToast: 'Đã kích hoạt tài khoản'
  })
  account.status = 'ACTIVE'
}

function openPosting(row: CustomerRow, account: AccountResponse) {
  postingPanel.value = { row, account }
  postingDraft.amount = 0
  postingDraft.type = 'CREDIT'
  postingDraft.reason = ''
}

function closePosting() {
  postingPanel.value = null
}

async function openPostingForCustomer(row: CustomerRow) {
  if (!row.expanded) {
    await toggleExpand(row)
  }
  const acc = row.accounts.find((a) => a.status === 'ACTIVE')
  if (!acc) return
  openPosting(row, acc)
}

async function submitPosting() {
  if (!postingPanel.value) return
  const { row, account } = postingPanel.value
  if (postingDraft.amount <= 0 || !postingDraft.reason.trim()) return
  const isCredit = postingDraft.type === 'CREDIT'
  const label = isCredit ? 'Nạp' : 'Rút'
  if (!confirm(`${label} ${postingDraft.amount} ${account.currency} vào tài khoản ${account.accountNumber || account.id}?`)) {
    return
  }
  const resp = await run(
    label + ' tiền',
    () =>
      isCredit
        ? creditApi(account.id, { amount: postingDraft.amount, reason: postingDraft.reason.trim() }, account.version)
        : debitApi(account.id, { amount: postingDraft.amount, reason: postingDraft.reason.trim() }, account.version),
    { successToast: `Đã ${label.toLowerCase()} tiền. Đã gửi email thông báo cho khách hàng (nếu SMTP đã cấu hình).` }
  )
  const updated = resp.data as AccountResponse
  account.balance = updated.balance
  account.version = updated.version
  await notifyBalanceAdjustmentApi({
    customerId: row.customer.externalId,
    amount: postingDraft.amount,
    type: postingDraft.type,
    reason: postingDraft.reason.trim(),
    balanceAfter: Number(updated.balance)
  }).catch(() => undefined)
  closePosting()
}

async function createAccountForCustomer(row: CustomerRow) {
  const customerId = row.customer.externalId
  const idemKey = `acc-${Date.now()}-${Math.random().toString(36).slice(2)}`
  await run(
    'Tạo tài khoản',
    () =>
      createAccountApi(
        {
          customerId,
          accountType: 'CHEQUING',
          accountSubType: 'PERSONAL',
          status: 'ACTIVE',
          currency: 'VND',
          nickname: 'Checking',
          displayName: 'Tài khoản VND',
          openingBalance: 0
        },
        idemKey
      ),
    { successToast: 'Đã tạo tài khoản CHEQUING VND' }
  )
  await reloadAccounts(row)
  row.expanded = true
}

function formatMoney(currency: string, value: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND' }).format(value)
}

onMounted(loadAll)
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Khách hàng & Tài khoản</h2>
          <p class="lead">
            Quản lý hồ sơ KYC và tài khoản. Khi Verify KYC, hệ thống tự tạo tài khoản ngân hàng và gửi email cho khách.
          </p>
        </div>
        <div class="inline-row">
          <button class="secondary" :disabled="running" @click="loadAll">↻ Làm mới</button>
          <button @click="showCreateForm = !showCreateForm">
            {{ showCreateForm ? '✕ Đóng' : '+ Tạo hồ sơ' }}
          </button>
        </div>
      </div>
    </section>

    <div v-if="emailNotice" class="email-notice" :class="emailNotice.type === 'verify' ? 'success' : 'warning'">
      <span>{{ emailNotice.type === 'verify' ? '✉ Email xác minh' : '✉ Email từ chối KYC' }}</span>
      đã gửi đến <strong>{{ emailNotice.email }}</strong>.
    </div>

    <section class="card" v-if="showCreateForm">
      <h3>Tạo hồ sơ khách hàng mới</h3>
      <div class="form-grid">
        <label>Họ <input v-model="createForm.firstName" placeholder="Nguyen" /></label>
        <label>Tên <input v-model="createForm.lastName" placeholder="An" /></label>
        <label>Email <input v-model="createForm.email" type="email" /></label>
        <label>Điện thoại <input v-model="createForm.phone" placeholder="+84900000001" /></label>
        <label>Địa chỉ <input v-model="createForm.address" placeholder="123 Đường Demo" /></label>
        <label>External ID
          <div class="inline-row">
            <input v-model="createForm.externalId" placeholder="cust-xxxx" />
            <button class="ghost small" @click="genId">Tạo tự động</button>
          </div>
        </label>
      </div>
      <div class="actions">
        <button
          :disabled="running || !createForm.firstName || !createForm.email || !createForm.externalId"
          @click="createCustomer">
          Tạo hồ sơ
        </button>
        <button class="ghost" @click="showCreateForm = false">Hủy</button>
      </div>
    </section>

    <section class="card">
      <div class="row-between" style="margin-bottom:12px; flex-wrap:wrap; gap:12px;">
        <h3>Danh sách khách hàng ({{ filteredRows.length }})</h3>
        <div class="inline-row" style="flex:1; min-width:280px; max-width:520px; justify-content:flex-end;">
          <input
            v-model="searchQuery"
            placeholder="Tìm theo externalId, email, họ tên, SĐT…"
            style="flex:1; min-width:180px;"
            @keyup.enter="applySearch"
          />
          <button :disabled="running" @click="applySearch">Tìm</button>
          <button
            v-if="appliedSearch"
            class="ghost"
            @click="searchQuery = ''; appliedSearch = ''">
            Xóa
          </button>
        </div>
      </div>

      <div v-if="filteredRows.length > 0" class="table-wrap customers-table">
        <table>
          <thead>
            <tr>
              <th></th>
              <th>Họ tên</th>
              <th>External ID</th>
              <th>Email</th>
              <th>KYC</th>
              <th>Trạng thái</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="row in filteredRows" :key="row.customer.externalId">
              <tr class="customer-row">
                <td>
                  <button class="ghost small icon-btn" :disabled="row.loadingAccounts" @click="toggleExpand(row)">
                    {{ row.loadingAccounts ? '…' : row.expanded ? '▾' : '▸' }}
                  </button>
                </td>
                <td><strong>{{ row.customer.firstName }} {{ row.customer.lastName }}</strong></td>
                <td><span class="kbd small">{{ row.customer.externalId }}</span></td>
                <td class="muted small">{{ row.customer.email }}</td>
                <td>
                  <span
                    class="pill small"
                    :class="{
                      success: row.customer.kycStatus === 'VERIFIED',
                      warning: row.customer.kycStatus === 'PENDING',
                      danger: row.customer.kycStatus === 'REJECTED'
                    }">
                    {{ row.customer.kycStatus }}
                  </span>
                </td>
                <td>
                  <span class="pill small" :class="row.customer.active ? 'success' : ''">
                    {{ row.customer.active ? 'Hoạt động' : 'Chưa KT' }}
                  </span>
                </td>
                <td>
                  <div class="inline-row action-cell">
                    <button
                      v-if="row.customer.kycStatus !== 'VERIFIED'"
                      class="small"
                      :disabled="running"
                      @click="verifyKyc(row)">
                      Verify
                    </button>
                    <button
                      v-if="row.customer.kycStatus === 'PENDING'"
                      class="ghost small"
                      :disabled="running"
                      @click="rejectKyc(row)">
                      Reject
                    </button>
                    <button class="secondary small" :disabled="running" @click="openPostingForCustomer(row)">
                      Chỉnh tiền
                    </button>
                  </div>
                </td>
              </tr>

              <tr v-if="row.expanded && row.accounts.length > 0" class="account-header-row">
                <td></td>
                <td colspan="6">
                  <table class="nested-accounts">
                    <thead>
                      <tr>
                        <th>Số tài khoản</th>
                        <th>Loại</th>
                        <th>Số dư</th>
                        <th>Trạng thái</th>
                        <th>Phiên bản</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="acc in row.accounts" :key="acc.id">
                        <td>
                          <span class="kbd">{{ acc.accountNumber || acc.maskedAccountNumber || acc.id }}</span>
                        </td>
                        <td>{{ acc.accountType }} · {{ acc.accountSubType }}</td>
                        <td><strong>{{ formatMoney(acc.currency, Number(acc.balance ?? 0)) }}</strong></td>
                        <td>
                          <span
                            class="pill small"
                            :class="{
                              success: acc.status === 'ACTIVE',
                              warning: acc.status === 'FROZEN',
                              danger: acc.status === 'CLOSED'
                            }">
                            {{ acc.status }}
                          </span>
                        </td>
                        <td class="muted small">v{{ acc.version }}</td>
                        <td>
                          <div class="inline-row">
                            <button
                              v-if="acc.status === 'ACTIVE'"
                              class="ghost small"
                              :disabled="running"
                              @click="openPosting(row, acc)">
                              Chỉnh tiền
                            </button>
                            <button
                              v-if="acc.status === 'ACTIVE'"
                              class="ghost small"
                              :disabled="running"
                              @click="freezeAccount(acc, row)">
                              Đóng băng
                            </button>
                            <button
                              v-if="acc.status === 'FROZEN'"
                              class="ghost small"
                              :disabled="running"
                              @click="activateAccount(acc)">
                              Kích hoạt
                            </button>
                          </div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </td>
              </tr>

              <tr v-if="row.expanded && row.accounts.length === 0" class="account-sub-row">
                <td></td>
                <td colspan="6" class="muted small">
                  Chưa có tài khoản ngân hàng.
                  <button
                    class="ghost small"
                    style="margin-left:8px;"
                    :disabled="running"
                    @click="createAccountForCustomer(row)">
                    + Tạo tài khoản
                  </button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>

      <EmptyState
        v-else-if="!running"
        icon="☰"
        :title="appliedSearch ? 'Không tìm thấy khách hàng' : 'Chưa có khách hàng nào'"
        :hint="appliedSearch ? 'Thử từ khóa khác.' : 'Nhấn + Tạo hồ sơ để thêm khách hàng đầu tiên.'"
      />
    </section>

    <div v-if="postingPanel" class="posting-overlay" @click.self="closePosting">
      <section class="card posting-dialog">
        <div class="row-between">
          <h3>Chỉnh tiền tài khoản</h3>
          <button class="ghost small" @click="closePosting">✕</button>
        </div>
        <p class="hint">
          Khách: <strong>{{ postingPanel.row.customer.firstName }} {{ postingPanel.row.customer.lastName }}</strong>
          · STK: <span class="kbd">{{ postingPanel.account.accountNumber || postingPanel.account.id }}</span>
          · Số dư hiện tại:
          <strong>{{ formatMoney(postingPanel.account.currency, Number(postingPanel.account.balance ?? 0)) }}</strong>
        </p>
        <div class="form-grid">
          <label>Số tiền
            <MoneyInput v-model="postingDraft.amount" placeholder="VD: 1.000.000" />
          </label>
          <label>Loại giao dịch
            <select v-model="postingDraft.type">
              <option value="CREDIT">Nạp tiền</option>
              <option value="DEBIT">Rút tiền</option>
            </select>
          </label>
          <label style="grid-column:1/-1;">Lý do
            <input v-model="postingDraft.reason" placeholder="Bắt buộc" />
          </label>
        </div>
        <div class="actions">
          <button
            :disabled="running || postingDraft.amount <= 0 || !postingDraft.reason.trim()"
            @click="submitPosting">
            Xác nhận
          </button>
          <button class="ghost" @click="closePosting">Hủy</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.email-notice {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 0.9rem;
  border-left: 4px solid;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.email-notice.success {
  background: rgba(34, 197, 94, 0.1);
  border-color: #22c55e;
  color: #15803d;
}
.email-notice.warning {
  background: rgba(245, 158, 11, 0.1);
  border-color: #f59e0b;
  color: #92400e;
}
.customers-table table {
  width: 100%;
}
.customer-row td {
  vertical-align: middle;
}
.account-header-row > td {
  padding: 0 0 12px 0;
  background: var(--surface-alt, #f8f9fa);
  border-top: none;
}
.nested-accounts {
  width: 100%;
  margin: 8px 0 4px 24px;
  font-size: 13px;
  border-collapse: collapse;
}
.nested-accounts th {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border, #e5e7eb);
  color: var(--text-muted, #6b7280);
  font-weight: 600;
}
.nested-accounts td {
  padding: 10px;
  border-bottom: 1px solid var(--border, #e5e7eb);
}
.account-sub-row td {
  background: var(--surface-alt, #f8f9fa);
  padding: 12px 16px 12px 40px;
}
.action-cell {
  flex-wrap: wrap;
  gap: 6px;
}
.icon-btn {
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 12px;
}
.posting-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.posting-dialog {
  width: 100%;
  max-width: 480px;
  margin: 0;
}
</style>
