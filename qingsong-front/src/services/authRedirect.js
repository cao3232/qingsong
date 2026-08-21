import { clearAuthSession } from './authSession.js'

let redirecting = false

export const redirectToLogin = () => {
  if (typeof window === 'undefined' || redirecting) return

  clearAuthSession()
  if (window.location.pathname.startsWith('/login')) return

  redirecting = true
  const current = window.location.pathname + window.location.search + window.location.hash
  window.location.href = `/login?redirect=${encodeURIComponent(current)}`
}
