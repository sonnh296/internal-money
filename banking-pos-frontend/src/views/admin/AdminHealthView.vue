<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useApiAction } from '../../composables/useApiAction'
import { rawClient } from '../../api/httpClient'
import { env } from '../../config/env'
import { jwksApi, openidConfigApi, testPublicApi, testSecureApi } from '../../api/auth.api'
import { customerHealthApi } from '../../api/customer.api'

const { run, running } = useApiAction()

const services = [
  { id: 'auth', label: 'AuthUser', baseUrl: env.authBaseUrl, healthPath: '/api/v1/test/public' },
  { id: 'customer', label: 'CustomerService', baseUrl: env.customerBaseUrl, healthPath: '/api/v1/customers/health' },
  { id: 'account', label: 'AccountService', baseUrl: env.accountBaseUrl, healthPath: '/api/v1/health' },
  { id: 'payment', label: 'PaymentOrchestrator', baseUrl: env.paymentBaseUrl, healthPath: '/api/v1/health' },
  { id: 'biller', label: 'BillerService', baseUrl: env.billerBaseUrl, healthPath: '/api/v1/health' }
]

const status = reactive<Record<string, { ok: boolean; code: number; message: string; ms: number } | null>>({})
for (const s of services) status[s.id] = null

async function ping(id: string, baseUrl: string, healthPath: string) {
  const start = Date.now()
  try {
    const client = rawClient(baseUrl)
    const resp = await client.get(healthPath, { timeout: 4000 })
    status[id] = {
      ok: resp.status >= 200 && resp.status < 400,
      code: resp.status,
      message: 'Healthy',
      ms: Date.now() - start
    }
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; statusText?: string }; message?: string }
    status[id] = {
      ok: false,
      code: e.response?.status ?? 0,
      message: e.response?.statusText ?? e.message ?? 'unreachable',
      ms: Date.now() - start
    }
  }
}

async function pingAll() {
  await Promise.all(services.map((s) => ping(s.id, s.baseUrl, s.healthPath)))
}

async function runChecks() {
  await run('AuthUser /test/public', () => testPublicApi())
  await run('AuthUser /test/secure', () => testSecureApi())
  await run('AuthUser JWKS', () => jwksApi())
  await run('AuthUser OIDC discovery', () => openidConfigApi())
  await run('CustomerService /customers/health', () => customerHealthApi())
}

onMounted(pingAll)
</script>

<template>
  <div class="stack">
    <section class="card">
      <h2>System Health</h2>
      <p class="lead">
        Kiểm tra endpoint health công khai của từng service (không cần JWT) và một số endpoint smoke thường dùng.
      </p>
      <div class="actions">
        <button :disabled="running" @click="pingAll">↻ Ping tất cả health endpoints</button>
        <button :disabled="running" class="secondary" @click="runChecks">Chạy smoke endpoints</button>
      </div>
    </section>

    <section class="card">
      <div class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>Service</th>
              <th>Base URL</th>
              <th>HTTP</th>
              <th>Latency</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in services" :key="s.id">
              <td><strong>{{ s.label }}</strong></td>
              <td><span class="kbd">{{ s.baseUrl }}</span></td>
              <td>
                <span v-if="status[s.id]" class="pill" :class="{
                  success: status[s.id]?.ok,
                  danger: status[s.id] && !status[s.id]?.ok
                }">{{ status[s.id]?.code || 'ERR' }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td>{{ status[s.id]?.ms != null ? status[s.id]?.ms + ' ms' : '—' }}</td>
              <td class="muted" style="font-size:12px;">{{ status[s.id]?.message ?? '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

  </div>
</template>
