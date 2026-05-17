<script setup lang="ts">
import { computed, ref } from 'vue'
import { useNotifyStore } from '../stores/notify.store'

const notify = useNotifyStore()
const open = ref(false)
const selectedId = ref<number | null>(null)

const selected = computed(() => {
  if (selectedId.value == null) return notify.recentCalls[0]
  return notify.recentCalls.find((c) => c.id === selectedId.value) ?? notify.recentCalls[0]
})

function toggle() {
  open.value = !open.value
  if (open.value && selectedId.value == null && notify.recentCalls.length > 0) {
    selectedId.value = notify.recentCalls[0].id
  }
}

function pickHeaders(headers: unknown): string {
  try {
    return JSON.stringify(headers, null, 2)
  } catch {
    return String(headers)
  }
}
</script>

<template>
  <div class="raw-drawer" :class="{ 'is-open': open }">
    <button class="raw-toggle" @click="toggle">
      <span>{{ open ? 'Đóng' : 'Mở' }} Raw Responses</span>
      <span class="badge">{{ notify.recentCalls.length }}</span>
    </button>

    <div v-if="open" class="raw-body">
      <aside class="raw-list">
        <header class="raw-list-header">
          <strong>Lịch sử gọi gần đây</strong>
          <button class="ghost-sm" @click="notify.clearCalls()">Xóa</button>
        </header>
        <ul>
          <li
            v-for="c in notify.recentCalls"
            :key="c.id"
            :class="{ active: c.id === selected?.id, fail: !c.ok }"
            @click="selectedId = c.id"
          >
            <span class="status-pill" :class="c.ok ? 'ok' : 'err'">{{ c.status.split(' ')[0] }}</span>
            <span class="title">{{ c.title }}</span>
            <span class="duration">{{ c.durationMs }}ms</span>
          </li>
          <li v-if="notify.recentCalls.length === 0" class="empty">Chưa có lời gọi nào.</li>
        </ul>
      </aside>

      <section class="raw-detail" v-if="selected">
        <header>
          <strong>{{ selected.title }}</strong>
          <span :class="selected.ok ? 'ok' : 'err'">{{ selected.status }}</span>
          <span class="muted">{{ selected.durationMs }}ms</span>
        </header>
        <details>
          <summary>Response headers</summary>
          <pre>{{ pickHeaders(selected.headers) }}</pre>
        </details>
        <pre class="body">{{ selected.body }}</pre>
      </section>
      <section class="raw-detail empty" v-else>
        <p class="muted">Thực hiện một thao tác để xem response.</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.raw-drawer {
  position: fixed;
  bottom: 0;
  right: 0;
  left: 0;
  z-index: 50;
  pointer-events: none;
}
.raw-toggle {
  pointer-events: auto;
  position: absolute;
  right: 16px;
  bottom: 12px;
  background: #0f172a;
  color: #e2e8f0;
  border: 1px solid #334155;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.3);
}
.raw-toggle .badge {
  background: #1e293b;
  border-radius: 999px;
  padding: 1px 7px;
  font-size: 11px;
  color: #94a3b8;
}
.raw-body {
  pointer-events: auto;
  background: #020617;
  border-top: 1px solid #1e293b;
  display: grid;
  grid-template-columns: 280px 1fr;
  height: 320px;
  max-height: 45vh;
}
.raw-list {
  border-right: 1px solid #1e293b;
  overflow-y: auto;
}
.raw-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #1e293b;
  position: sticky;
  top: 0;
  background: #020617;
}
.ghost-sm {
  background: transparent;
  border: 1px solid #334155;
  color: #94a3b8;
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 11px;
  cursor: pointer;
}
.raw-list ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
.raw-list li {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 12px;
  border-bottom: 1px solid #0f172a;
  cursor: pointer;
}
.raw-list li.active {
  background: #0f172a;
}
.raw-list li .title {
  color: #cbd5e1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.raw-list li.fail .title {
  color: #fda4af;
}
.raw-list li .duration {
  color: #64748b;
  font-size: 11px;
}
.status-pill {
  font-size: 10px;
  padding: 2px 5px;
  border-radius: 4px;
  text-align: center;
}
.status-pill.ok {
  background: #052e16;
  color: #86efac;
}
.status-pill.err {
  background: #450a0a;
  color: #fca5a5;
}
.raw-detail {
  overflow-y: auto;
  padding: 12px;
}
.raw-detail header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}
.raw-detail header .ok { color: #86efac; }
.raw-detail header .err { color: #fda4af; }
.raw-detail .muted { color: #64748b; font-size: 11px; }
.raw-detail pre {
  margin: 6px 0;
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.empty.raw-detail { display: grid; place-items: center; }
</style>
