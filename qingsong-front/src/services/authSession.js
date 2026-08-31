const AUTH_STORAGE_KEY = 'mt-ai-auth-session'
const LEGACY_AUTH_COOKIE_PATHS = ['/', '/user-config']

const parseSession = rawSession => {
  if (!rawSession || rawSession === 'null' || rawSession === 'undefined') return null

  try {
    return JSON.parse(rawSession)
  } catch {
    return null
  }
}

export const getAuthSession = () => {
  if (typeof window === 'undefined') return null

  return parseSession(window.localStorage.getItem(AUTH_STORAGE_KEY))
    || parseSession(window.sessionStorage.getItem(AUTH_STORAGE_KEY))
}

export const getAuthToken = () => {
  const session = getAuthSession()
  return typeof session === 'string' && session.trim() ? session.trim() : null
}

export const saveAuthSession = (session, remember = true) => {
  const storage = remember ? window.localStorage : window.sessionStorage
  const otherStorage = remember ? window.sessionStorage : window.localStorage
  storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session ?? ''))
  otherStorage.removeItem(AUTH_STORAGE_KEY)
}

export const clearLegacyAuthCookie = () => {
  if (typeof document === 'undefined') return

  LEGACY_AUTH_COOKIE_PATHS.forEach(path => {
    document.cookie = `satoken=; Max-Age=0; Path=${path}; SameSite=Lax`
  })
}

export const clearAuthSession = () => {
  window.localStorage.removeItem(AUTH_STORAGE_KEY)
  window.sessionStorage.removeItem(AUTH_STORAGE_KEY)
  clearLegacyAuthCookie()
}

export const validateAuthSession = async validator => {
  const token = getAuthToken()
  if (!token) return false

  const isValid = await validator(token)
  if (!isValid) clearAuthSession()
  return Boolean(isValid)
}

export const validateNavigationSession = async validator => {
  try {
    return await validateAuthSession(validator)
  } catch (error) {
    console.error('[auth] 登录态校验服务暂不可用', error)
    // 三态：校验服务不可用（网络失败/5xx）时既不确认也不否定登录，
    // 路由守卫据此"放行但不跳转"——fail-open 只放行受保护路由，不在 guest-only 路由上替用户跳转
    return 'unavailable'
  }
}
