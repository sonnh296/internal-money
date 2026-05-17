<script setup lang="ts">
defineProps<{
  title: string
  hint?: string
  icon?: string
}>()
</script>

<template>
  <div class="empty-state">
    <span class="icon">{{ icon ?? '◌' }}</span>
    <strong>{{ title }}</strong>
    <p v-if="hint">{{ hint }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 32px 16px;
  border: 1px dashed var(--surface-border, #1e293b);
  border-radius: 12px;
  text-align: center;
  color: var(--muted, #94a3b8);
}
.icon {
  font-size: 28px;
  color: var(--muted, #475569);
}
.empty-state strong { color: var(--text-strong, #e2e8f0); }
.empty-state p { margin: 0; font-size: 13px; }
</style>
