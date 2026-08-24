import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dictService } from '@/services/dictService'

const STORAGE_KEY = 'app-dict-config'

const loadLocalCache = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed && parsed.items ? parsed : null
  } catch (error) {
    console.error('加载字典本地缓存失败:', error)
    return null
  }
}

// 业务字典 store：启动时懒加载一次，版本号变了才重新拉取
export const useDictStore = defineStore('dict', () => {
  const local = loadLocalCache()
  const items = ref(local?.items || {})
  const version = ref(local?.version ?? null)
  const loaded = ref(false)
  const loading = ref(false)

  const ensureLoaded = async () => {
    if (loaded.value || loading.value) return
    loading.value = true
    try {
      const response = await dictService.getAll()
      if (response?.items) {
        if (version.value !== response.version) {
          items.value = response.items
          try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify({
              version: response.version,
              items: response.items
            }))
          } catch (error) {
            console.error('保存字典本地缓存失败:', error)
          }
        }
        version.value = response.version
      }
      loaded.value = true
    } catch (error) {
      console.error('加载字典失败:', error)
    } finally {
      loading.value = false
    }
  }

  // 获取某个字典类型的启用项列表
  const getItems = (code) => items.value[code] || []

  // 值 -> 文案，找不到时原样返回 key
  const getLabel = (code, key) => {
    const item = getItems(code).find((it) => it.key === key)
    return item ? item.label : key
  }

  // 手动刷新（版本号变化后下次重新拉取）
  const refresh = async () => {
    loaded.value = false
    loading.value = false
    await ensureLoaded()
  }

  return { items, version, loaded, loading, ensureLoaded, getItems, getLabel, refresh }
})
