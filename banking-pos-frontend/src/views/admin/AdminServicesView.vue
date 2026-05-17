<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createServicePackageApi,
  listServicePackagesApi,
  toggleServicePackageApi,
  deleteServicePackageApi,
  createInvoiceApi,
  listAllInvoicesApi,
  markInvoicePaidApi,
  type ServicePackagePayload,
  type CreateInvoicePayload,
  type BulkInvoiceResponse
} from '../../api/biller.api'
import { useApiAction } from '../../composables/useApiAction'
import EmptyState from '../../components/EmptyState.vue'

const { run, running } = useApiAction()

type Tab = 'services' | 'invoices'
const activeTab = ref<Tab>('services')

interface ServicePackage {
  id: string
  name: string
  category: string
  referenceNumber: string
  monthlyAmount: number
  currency: string
  description: string
  status: string
  createdAt: string
}

interface Invoice {
  id: string
  subscriptionId: string
  customerId: string
  packageId: string
  packageName: string
  billerReferenceNumber: string
  amount: number
  currency: string
  dueDate: string
  status: string
  createdAt: string
}

const packages = ref<ServicePackage[]>([])
const invoices = ref<Invoice[]>([])

const pkgForm = reactive<ServicePackagePayload>({
  name: '',
  category: 'Electricity',
  referenceNumber: '',
  monthlyAmount: 0,
  currency: 'VND',
  description: ''
})

const invForm = reactive<CreateInvoicePayload>({
  packageId: '',
  dueDate: new Date(Date.now() + 30 * 86400_000).toISOString().slice(0, 10)
})
const selectedPackage = computed(() => packages.value.find((p) => p.id === invForm.packageId))
const activePackages = computed(() => packages.value.filter((p) => p.status === 'ACTIVE'))

function genRefNum() {
  pkgForm.referenceNumber = `SVC-${Date.now().toString(36).toUpperCase()}`
}

async function loadPackages() {
  const resp = await run('Lấy dịch vụ', () => listServicePackagesApi(true), { silent: true })
  packages.value = Array.isArray(resp.data) ? (resp.data as ServicePackage[]) : []
}

async function loadInvoices() {
  const resp = await run('Lấy hóa đơn', () => listAllInvoicesApi(), { silent: true })
  invoices.value = Array.isArray(resp.data) ? (resp.data as Invoice[]) : []
}

async function createPkg() {
  await run('Tạo gói dịch vụ', () => createServicePackageApi(pkgForm), {
    successToast: `Đã tạo gói: ${pkgForm.name}`
  })
  pkgForm.name = ''
  pkgForm.referenceNumber = ''
  pkgForm.monthlyAmount = 0
  pkgForm.description = ''
  await loadPackages()
}

async function togglePkg(pkg: ServicePackage) {
  await run('Đổi trạng thái dịch vụ', () => toggleServicePackageApi(pkg.id), {
    successToast: `Đã đổi trạng thái ${pkg.name}`
  })
  await loadPackages()
}

async function deletePkg(pkg: ServicePackage) {
  if (!confirm(`Xóa gói "${pkg.name}"?`)) return
  await run('Xóa gói dịch vụ', () => deleteServicePackageApi(pkg.id), {
    successToast: 'Đã xóa gói dịch vụ'
  })
  await loadPackages()
}

async function createInvoice() {
  if (!invForm.packageId || !invForm.dueDate) return
  const resp = await run('Phát hành hóa đơn', () => createInvoiceApi({ ...invForm }), { silent: true })
  const bulk = resp.data as BulkInvoiceResponse
  await run('Phát hành hóa đơn', () => Promise.resolve(resp), {
    successToast: `Đã tạo ${bulk.createdCount} hóa đơn, bỏ qua ${bulk.skippedCount} trùng kỳ.`
  })
  await loadInvoices()
}

async function markPaid(inv: Invoice) {
  await run('Đánh dấu đã thanh toán', () => markInvoicePaidApi(inv.id), {
    successToast: `Hóa đơn ${inv.id.slice(0,8)} đã được đánh dấu PAID`
  })
  await loadInvoices()
}

async function init() {
  await loadPackages()
  if (!invForm.packageId && activePackages.value[0]) {
    invForm.packageId = activePackages.value[0].id
  }
  await loadInvoices()
}

