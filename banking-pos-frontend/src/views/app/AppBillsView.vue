<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'
import { listMyInvoicesApi } from '../../api/biller.api'
import { getMyAccountApi } from '../../api/account.api'
import { createBillPayApi, getMyRewardPointsApi, getPaymentApi } from '../../api/payment.api'
import { useApiAction } from '../../composables/useApiAction'
import { useNotifyStore } from '../../stores/notify.store'
import EmptyState from '../../components/EmptyState.vue'
import type { AccountResponse, RewardPointsResponse } from '../../types/api.types'

const notify = useNotifyStore()
const { run, running } = useApiAction()

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
const rewards = ref<RewardPointsResponse | null>(null)
const selectedInvoiceId = ref('')
const pointsToRedeem = ref(0)

const pendingInvoices = computed(() => invoices.value.filter((i) => i.status === 'PENDING'))
const selectedInvoice = computed(() => pendingInvoices.value.find((i) => i.id === selectedInvoiceId.value) ?? null)
const totalPending = computed(() => pendingInvoices.value.length)
const pointsValue = computed(() => (rewards.value?.points ?? 0) / 10)

const maxRedeemablePoints = computed(() => {
  if (!selectedInvoice.value) return 0
  const inv = selectedInvoice.value
  const pointsByBalance = rewards.value?.points ?? 0
  const pointsByAmount = Math.max(0, Math.floor((Number(inv.amount) - 0.01) * 10))
  return Math.min(pointsByBalance, pointsByAmount)
})

const discountFromPoints = computed(() => Math.min(pointsToRedeem.value / 10, Number(selectedInvoice.value?.amount ?? 0)))
const amountToPay = computed(() => {
  if (!selectedInvoice.value) return 0
  return Math.max(0.01, Number(selectedInvoice.value.amount) - discountFromPoints.value)
})

const availableForPay = computed(() => {
  if (!myAccount.value) return 0
  const avail = myAccount.value.availableBalance
  if (avail != null && Number.isFinite(Number(avail))) return Number(avail)
  const holds = Number(myAccount.value.totalHolds ?? 0)
  return Number(myAccount.value.balance ?? 0) - holds
})

const canPay = computed(() => {
  if (!myAccount.value || !selectedInvoice.value) return false
  if (pointsToRedeem.value < 0 || pointsToRedeem.value > maxRedeemablePoints.value) return false
  return amountToPay.value <= availableForPay.value
})

async function load() {
  const [invoiceResp, accountResp, rewardResp] = await Promise.all([
    run('Lấy hóa đơn', () => listMyInvoicesApi(), { silent: true }),
    run('Lấy tài khoản', () => getMyAccountApi(), { silent: true }).catch(() => ({ data: null })),
    run('Lấy điểm thưởng', () => getMyRewardPointsApi(), { silent: true })
  ])
  invoices.value = Array.isArray(invoiceResp.data) ? (invoiceResp.data as Invoice[]) : []
  myAccount.value = accountResp.data as AccountResponse | null
  rewards.value = rewardResp.data as RewardPointsResponse
  if (!selectedInvoiceId.value && pendingInvoices.value[0]) {
    selectedInvoiceId.value = pendingInvoices.value[0].id
  }
}

function selectInvoice(inv: Invoice) {
  selectedInvoiceId.value = inv.id
  pointsToRedeem.value = 0
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

async function paySelected() {
  if (!myAccount.value || !selectedInvoice.value) {
    notify.push('error', 'Không thể thanh toán', 'Vui lòng chọn hóa đơn và đảm bảo tài khoản đã kích hoạt.')
    return
  }
  const inv = selectedInvoice.value
  const cappedPoints = Math.min(Math.max(0, Math.floor(pointsToRedeem.value)), maxRedeemablePoints.value)
  pointsToRedeem.value = cappedPoints

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
          note: `Pay invoice ${inv.id}`,
          pointsToRedeem: cappedPoints > 0 ? cappedPoints : undefined
        },
        `billpay-invoice-${inv.id}-${Date.now()}`
      )
    )
    pointsToRedeem.value = 0
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

function formatMoney(currency: string, value: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND' }).format(value)
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Hóa đơn của tôi</h2>
          <p class="lead">
            Chọn một hóa đơn chờ thanh toán, nhập điểm thưởng (nếu có) và thanh toán từ panel bên dưới.
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
        <span v-if="Number(myAccount.totalHolds ?? 0) > 0" class="muted">
          (tổng {{ formatMoney(myAccount.currency, Number(myAccount.balance)) }}, giữ {{ formatMoney(myAccount.currency, Number(myAccount.totalHolds)) }})
        </span>
        · Điểm thưởng: <strong>{{ rewards?.points ?? 0 }}</strong> (≈ {{ formatMoney(myAccount.currency, pointsValue) }})
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
        <label>Điểm thưởng sử dụng
          <input v-model.number="pointsToRedeem" type="number" min="0" :max="maxRedeemablePoints" step="1" />
        </label>
      </div>
      <p class="hint" v-if="selectedInvoice">
        Giảm từ điểm: <strong>{{ formatMoney(selectedInvoice.currency, discountFromPoints) }}</strong>
        · Số tiền thực trả: <strong>{{ formatMoney(selectedInvoice.currency, amountToPay) }}</strong>
        · Tối đa {{ maxRedeemablePoints }} điểm cho hóa đơn này.
      </p>
      <div class="actions">
        <button :disabled="running || !canPay" @click="paySelected">Thanh toán</button>
      </div>
    </section>

    <section class="card" v-if="invoices.length > 0">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th></th>
              <th>Dịch vụ</th>
              <th>Mã tham chiếu</th>
              <th>Số tiền</th>
              <th>Hạn thanh toán</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="inv in invoices"
              :key="inv.id"
              :class="{ 'row-selected': inv.id === selectedInvoiceId && inv.status === 'PENDING' }"
              @click="inv.status === 'PENDING' ? selectInvoice(inv) : undefined"
            >
              <td>
                <input
                  v-if="inv.status === 'PENDING'"
                  type="radio"
                  :checked="inv.id === selectedInvoiceId"
                  @change="selectInvoice(inv)"
                />
              </td>
              <td>{{ inv.packageName || '—' }}</td>
              <td><span class="kbd small">{{ inv.billerReferenceNumber }}</span></td>
              <td><strong>{{ inv.amount }} {{ inv.currency }}</strong></td>
              <td :style="isOverdue(inv.dueDate) && inv.status === 'PENDING' ? 'color: #ef4444' : ''">
                {{ inv.dueDate }}
                <span v-if="isOverdue(inv.dueDate) && inv.status === 'PENDING'" class="pill danger" style="margin-left:4px;">Quá hạn</span>
              </td>
              <td>
                <span class="pill" :class="{
                  success: inv.status === 'PAID',
                  warning: inv.status === 'PENDING' && !isOverdue(inv.dueDate),
                  danger: inv.status === 'PENDING' && isOverdue(inv.dueDate)
                }">{{ inv.status }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <EmptyState
      v-else-if="!running && myAccount"
      icon="⊟"
      title="Chưa có hóa đơn nào"
      hint="Đăng ký dịch vụ để nhận hóa đơn khi admin tạo. Xem trang Dịch vụ để đăng ký."
    />
  </div>
</template>

<style scoped>
.row-selected td {
  background: rgba(99, 102, 241, 0.08);
}
tbody tr {
  cursor: default;
}
tbody tr:has(input[type='radio']) {
  cursor: pointer;
}
</style>
