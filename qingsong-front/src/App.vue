<script setup lang="ts">
import AppShell from './app/layouts/AppShell.vue'
import { NMessageProvider, NDialogProvider } from 'naive-ui'
</script>

<template>
  <n-message-provider>
    <n-dialog-provider>
      <AppShell />
    </n-dialog-provider>
  </n-message-provider>
</template>
