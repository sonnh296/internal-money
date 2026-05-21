<script setup lang="ts">
import { ref, watch } from 'vue'
import Decimal from 'decimal.js-light'

const props = withDefaults(
  defineProps<{
    modelValue: number | string
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
  'update:modelValue': [number | string]
}>()

const display = ref('')

function formatValue(value: number | string): string {
  try {
    const val = new Decimal(value || 0)
    if (!val.isFinite() || val.isZero()) return ''
    if (props.allowDecimals) {
      return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(val.toNumber())
    }
    return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(val.round().toNumber())
  } catch {
    return ''
  }
}

function parseValue(raw: string): string {
  const normalized = raw.trim().replace(/\s/g, '').replace(/\./g, '').replace(',', '.')
  if (!normalized) return '0'
  try {
    const parsed = new Decimal(normalized)
    if (!props.allowDecimals) {
      return parsed.round().toString()
    }
    return parsed.toString()
  } catch {
    return '0'
  }
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
  const amountStr = parseValue(raw)
  let valToEmit: string | number = amountStr
  if (typeof props.modelValue === 'number') {
    valToEmit = Number(amountStr)
  }
  display.value = new Decimal(amountStr || 0).greaterThan(0) ? formatValue(amountStr) : raw.replace(/[^\d.,]/g, '')
  emit('update:modelValue', valToEmit)
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
