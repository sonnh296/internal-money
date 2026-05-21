<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Decimal from 'decimal.js-light'
import { listMyInvoicesApi } from '../../api/biller.api'
import { getMyAccountApi } from '../../api/account.api'
import { createBillPayApi, getPaymentApi } from '../../api/payment.api'
import { useApiAction } from '../../composables/useApiAction'
import { useNotifyStore } from '../../stores/notify.store'
import EmptyState from '../../components/EmptyState.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'
import type { AccountResponse } from '../../types/api.types'

const notify = useNotifyStore()
const { run, running } = useApiAction()

let isMounted = false
const confirmModal = ref<InstanceType<typeof ConfirmModal> | null>(null)

interface Invoice {
  id: string
  packageName: string
  billerReferenceNumber: string
  amount: number
  currency: string
  dueDate: string
  status: string
  customerId: string
}

const invoices = ref<Invoice[]>([])
const myAccount = ref<AccountResponse | null>(null)
const selectedInvoiceId = ref('')

const pendingInvoices = computed(() => invoices.value.filter((i) => i.status === 'PENDING'))
const selectedInvoice = computed(() => pendingInvoices.value.find((i) => i.id === selectedInvoiceId.value) ?? null)
const totalPending = computed(() => pendingInvoices.value.length)

const availableForPay = computed(() => {
  if (!myAccount.value) return new Decimal(0)
  const avail = myAccount.value.availableBalance
  if (avail != null) return new Decimal(avail)
  const holds = new Decimal(myAccount.value.totalHolds || 0)
  return new Decimal(myAccount.value.balance || 0).minus(holds)
})

const canPay = computed(() => {
  if (!myAccount.value || !selectedInvoice.value) return false
  return new Decimal(selectedInvoice.value.amount || 0).lte(availableForPay.value)
})

async function load() {
  const [invoiceResp, accountResp] = await Promise.all([
    run('Lấy hóa đơn', () => listMyInvoicesApi(), { silent: true }),
    run('Lấy tài khoản', () => getMyAccountApi(), { silent: true }).catch(() => ({ data: null }))
  ])
  if (!isMounted) return
  const invoiceData = invoiceResp.data as Invoice[] | { content?: Invoice[] }
  if (Array.isArray(invoiceData)) {
    invoices.value = invoiceData
  } else if (invoiceData && Array.isArray(invoiceData.content)) {
    invoices.value = invoiceData.content
  } else {
    invoices.value = []
  }
  myAccount.value = accountResp.data as AccountResponse | null
  if (!selectedInvoiceId.value && pendingInvoices.value[0]) {
    selectedInvoiceId.value = pendingInvoices.value[0].id
  }
}

function selectInvoice(inv: Invoice) {
  selectedInvoiceId.value = inv.id
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function waitForPaymentFinal(paymentId: string): Promise<'POSTED' | 'FAILED' | 'TIMEOUT'> {
  for (let attempt = 0; attempt < 20; attempt++) {
    try {
      const resp = await getPaymentApi(paymentId)
      const state = String((resp.data as { state?: string })?.state ?? '').toUpperCase()
      if (state === 'POSTED' || state === 'FAILED') return state as 'POSTED' | 'FAILED'
    } catch {
      /* retry */
    }
    await sleep(1500)
  }
  return 'TIMEOUT'
}

function confirmPay() {
  if (!canPay.value) return
  confirmModal.value?.open()
}

async function paySelected() {
  if (!myAccount.value || !selectedInvoice.value) {
    notify.push('error', 'Không thể thanh toán', 'Vui lòng chọn hóa đơn và đảm bảo tài khoản đã kích hoạt.')
    return
  }
  const inv = selectedInvoice.value
  const paidInvoiceId = inv.id
  try {
    const resp = await run(`Thanh toán hóa đơn ${inv.id.slice(0, 8)}`, () =>
      createBillPayApi(
        {
          debtorAccountId: myAccount.value!.id,
          billerReferenceNumber: inv.billerReferenceNumber,
          invoiceReference: inv.id,
          executionDate: new Date().toISOString().slice(0, 10),
          amount: { value: Number(inv.amount), currency: inv.currency || myAccount.value!.currency || 'VND' },
          note: `Pay invoice ${inv.id}`
        },
        `billpay-invoice-${inv.id}`
      )
    )
    const paymentId = String((resp.data as { paymentId?: string })?.paymentId ?? '')
    if (paymentId) {
      const finalState = await waitForPaymentFinal(paymentId)
      await load()
      if (finalState === 'POSTED') {
        if (selectedInvoiceId.value === paidInvoiceId) {
          selectedInvoiceId.value = pendingInvoices.value[0]?.id ?? ''
        }
        notify.push('success', 'Thanh toán thành công', 'Hóa đơn đã được thanh toán và cập nhật.')
      } else if (finalState === 'FAILED') {
        notify.push('error', 'Thanh toán thất bại', 'Giao dịch không hoàn tất. Vui lòng thử lại.')
      } else {
        notify.push('info', 'Đang xử lý', 'Thanh toán đã gửi. Vui lòng tải lại trang sau vài giây.')
      }
    } else {
      await load()
    }
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 409) {
      const detail = String((error.response?.data as { error?: { details?: string } })?.error?.details ?? '')
      if (detail.toLowerCase().includes('exceeds available balance')) {
        notify.push('warning', 'Số dư đã thay đổi', 'Số tiền cần nộp lớn hơn số dư hiện tại. Vui lòng tải lại trang.')
      }
    }
  }
}

