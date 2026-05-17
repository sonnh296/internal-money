<script setup lang="ts">
import { useNotifyStore } from '../stores/notify.store'

const notify = useNotifyStore()
</script>

<template>
  <div class="toast-stack" role="status" aria-live="polite">
    <div
      v-for="t in notify.toasts"
      :key="t.id"
      class="toast"
      :class="`toast-${t.kind}`"
      @click="notify.dismiss(t.id)"
    >
      <strong>{{ t.title }}</strong>
      <span v-if="t.message" class="toast-message">{{ t.message }}</span>
    </div>
  </div>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  display: grid;
  gap: 8px;
  max-width: 360px;
  pointer-events: none;
}

.toast {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  border-left: 3px solid #64748b;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  display: grid;
  gap: 4px;
  pointer-events: auto;
  cursor: pointer;
  animation: toast-in 0.2s ease-out;
}

.toast-success {
  border-left-color: #22c55e;
}
.toast-error {
  border-left-color: #ef4444;
}
.toast-info {
  border-left-color: #38bdf8;
}

.toast-message {
  font-size: 12px;
  color: #cbd5e1;
}

@keyframes toast-in {
  from { transform: translateX(20px); opacity: 0; }
  to   { transform: translateX(0); opacity: 1; }
}
</style>
