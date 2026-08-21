import { createRouter, createWebHistory } from 'vue-router'
import { authService } from '@/services/authService'
import { mainRoutes } from './routes/main.routes'

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
  const hasLocalToken = authService.isAuthenticated()

  if (!isRoutePublic(to)) {
    if (!hasLocalToken) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
        replace: true
      }
    }

    if (!await authService.validateSession()) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
        replace: true
      }
    }
  }

  if (isGuestOnlyRoute(to) && hasLocalToken) {
    if (await authService.validateSession()) {
      return getSafeRedirect(to.query.redirect)
    }

    return true
  }

  return true
})

export default router