function isOverdue(dueDate: string): boolean {
  return new Date(dueDate) < new Date()
}

function formatMoney(currency: string, value: number | string | Decimal): string {
  try {
    const val = new Decimal(value || 0).toNumber()
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND' }).format(val)
  } catch {
    return '0'
  }
}

onMounted(() => {
  isMounted = true
  load()
})

onUnmounted(() => {
  isMounted = false
})
</script>

<template>
  <div class="stack">
    <ConfirmModal 
      ref="confirmModal" 
      title="Xác nhận thanh toán hóa đơn" 
      @confirm="paySelected"
    >
      <div v-if="selectedInvoice">
        <p>Gói dịch vụ: <strong>{{ selectedInvoice.packageName }}</strong></p>
        <p>Số tiền: <strong>{{ formatMoney(selectedInvoice.currency, selectedInvoice.amount) }}</strong></p>
        <p>Mã hóa đơn: {{ selectedInvoice.id }}</p>
      </div>
    </ConfirmModal>
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Hóa đơn của tôi</h2>
          <p class="lead">
            Chọn một hóa đơn chờ thanh toán và thanh toán từ panel bên dưới.
            <span v-if="totalPending > 0" style="color: #f59e0b; font-weight:600;">
              {{ totalPending }} hóa đơn chưa thanh toán.
            </span>
          </p>
        </div>
        <button class="secondary" :disabled="running" @click="load">Làm mới</button>
      </div>

      <div v-if="myAccount" class="hint" style="margin-top:12px;">
        Số tài khoản: <span class="kbd">{{ myAccount.accountNumber }}</span>
        · Số dư khả dụng: <strong>{{ formatMoney(myAccount.currency, availableForPay) }}</strong>
        <span v-if="new Decimal(myAccount.totalHolds || 0).gt(0)" class="muted">
          (tổng {{ formatMoney(myAccount.currency, myAccount.balance || 0) }}, giữ {{ formatMoney(myAccount.currency, myAccount.totalHolds || 0) }})
        </span>
      </div>
      <p v-else class="hint" style="margin-top:12px; color:#b45309;">
        Bạn chưa có tài khoản thanh toán. Liên hệ ngân hàng để kích hoạt tài khoản sau khi KYC được duyệt.
      </p>
    </section>

    <section v-if="myAccount && pendingInvoices.length" class="card">
      <h3>Thanh toán hóa đơn</h3>
      <div class="form-grid">
        <label>Hóa đơn đang chọn
          <select v-model="selectedInvoiceId">
            <option v-for="inv in pendingInvoices" :key="inv.id" :value="inv.id">
              {{ inv.packageName }} — {{ inv.amount }} {{ inv.currency }} (hạn {{ inv.dueDate }})
            </option>
          </select>
        </label>
      </div>
      <p class="hint" v-if="selectedInvoice">
        Số tiền thanh toán: <strong>{{ formatMoney(selectedInvoice.currency, Number(selectedInvoice.amount)) }}</strong>
      </p>
      <div class="actions">
        <button :disabled="running || !canPay" @click="confirmPay">Thanh toán</button>
      </div>
    </section>

    <section class="card" v-if="invoices.length > 0">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Gói dịch vụ</th>
              <th>Số tiền</th>
              <th>Hạn</th>
              <th>Trạng thái</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="inv in invoices"
              :key="inv.id"
              :class="{ selected: inv.id === selectedInvoiceId }"
              @click="selectInvoice(inv)"
            >
              <td>{{ inv.packageName }}</td>
              <td>{{ formatMoney(inv.currency, inv.amount) }}</td>
              <td :class="{ overdue: isOverdue(inv.dueDate) }">{{ inv.dueDate }}</td>
              <td><span class="pill" :class="inv.status === 'PAID' ? 'success' : 'warning'">{{ inv.status }}</span></td>
              <td>
                <button
                  v-if="inv.status === 'PENDING' && myAccount"
                  class="small"
                  :disabled="running"
                  @click.stop="selectInvoice(inv); confirmPay()"
                >
                  Thanh toán
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <EmptyState v-else-if="!running" title="Chưa có hóa đơn" message="Đăng ký dịch vụ để nhận hóa đơn." />
  </div>
</template>

<style scoped>
tr.selected {
  background: rgba(59, 130, 246, 0.08);
}
.overdue {
  color: #dc2626;
  font-weight: 600;
}
</style>
