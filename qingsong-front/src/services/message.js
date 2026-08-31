// 全局 message 访问层：供 http 拦截器等服务层在 setup 外获取 Naive UI message 实例
// 复用 AppShell 在 NMessageProvider 内挂载的 window.$message（见 AppShell.vue），
// 避免使用 createDiscreteApi 与主 NMessageProvider 产生两套 message 容器冲突
export const getGlobalMessage = () => {
  if (typeof window === 'undefined') return null
  return window.$message || null
}
