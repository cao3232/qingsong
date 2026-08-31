// 会话消息 LRU 缓存：切会话不重拉。
// 注意：发送/删除消息后由调用方 invalidate，不做 append——流式消息字段形状（toolSteps/tokenUsage）
// 与服务端回读形状不同，混存会导致渲染异常。
export const createMessageCache = (capacity = 8) => {
  const map = new Map()
  return {
    get(key) {
      const k = String(key)
      const value = map.get(k)
      if (value !== undefined) {
        // touch：移到最新位置
        map.delete(k)
        map.set(k, value)
      }
      return value
    },
    set(key, messages) {
      const k = String(key)
      map.delete(k)
      map.set(k, messages)
      while (map.size > capacity) {
        map.delete(map.keys().next().value)
      }
    },
    remove(key) {
      map.delete(String(key))
    },
    clear() {
      map.clear()
    },
    get size() {
      return map.size
    }
  }
}
