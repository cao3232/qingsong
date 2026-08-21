import { defineStore } from 'pinia'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    history: [],
    currentRole: '',
    isStreaming: false,
    selectedFiles: [],
    autoSendToFeishu: false
  }),
  actions: {
    addMessage(message) {
      this.messages.push(message)
    },
    clearFiles() {
      this.selectedFiles = []
    },
    toggleAutoSend() {
      this.autoSendToFeishu = !this.autoSendToFeishu
    }
  }
})
