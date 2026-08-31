import { reactive } from 'vue'
import { favoriteAPI } from '../services/favoriteService.js'

// 聊天页消息收藏状态（模块级单例）：
// - 消息接口已合并 favorited 字段，loadChat 加载消息后直接以消息为准对齐（syncFromMessages），不再单独查 status 接口；
// - ChatMessage 读星标回显、写 toggle；
// - ChatFavoritesPage 取消收藏后经 unmarkFavorited 同步；
// 状态以服务端为准（双向对齐），本地仅作当前会话内的展示缓存。
const favoritedNos = reactive(new Set())
// 进行中请求按 messageNo 去重，防止连点产生并发 toggle
const pendingNos = new Set()

export const useFavoriteMessages = () => {
  const isFavorited = messageNo => favoritedNos.has(String(messageNo))

  // 以消息随带的 favorited 字段为准双向对齐单例（已收藏 add、未收藏 delete）；
  // toggle 进行中的 messageNo 跳过，避免对齐覆盖刚完成的 toggle 结果
  const syncFromMessages = messages => {
    ;(messages || []).forEach(item => {
      const no = item?.messageNo ? String(item.messageNo) : ''
      if (!no || pendingNos.has(no)) return
      if (item.favorited) favoritedNos.add(no)
      else favoritedNos.delete(no)
    })
  }

  // 外部已完成取消收藏（如收藏页删除条目）时同步单例，不重复调接口
  const unmarkFavorited = messageNo => {
    favoritedNos.delete(String(messageNo))
  }

  const toggleFavorite = async (messageNo, messageApi) => {
    const no = String(messageNo)
    if (!no || pendingNos.has(no)) return
    pendingNos.add(no)
    const next = !favoritedNos.has(no)
    try {
      if (next) {
        await favoriteAPI.favorite(no)
        favoritedNos.add(no)
        messageApi?.success('已收藏')
      } else {
        await favoriteAPI.unfavorite(no)
        favoritedNos.delete(no)
        messageApi?.success('已取消收藏')
      }
    } catch (error) {
      messageApi?.error(error?.message || (next ? '收藏失败' : '取消收藏失败'))
    } finally {
      pendingNos.delete(no)
    }
  }

  return { favoritedNos, isFavorited, syncFromMessages, unmarkFavorited, toggleFavorite }
}
