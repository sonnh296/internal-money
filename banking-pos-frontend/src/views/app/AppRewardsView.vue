<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getMyRewardPointsApi } from '../../api/payment.api'
import { useApiAction } from '../../composables/useApiAction'
import type { RewardPointsResponse } from '../../types/api.types'

const { run, running } = useApiAction()
const rewards = ref<RewardPointsResponse | null>(null)

function pointToCurrency(points: number): string {
  return (points / 10).toFixed(2)
}

async function loadRewards() {
  const resp = await run('Lấy điểm thưởng', () => getMyRewardPointsApi(), { silent: true })
  rewards.value = resp.data as RewardPointsResponse
}

onMounted(loadRewards)
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="row-between">
        <div>
          <h2>Điểm thưởng của bạn</h2>
          <p class="lead">
            Điểm thưởng có thể dùng để giảm tiền khi thanh toán hóa đơn.
          </p>
        </div>
        <button class="secondary small" :disabled="running" @click="loadRewards">↻ Làm mới</button>
      </div>
    </section>

    <section class="card" v-if="rewards">
      <h2>Số dư hiện tại</h2>
      <p class="lead">
        <strong style="font-size: 24px;">{{ rewards.points }}</strong> điểm
      </p>
      <p class="lead">
        Giá trị quy đổi ước tính:
        <span class="kbd">{{ pointToCurrency(rewards.points) }} VND</span>
      </p>
      <p class="muted">
        Điểm được lưu theo hồ sơ khách hàng của bạn trong hệ thống thanh toán.
      </p>
      <p class="hint">
        Quy ước hiện tại: <span class="kbd">10 điểm = 1 VND</span>.
      </p>
    </section>
  </div>
</template>
