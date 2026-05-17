<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  hint?: string
  trend?: 'up' | 'down' | 'flat'
}>()
</script>

<template>
  <div class="stat-tile">
    <p class="stat-label">{{ label }}</p>
    <p class="stat-value">{{ value }}</p>
    <p v-if="hint" class="stat-hint" :class="trend">{{ hint }}</p>
  </div>
</template>

<style scoped>
.stat-tile {
  background: var(--surface, #0f172a);
  border: 1px solid var(--surface-border, #1e293b);
  border-radius: 12px;
  padding: 16px;
  display: grid;
  gap: 4px;
}
.stat-label {
  margin: 0;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--muted, #94a3b8);
}
.stat-value {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-strong, #f1f5f9);
}
.stat-hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted, #94a3b8);
}
.stat-hint.up { color: #4ade80; }
.stat-hint.down { color: #f87171; }
</style>
