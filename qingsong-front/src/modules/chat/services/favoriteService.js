import http from '@/utils/http'

// 聊天消息收藏：收藏即快照（副本不随原消息删除），列表游标分页。
// 后端接口挂在 /ai/chat/favorite，统一 Result 包装（ok/data/msg），鉴权走 satoken cookie/header。
export const favoriteAPI = {
  // 收藏消息（幂等）：返回 true 新收藏；false 已收藏过
  async favorite(messageNo) {
    const result = await http.post('/ai/chat/favorite', { messageNo: String(messageNo) })
    if (result?.ok !== 1) {
      throw new Error(result?.msg || '收藏失败')
    }
    return Boolean(result.data)
  },

  // 取消收藏（幂等）
  async unfavorite(messageNo) {
    const result = await http.delete(`/ai/chat/favorite/${encodeURIComponent(String(messageNo))}`)
    if (result?.ok !== 1) {
      throw new Error(result?.msg || '取消收藏失败')
    }
    return Boolean(result.data)
  },

  // 游标分页：before/beforeId 为上一页末条游标（首页不传）；keyword 匹配内容快照与会话标题
  async getFavoritePage({ keyword, roleCode, before, beforeId, limit = 15 } = {}) {
    const params = new URLSearchParams()
    if (keyword) params.append('keyword', keyword)
    if (roleCode) params.append('roleCode', roleCode)
    if (before) params.append('before', before)
    if (beforeId != null) params.append('beforeId', String(beforeId))
    params.append('limit', String(limit))
    const query = params.toString()
    const result = await http.get(`/ai/chat/favorite/page?${query}`)
    if (result?.ok !== 1) {
      throw new Error(result?.msg || '获取收藏列表失败')
    }
    return {
      list: Array.isArray(result.data?.list) ? result.data.list : [],
      hasMore: Boolean(result.data?.hasMore)
    }
  }

  // 注：星标回显已并入消息接口（每条消息带 favorited 字段），不再单独查 /ai/chat/favorite/status
}
