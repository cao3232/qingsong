import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { DEFAULT_EMOJI_PROVIDER } from '@/modules/chat/utils/emoji.js'

const STORAGE_KEY = 'app-emoji-config'

// 聊天消息 emoji 渲染风格（native / twemoji / openmoji / fluent），持久化到 localStorage
export const useEmojiStore = defineStore('emoji', () => {
  const provider = ref(DEFAULT_EMOJI_PROVIDER)

  const load = () => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (saved && typeof saved === 'string') {
        provider.value = saved
      }
    } catch (error) {
      console.error('加载表情配置失败:', error)
    }
  }

  const save = () => {
    try {
      localStorage.setItem(STORAGE_KEY, provider.value)
    } catch (error) {
      console.error('保存表情配置失败:', error)
    }
  }

  load()
  watch(provider, save)

  return { provider }
})
