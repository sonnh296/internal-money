<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const isOpen = ref(false)

function open() {
  isOpen.value = true
}

function close() {
  isOpen.value = false
}

function onConfirm() {
  emit('confirm')
  close()
}

function onCancel() {
  emit('cancel')
  close()
}

defineExpose({ open, close })
</script>

<template>
  <div v-if="isOpen" class="modal-backdrop" @click="onCancel">
    <div class="modal" @click.stop>
      <h3>{{ title }}</h3>
      <p v-if="message" class="modal-message">{{ message }}</p>
      <slot></slot>
      <div class="actions" style="margin-top: 24px; justify-content: flex-end;">
        <button class="secondary" @click="onCancel">{{ cancelText || 'Hủy' }}</button>
        <button @click="onConfirm">{{ confirmText || 'Xác nhận' }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}
.modal {
  background: var(--bg-surface);
  border-radius: 12px;
  padding: 24px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
}
.modal-message {
  margin-top: 12px;
  color: var(--text-secondary);
}
</style>
