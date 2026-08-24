<script setup lang="ts">
import { onMounted } from 'vue'
import AppShell from './app/layouts/AppShell.vue'
import { NMessageProvider, NDialogProvider } from 'naive-ui'
import { useDictStore } from './stores/dictStore'

const dictStore = useDictStore()

onMounted(() => {
  dictStore.ensureLoaded()
})
</script>

<template>
  <n-message-provider>
    <n-dialog-provider>
      <AppShell />
    </n-dialog-provider>
  </n-message-provider>
</template>
