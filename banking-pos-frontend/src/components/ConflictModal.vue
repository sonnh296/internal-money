<script setup lang="ts">
import { useNotifyStore } from '../stores/notify.store'
import { computed } from 'vue'

const notify = useNotifyStore()
const visible = computed(() => notify.conflictDetected)

function reload() {
  notify.clearConflict()
  window.location.reload()
}

function dismiss() {
  notify.clearConflict()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="conflict-overlay" role="dialog" aria-modal="true">
      <div class="conflict-dialog">
        <div class="conflict-icon">⚠</div>
        <h2>Xung đột dữ liệu</h2>
        <p>
          Dữ liệu đã bị thay đổi từ phiên hoặc tab khác.<br />
          Vui lòng tải lại trang để lấy phiên bản mới nhất trước khi tiếp tục.
        </p>
        <div class="conflict-actions">
          <button @click="reload">Tải lại trang</button>
          <button class="ghost" @click="dismiss">Bỏ qua</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.conflict-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.conflict-dialog {
  background: var(--surface, #fff);
  border: 2px solid #f59e0b;
  border-radius: 12px;
  padding: 32px;
  max-width: 400px;
  width: 90%;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.conflict-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
h2 {
  margin: 0 0 12px;
  font-size: 1.25rem;
  color: #f59e0b;
}
p {
  margin: 0 0 24px;
  line-height: 1.6;
  color: var(--text, #374151);
  font-size: 0.95rem;
}
.conflict-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
