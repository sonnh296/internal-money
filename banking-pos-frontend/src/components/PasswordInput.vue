<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  modelValue: string
  placeholder?: string
  autocomplete?: string
  id?: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()

const visible = ref(false)
</script>

<template>
  <div class="password-field">
    <input
      :id="id"
      :value="modelValue"
      :type="visible ? 'text' : 'password'"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <button
      type="button"
      class="password-toggle"
      :aria-label="visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
      tabindex="-1"
      @click="visible = !visible"
    >
      {{ visible ? 'Ẩn' : 'Hiện' }}
    </button>
  </div>
</template>

<style scoped>
.password-field {
  display: flex;
  align-items: stretch;
  gap: 0;
}
.password-field input {
  flex: 1;
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}
.password-toggle {
  flex-shrink: 0;
  padding: 0 0.75rem;
  font-size: 0.8125rem;
  border: 1px solid var(--border, #cbd5e1);
  border-left: none;
  border-radius: 0 6px 6px 0;
  background: var(--surface-muted, #f1f5f9);
  color: var(--text-muted, #64748b);
  cursor: pointer;
}
.password-toggle:hover {
  background: var(--surface-hover, #e2e8f0);
}
</style>
