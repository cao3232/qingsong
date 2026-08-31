import { createRouter, createWebHistory } from 'vue-router'
import { authService } from '@/services/authService'
import { mainRoutes } from './routes/main.routes'
import { getGlobalMessage } from '@/services/message'
import { getConnectionErrorMessage } from '@/services/networkError'

const routes = [...mainRoutes]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const DEFAULT_AUTH_REDIRECT = '/chat'

const isRoutePublic = route => route.matched.some(record => record.meta?.public)
const isGuestOnlyRoute = route => route.matched.some(record => record.meta?.guestOnly)

const getSafeRedirect = redirect => {
  if (typeof redirect !== 'string' || !redirect.startsWith('/')) return DEFAULT_AUTH_REDIRECT
  if (redirect.startsWith('/login') || redirect.startsWith('/register')) return DEFAULT_AUTH_REDIRECT

  return redirect
}

router.beforeEach(async (to, from) => {
  if (from.path === '/chat-pdf') {
    window.dispatchEvent(new CustomEvent('cleanupChatPDF'))
  }

  const hasLocalToken = authService.isAuthenticated()

  if (!isRoutePublic(to)) {
    if (!hasLocalToken) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
        replace: true
      }
    }

    const validation = await authService.validateSession()
    if (validation === false) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
        replace: true
      }
    }

    // 5xx/网络失败（'unavailable'）：弹统一提示，默认 fail-closed 不进入受保护页，
    // 仅路由 meta.failOpen 标记的页面放行（数据弱依赖/允许离线态进入的页面）
    if (validation === 'unavailable') {
      getGlobalMessage()?.error?.(getConnectionErrorMessage())
      if (to.matched.some(record => record.meta?.failOpen)) {
        return true
      }
      // 统一兜底：页面内导航（from 已有有效页面）取消导航停留原地；
      // 刷新/首次直进受保护页（初始导航，from 无有效匹配）fallback 回 public 首页，避免白屏
      if (from.matched.length > 0) {
        return false
      }
      return { path: '/' }
    }
  }

  if (isGuestOnlyRoute(to) && hasLocalToken) {
    // 仅当校验明确有效（true）才把已登录用户从 /login 等游客页重定向走；
    // 校验服务不可用（'unavailable'，网络失败/5xx）时不重定向，停留在当前游客页，
    // 避免 fail-open 在服务异常时替用户跳转
    const validation = await authService.validateSession()
    if (validation === true) {
      return getSafeRedirect(to.query.redirect)
    }

    return true
  }

  return true
})

export default router
