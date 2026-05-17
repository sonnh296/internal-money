<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: number
    placeholder?: string
    min?: number
    allowDecimals?: boolean
  }>(),
  {
    placeholder: '',
    min: 0,
    allowDecimals: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [number]
}>()

const display = ref('')

function formatValue(value: number): string {
  if (!Number.isFinite(value) || value === 0) return ''
  if (props.allowDecimals) {
    return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)
  }
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Math.round(value))
}

function parseValue(raw: string): number {
  const normalized = raw.trim().replace(/\s/g, '').replace(/\./g, '').replace(',', '.')
  if (!normalized) return 0
  const parsed = props.allowDecimals ? Number.parseFloat(normalized) : Number.parseInt(normalized, 10)
  return Number.isFinite(parsed) ? parsed : 0
}

watch(
  () => props.modelValue,
  (value) => {
    const formatted = formatValue(value)
    if (formatted !== display.value.replace(/\s/g, '')) {
      display.value = formatted
    }
  },
  { immediate: true }
)

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  let raw = target.value
  if (props.allowDecimals) {
    raw = raw.replace(/[^\d.,]/g, '')
  } else {
    raw = raw.replace(/[^\d]/g, '')
  }
  const amount = parseValue(raw)
  display.value = amount > 0 ? formatValue(amount) : raw.replace(/[^\d.,]/g, '')
  emit('update:modelValue', amount)
}
</script>

<template>
  <input
    :value="display"
    type="text"
    inputmode="decimal"
    :placeholder="placeholder"
    @input="onInput"
  />
</template>
