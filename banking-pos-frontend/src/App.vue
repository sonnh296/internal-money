<script setup lang="ts">
import { RouterView } from 'vue-router'
import ToastStack from './components/ToastStack.vue'
import ConflictModal from './components/ConflictModal.vue'
</script>

<template>
  <RouterView />
  <ToastStack />
  <ConflictModal />
</template>

<style>
html, body, #app {
  min-height: 100%;
}
</style>
