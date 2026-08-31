import { nextTick, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { isLocalOnlyChatId } from './useAIChatPage.js'
import { getVirtualListController } from './virtualListController.js'

// URL 路由 ↔ 聊天状态 双向同步：
// - URL → 状态：/chat/:roleId/:chatId 深链进入时解析角色并加载对应会话；
//   可带 ?msg=<messageNo> 或 #<messageNo>--<标题slug> 定位到具体消息（含消息内标题锚点）并跳转高亮
// - 状态 → URL：切角色/切会话/临时会话 ID 提升时用 router.replace 回写（不增历史栈，
//   浏览器后退直接回到进入聊天前的页面，符合“后退回首页”诉求）
//
// 约定：
// - URL 只存稳定标识（roleId / 服务端 sessionNo），临时本地会话 ID（temp-*）不写进 URL；
// - 会话存在性校验失败时回退到该角色最新会话（或新建），不回 404；
// - 用 replace 而非 push，保证会话切换不污染历史栈。
export const useChatRouteSync = ({
  selectedRole,
  selectedRoleName,
  currentChatId,
  currentMessages,
  chatHistory,
  roles,
  loadRoles,
  loadChat,
  loadChatHistory,
  loadHistoryDates,
  loadLatestChat,
  initDefaultChat,
  handleJumpToMessage
}) => {
  const route = useRoute()
  const router = useRouter()
  const message = useMessage()

  // 正在“从 URL 应用到状态”，期间抑制状态→URL 回写，避免死循环
  let applyingFromUrl = false
  // 路由连续变化时使旧的一次 apply 失效，避免旧结果覆盖新状态
  let applyToken = 0

  // selectedRole 存在两种形态：startNewChat 置为 { ...role.value, id }（顶层 id），
  // 深链 applyRouteToState 置为 roles 条目 { name, value:{ id, ... } }（嵌套 value.id），
  // 统一兜底读取，避免角色 ID 解析为空导致 URL 不回写
  const currentRoleId = () => {
    const role = selectedRole.value
    const id = role?.id ?? role?.value?.id
    return id != null ? String(id) : null
  }

  const currentChatIdValue = () =>
    currentChatId.value != null ? String(currentChatId.value) : null

  // 编码路径段：roleId / sessionNo 经 URL 传递时避免特殊字符破坏路由匹配
  const encodeSegment = value => encodeURIComponent(String(value))

  // 解析 URL 中的消息定位目标：
  // - 优先 ?msg=<messageNo>（消息级）
  // - 支持 #<messageNo>--<标题slug>（消息内标题锚点，即 useChatMessageContent 生成的 heading anchorId）
  const resolveMessageTarget = () => {
    const queryMsg = route.query.msg ? String(route.query.msg) : ''
    if (queryMsg) {
      return { messageNo: queryMsg, anchorId: queryMsg }
    }

    const hash = String(route.hash || '').replace(/^#/, '')
    if (!hash) return null

    const sepIndex = hash.indexOf('--')
    if (sepIndex === -1) {
      return { messageNo: hash, anchorId: hash }
    }

    return {
      messageNo: hash.slice(0, sepIndex),
      anchorId: hash
    }
  }

  // 虚拟滚动 + 首次定位估算高度漂移：目标消息可能未渲染。
  // 策略：
  // 1) 目标消息未渲染时，用 align:'center' 强制滚到目标索引（比 'auto' 更确定），逐次逼近真实位置；
  // 2) 消息渲染出来（data-index 命中）后，直接对消息槽用 DOM scrollIntoView 精确居中（基于真实布局，不受虚拟器估算影响）；
  // 3) 再对标题锚点 scrollIntoView 居中，稍后重确认一次以抵御虚拟列表测量引发的重排。
  // 中文 hash 编解码可能不一致，同时尝试原始 / decode / encode 三种形态查找标题元素
  const scrollToHeading = (messageNo, anchorId) => {
    if (typeof document === 'undefined' || !anchorId) return

    const candidates = [anchorId]
    try {
      candidates.push(decodeURIComponent(anchorId))
    } catch (error) {}
    try {
      candidates.push(encodeURIComponent(anchorId))
    } catch (error) {}
    const findHeading = () => candidates.map(id => document.getElementById(id)).find(Boolean) || null

    const index = currentMessages.value.findIndex(
      m => String(m?.messageNo || m?.id) === String(messageNo)
    )
    if (index < 0) return

    const virtualList = getVirtualListController()
    let attempts = 0
    const tryLocate = () => {
      if (attempts++ > 60) {
        // 诊断信息：区分「目标消息未渲染（虚拟列表估算漂移）」与「标题锚点 id 不匹配」
        const slot = document.querySelector(`[data-index="${index}"]`)
        console.warn('[chat-route] 标题锚点定位失败', {
          anchorId,
          messageIndex: index,
          messageRendered: Boolean(slot),
          headingAnchors: slot
            ? Array.from(slot.querySelectorAll('.heading-anchor')).map(el => el.id).slice(0, 20)
            : []
        })
        return
      }

      const slot = document.querySelector(`[data-index="${index}"]`)
      if (slot) {
        slot.scrollIntoView({ block: 'center', behavior: 'auto' })
        const el = findHeading()
        if (el) {
          el.scrollIntoView({ block: 'center', behavior: 'auto' })
          // 虚拟列表测量可能引发重排，稍后再确认一次
          setTimeout(() => {
            const again = findHeading()
            if (again) again.scrollIntoView({ block: 'center', behavior: 'auto' })
          }, 300)
          return
        }
        // 消息已渲染但标题暂未找到：继续轮询，不再重滚
        setTimeout(tryLocate, 100)
        return
      }

      // 消息未渲染：强制居中滚动并测量，逐次逼近目标
      if (virtualList && index >= 0) {
        virtualList.scrollToIndex(index, { align: 'center', behavior: 'auto', highlight: false })
      }
      setTimeout(tryLocate, 150)
    }
    tryLocate()
  }

  // 状态 → URL：
  // - 会话已确定且归属当前角色 → 写 /chat/:roleId/:chatId
  // - 新建中的本地临时会话 → 写 /chat/:roleId（角色级，刷新会回该角色最新会话）
  // - 会话未确定（初始化中，chatId 为空）或 chatId 归属上一角色（角色切换中）→ 不写，
  //   避免写角色级 URL 触发 applyRouteToState 误判为“加载该角色最新会话”导致全量重复加载
  const syncUrlFromState = async () => {
    if (applyingFromUrl) return

    const roleId = currentRoleId()
    if (!roleId) return

    const chatId = currentChatIdValue()
    const roleName = String(selectedRoleName.value || '')

    let targetPath = null
    if (chatId) {
      if (isLocalOnlyChatId(chatId)) {
        targetPath = `/chat/${encodeSegment(roleId)}`
      } else {
        const historyItem = chatHistory.value.find(item => String(item.id) === chatId)
        const belongsToCurrentRole = historyItem
          ? String(historyItem.role) === roleName
          : true
        if (belongsToCurrentRole) {
          targetPath = `/chat/${encodeSegment(roleId)}/${encodeSegment(chatId)}`
        }
      }
    }

    if (!targetPath || route.path === targetPath) return

    // replace 不增历史栈（后退直接回进入聊天前的页面）；await 避免未处理拒绝
    try {
      await router.replace({ path: targetPath })
    } catch (error) {
      console.warn('会话 URL 同步被路由守卫中断:', error)
    }
  }

  const applyRouteToState = async () => {
    const token = ++applyToken
    const roleParam = route.params.roleId ? String(route.params.roleId) : null
    const chatParam = route.params.chatId ? String(route.params.chatId) : null
    const messageTarget = resolveMessageTarget()

    // /chat（无参数）的默认初始化由 useAIChatPage 自身负责，这里不干预
    if (!roleParam) return

    applyingFromUrl = true
    let chatLoadedThisApply = false
    try {
      if (roles.value.length === 0) {
        await loadRoles()
        if (token !== applyToken) return
      }

      const role = roles.value.find(item => String(item.value?.id) === roleParam)
      if (!role) {
        message.warning('角色不存在或已删除，已切换到默认角色')
        await initDefaultChat()
        return
      }

      if (currentRoleId() !== roleParam) {
        selectedRole.value = role
        selectedRoleName.value = role.value.name
        // 深链进入不经 startNewChat，日历可用日期在这里补加载（不阻塞主流程）
        loadHistoryDates?.()
      }

      const stateChatId = currentChatIdValue()

      if (chatParam && !isLocalOnlyChatId(chatParam)) {
        // 带会话号：首页历史（填充侧边栏）与目标会话消息并行加载；
        // 存在性不再查全量历史，由消息接口 404 判定（loadChat 返回 null）后回退最新会话
        if (stateChatId !== chatParam) {
          const historyPromise = loadChatHistory(false)
          const result = await loadChat(chatParam, { skipScroll: Boolean(messageTarget) })
          await historyPromise
          if (token !== applyToken) return

          if (result === null) {
            message.warning('会话不存在或已删除，已切换到该角色最新会话')
            await loadLatestChat()
          }
          chatLoadedThisApply = true
        }
      } else if (!chatParam) {
        // 仅进入角色：当前会话不归属该角色（或尚未有会话）时，加载该角色最新会话（无历史自动新建）；
        // 已是该角色下的会话（含新建中的本地临时会话）则保持不动，避免打断进行中的对话
        const belongsToCurrentRole = (() => {
          if (stateChatId === null) return false
          const historyItem = chatHistory.value.find(item => String(item.id) === stateChatId)
          if (historyItem) {
            return String(historyItem.role) === String(selectedRoleName.value || '')
          }
          return isLocalOnlyChatId(stateChatId)
        })()

        if (!belongsToCurrentRole) {
          await loadLatestChat()
          if (token !== applyToken) return
          chatLoadedThisApply = true
        }
      }

      // 深链进入且带消息定位目标（?msg= 或 #messageNo--标题）：加载完成后跳转消息并高亮，
      // 若有标题锚点再定位到消息内的具体章节；同会话内 hash 变化由浏览器原生锚点滚动处理，不重复跳转
      if (messageTarget && chatLoadedThisApply) {
        await nextTick()
        if (token === applyToken) {
          handleJumpToMessage({ messageNo: messageTarget.messageNo })
          if (messageTarget.anchorId !== messageTarget.messageNo) {
            scrollToHeading(messageTarget.messageNo, messageTarget.anchorId)
          }
        }
      }
    } finally {
      if (token === applyToken) {
        applyingFromUrl = false
        await syncUrlFromState()
      }
    }
  }

  watch(
    () => [route.params.roleId, route.params.chatId, route.query.msg, route.hash],
    () => {
      applyRouteToState()
    }
  )

  watch(selectedRole, () => {
    syncUrlFromState()
  })
  watch(currentChatId, () => {
    syncUrlFromState()
  })

  onMounted(async () => {
    // 等待初始导航完成再应用/回写 URL，避免 replace 与初始化导航竞争被吞掉
    await router.isReady()
    applyRouteToState()
  })

  return {
    applyRouteToState,
    syncUrlFromState
  }
}
