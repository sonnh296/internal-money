<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  cancelSubscriptionApi,
  listMySubscriptionsApi,
  listServicePackagesApi,
  subscribeApi
} from '../../api/biller.api'
import { useApiAction } from '../../composables/useApiAction'
import EmptyState from '../../components/EmptyState.vue'

const { run, running } = useApiAction()

interface ServicePackage {
  id: string
  name: string
  category: string
  referenceNumber: string
  monthlyAmount: number
  currency: string
  description: string
  status: string
}

interface Subscription {
  id: string
  packageId: string
  packageName: string
  packageCategory: string
  packageReferenceNumber: string
  status: string
  createdAt: string
}

const packages = ref<ServicePackage[]>([])
const subscriptions = ref<Subscription[]>([])
const subscribedPackageIds = ref(new Set<string>())

async function load() {
  const [pkgResp, subResp] = await Promise.all([
    run('Lấy dịch vụ', () => listServicePackagesApi(false), { silent: true }),
    run('Lấy đăng ký của tôi', () => listMySubscriptionsApi(), { silent: true })
  ])
  packages.value = Array.isArray(pkgResp.data) ? (pkgResp.data as ServicePackage[]) : []
  subscriptions.value = Array.isArray(subResp.data) ? (subResp.data as Subscription[]) : []
  subscribedPackageIds.value = new Set(subscriptions.value.filter((s) => s.status === 'ACTIVE').map((s) => s.packageId))
}

async function subscribe(pkg: ServicePackage) {
  await run(`Đăng ký ${pkg.name}`, () => subscribeApi(pkg.id), {
    successToast: `Đã đăng ký ${pkg.name}`
  })
  await load()
}

async function cancel(sub: Subscription) {
  if (!confirm(`Hủy đăng ký "${sub.packageName}"?`)) return
  await run('Hủy đăng ký', () => cancelSubscriptionApi(sub.id), {
    successToast: `Đã hủy ${sub.packageName}`
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
          <h2>Dịch vụ</h2>
          <p class="lead">
            Đăng ký các dịch vụ tiện ích (điện, nước, internet...). Sau khi đăng ký, hóa đơn sẽ xuất hiện
            trong mục <strong>Hóa đơn của tôi</strong> để bạn thanh toán.
          </p>
        </div>
        <button class="secondary" :disabled="running" @click="load">Làm mới</button>
      </div>
    </section>

    <!-- Available Services -->
    <section class="card" v-if="packages.length > 0">
      <h3>Dịch vụ đang hoạt động</h3>
      <div class="service-grid">
        <div v-for="pkg in packages" :key="pkg.id" class="service-card"
          :class="{ subscribed: subscribedPackageIds.has(pkg.id) }">
          <div class="service-header">
            <div>
              <span class="service-icon">{{ pkg.category === 'Electricity' ? '⚡' : pkg.category === 'Internet' ? '🌐' : pkg.category === 'Water' ? '💧' : '☷' }}</span>
              <strong>{{ pkg.name }}</strong>
            </div>
            <span class="pill" :class="subscribedPackageIds.has(pkg.id) ? 'success' : ''">
              {{ subscribedPackageIds.has(pkg.id) ? 'Đã đăng ký' : pkg.category }}
            </span>
          </div>
          <p v-if="pkg.description" class="muted small-text">{{ pkg.description }}</p>
          <div class="service-footer">
            <span class="price">{{ pkg.monthlyAmount }} {{ pkg.currency }}/tháng</span>
            <button v-if="!subscribedPackageIds.has(pkg.id)"
              :disabled="running" @click="subscribe(pkg)">
              Đăng ký
            </button>
            <span v-else class="muted">✓ Đã đăng ký</span>
          </div>
        </div>
      </div>
    </section>

    <EmptyState v-else-if="!running" icon="☷" title="Chưa có dịch vụ nào" hint="Admin chưa thêm dịch vụ nào. Vui lòng liên hệ hỗ trợ." />

    <!-- My Subscriptions -->
    <section class="card" v-if="subscriptions.length > 0">
      <h3>Đăng ký của tôi ({{ subscriptions.length }})</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>Dịch vụ</th><th>Danh mục</th><th>Mã tham chiếu</th><th>Trạng thái</th><th>Ngày đăng ký</th><th>Hành động</th></tr>
          </thead>
          <tbody>
            <tr v-for="sub in subscriptions" :key="sub.id">
              <td>{{ sub.packageName }}</td>
              <td>{{ sub.packageCategory }}</td>
              <td><span class="kbd small">{{ sub.packageReferenceNumber }}</span></td>
              <td>
                <span class="pill" :class="sub.status === 'ACTIVE' ? 'success' : 'danger'">{{ sub.status }}</span>
              </td>
              <td class="muted">{{ sub.createdAt ? new Date(sub.createdAt).toLocaleDateString('vi') : '—' }}</td>
              <td>
                <button v-if="sub.status === 'ACTIVE'" class="ghost small" :disabled="running" @click="cancel(sub)">Hủy</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.service-card {
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.service-card.subscribed {
  border-color: var(--success, #22c55e);
  background: rgba(34, 197, 94, 0.04);
}
.service-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.service-icon {
  margin-right: 6px;
  font-size: 18px;
}
.service-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.price {
  font-weight: 700;
  font-size: 1rem;
  color: var(--accent, #6366f1);
}
.small-text {
  font-size: 0.82rem;
}
</style>
