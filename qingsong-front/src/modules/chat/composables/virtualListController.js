// 虚拟滚动控制器单例：聊天工作区（ChatWorkspace）在挂载时注册，
// 父级（useAIChatPage）通过它把「滚动到底/跳转到指定消息」的操作交给虚拟列表，
// 而不是直接 query DOM —— 窗口化后不可见消息不在 DOM 中，直接查会失效。

let activeController = null

export const registerVirtualListController = controller => {
  activeController = controller
  return () => {
    if (activeController === controller) {
      activeController = null
    }
  }
}

export const getVirtualListController = () => activeController
