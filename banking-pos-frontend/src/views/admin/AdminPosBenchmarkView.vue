<script setup lang="ts">
import { ref } from 'vue'
import { rawClient } from '../../api/httpClient'
import { env } from '../../config/env'

interface BenchmarkResult {
  mode: string
  concurrency: number
  totalRequests: number
  successCount: number
  failCount: number
  durationMs: number
  throughput: number
  avgLatencyMs: number
}

const running = ref(false)
const results = ref<BenchmarkResult[]>([])
const errorText = ref('')

async function runBenchmark(concurrency: number) {
  running.value = true
  results.value = []
  errorText.value = ''
  const client = rawClient(env.posBaseUrl)
  const modes = ['platform', 'virtual']
  const next: BenchmarkResult[] = []
  for (const mode of modes) {
    try {
      const resp = await client.get('/api/benchmark/run', {
        params: { concurrency, mode },
        timeout: 120_000
      })
      const data = resp.data as Record<string, unknown>
      next.push({
        mode,
        concurrency: Number(data.concurrency ?? concurrency),
        totalRequests: Number(data.totalRequests ?? concurrency),
        successCount: Number(data.successCount ?? 0),
        failCount: Number(data.failCount ?? 0),
        durationMs: Number(data.durationMs ?? 0),
        throughput: Number(data.throughput ?? 0),
        avgLatencyMs: Number(data.avgLatencyMs ?? 0)
      })
    } catch (error: unknown) {
      const e = error as { message?: string }
      errorText.value += `${mode}: ${e.message ?? 'Lỗi benchmark'}\n`
    }
  }
  results.value = next
  running.value = false
}
</script>

<template>
  <div class="stack">
    <section class="card">
      <h2>POS Benchmark</h2>
      <p class="lead">Đo hiệu năng xử lý điểm thưởng POS giữa platform thread và virtual thread.</p>
      <div class="actions">
        <button :disabled="running" @click="runBenchmark(50)">Run 50 concurrent</button>
        <button class="secondary" :disabled="running" @click="runBenchmark(100)">Run 100 concurrent</button>
        <button class="ghost" :disabled="running" @click="runBenchmark(200)">Run 200 concurrent</button>
      </div>
      <p class="hint" v-if="running">Benchmark đang chạy, vui lòng chờ...</p>
      <p v-if="errorText" class="hint" style="color:#ef4444; white-space: pre-line;">{{ errorText }}</p>
    </section>

    <section class="card" v-if="results.length">
      <div class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>Mode</th>
              <th>Concurrent</th>
              <th>Success</th>
              <th>Fail</th>
              <th>Duration</th>
              <th>Throughput</th>
              <th>Avg Latency</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in results" :key="item.mode">
              <td>{{ item.mode }}</td>
              <td>{{ item.concurrency }}</td>
              <td>{{ item.successCount }}</td>
              <td>{{ item.failCount }}</td>
              <td>{{ item.durationMs }} ms</td>
              <td>{{ item.throughput.toFixed(1) }} req/s</td>
              <td>{{ item.avgLatencyMs.toFixed(1) }} ms</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