onMounted(init)
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Dịch vụ & Hóa đơn</h2>
          <p class="lead">
            Tạo <strong>gói dịch vụ</strong> (điện, internet, v.v.) rồi tạo <strong>hóa đơn</strong>
            cho khách hàng đã đăng ký. Khách hàng thấy hóa đơn ở trang
            <span class="kbd">Hóa đơn của tôi</span> và thanh toán từ đó.
          </p>
        </div>
        <button class="secondary" :disabled="running" @click="init">Làm mới</button>
      </div>
    </section>

    <!-- Tabs -->
    <div class="tab-bar">
      <button :class="{ active: activeTab === 'services' }" @click="activeTab = 'services'">
        Gói dịch vụ ({{ packages.length }})
      </button>
      <button :class="{ active: activeTab === 'invoices' }" @click="activeTab = 'invoices'">
        Hóa đơn ({{ invoices.length }})
      </button>
    </div>

    <!-- Services Tab -->
    <template v-if="activeTab === 'services'">
      <section class="card">
        <h3>Tạo gói dịch vụ mới</h3>
        <div class="form-grid">
          <label>Tên dịch vụ <input v-model="pkgForm.name" placeholder="Điện EVN" /></label>
          <label>Danh mục
            <select v-model="pkgForm.category">
              <option>Electricity</option>
              <option>Internet</option>
              <option>Water</option>
              <option>Gas</option>
              <option>Insurance</option>
              <option>Telecom</option>
              <option>Other</option>
            </select>
          </label>
          <label>Mã tham chiếu
            <div class="inline-row">
              <input v-model="pkgForm.referenceNumber" placeholder="SVC-XXXX" />
              <button class="ghost small" @click="genRefNum">Tạo tự động</button>
            </div>
          </label>
          <label>Phí hàng tháng (VND) <input v-model.number="pkgForm.monthlyAmount" type="number" min="0" step="0.01" /></label>
          <label>Tiền tệ <select v-model="pkgForm.currency"><option>VND</option><option>USD</option><option>CAD</option></select></label>
          <label style="grid-column:1/-1;">Mô tả <input v-model="pkgForm.description" placeholder="Mô tả ngắn..." /></label>
        </div>
        <div class="actions">
          <button :disabled="running || !pkgForm.name || !pkgForm.referenceNumber" @click="createPkg">
            Tạo gói dịch vụ
          </button>
        </div>
      </section>

      <section class="card" v-if="packages.length > 0">
        <h3>Tất cả gói dịch vụ</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Tên</th><th>Danh mục</th><th>Mã tham chiếu</th><th>Phí/tháng</th><th>Trạng thái</th><th>Hành động</th></tr>
            </thead>
            <tbody>
              <tr v-for="pkg in packages" :key="pkg.id">
                <td>{{ pkg.name }}</td>
                <td>{{ pkg.category }}</td>
                <td><span class="kbd">{{ pkg.referenceNumber }}</span></td>
                <td>{{ pkg.monthlyAmount }} {{ pkg.currency }}</td>
                <td>
                  <span class="pill" :class="pkg.status === 'ACTIVE' ? 'success' : 'danger'">
                    {{ pkg.status }}
                  </span>
                </td>
                <td>
                  <div class="inline-row">
                    <button class="ghost small" :disabled="running" @click="togglePkg(pkg)">
                      {{ pkg.status === 'ACTIVE' ? 'Tắt' : 'Bật' }}
                    </button>
                    <button class="ghost small danger" :disabled="running" @click="deletePkg(pkg)">Xóa</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <EmptyState v-else-if="!running" icon="☷" title="Chưa có gói dịch vụ" hint="Tạo gói đầu tiên bên trên." />
    </template>

    <!-- Invoices Tab -->
    <template v-if="activeTab === 'invoices'">
      <section class="card">
        <h3>Phát hành hóa đơn</h3>
        <div class="form-grid">
          <label>Gói dịch vụ
            <select v-model="invForm.packageId">
              <option v-for="pkg in activePackages" :key="pkg.id" :value="pkg.id">
                {{ pkg.name }} ({{ pkg.referenceNumber }})
              </option>
            </select>
          </label>
          <label>Hạn thanh toán <input v-model="invForm.dueDate" type="date" :min="new Date().toISOString().slice(0, 10)" /></label>
          <label>Số tiền tùy chỉnh (tùy chọn)
            <input v-model.number="invForm.amount" type="number" min="0" step="0.01" placeholder="Để trống = tính theo kỳ" />
          </label>
        </div>
        <div class="hint" v-if="selectedPackage">
          Phát hành cho mọi khách hàng đang đăng ký ACTIVE gói <strong>{{ selectedPackage.name }}</strong>.
          Phí tham chiếu: <strong>{{ selectedPackage.monthlyAmount }} {{ selectedPackage.currency }}</strong>/tháng.
        </div>
        <div class="actions">
          <button :disabled="running || !invForm.packageId || !invForm.dueDate" @click="createInvoice">
            Phát hành hóa đơn hàng loạt
          </button>
        </div>
      </section>

      <section class="card" v-if="invoices.length > 0">
        <h3>Tất cả hóa đơn ({{ invoices.length }})</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Gói dịch vụ</th><th>Khách hàng</th><th>Số tiền</th><th>Hạn</th><th>Trạng thái</th><th>Hành động</th></tr>
            </thead>
            <tbody>
              <tr v-for="inv in invoices" :key="inv.id">
                <td>{{ inv.packageName || inv.billerReferenceNumber }}</td>
                <td><span class="kbd small">{{ inv.customerId.slice(0,12) }}…</span></td>
                <td>{{ inv.amount }} {{ inv.currency }}</td>
                <td>{{ inv.dueDate }}</td>
                <td>
                  <span class="pill" :class="{
                    success: inv.status === 'PAID',
                    warning: inv.status === 'PENDING',
                    danger: inv.status === 'OVERDUE'
                  }">{{ inv.status }}</span>
                </td>
                <td>
                  <button v-if="inv.status === 'PENDING'" class="ghost small" :disabled="running" @click="markPaid(inv)">
                    Đánh dấu Đã TT
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <EmptyState v-else-if="!running" icon="⊟" title="Chưa có hóa đơn nào" hint="Tạo hóa đơn sau khi khách hàng đăng ký dịch vụ." />
    </template>
  </div>
</template>

<style scoped>
.tab-bar {
  display: flex;
  gap: 4px;
  border-bottom: 2px solid var(--border, #e5e7eb);
  padding-bottom: 0;
}
.tab-bar button {
  background: none;
  border: none;
  border-bottom: 3px solid transparent;
  padding: 8px 20px;
  cursor: pointer;
  font-weight: 500;
  color: var(--text-muted, #6b7280);
  margin-bottom: -2px;
}
.tab-bar button.active {
  border-bottom-color: var(--accent, #6366f1);
  color: var(--text-strong, #111827);
}
</style>
