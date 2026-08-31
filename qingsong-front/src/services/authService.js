import http from '@/utils/http'
import {
  clearAuthSession,
  getAuthSession,
  getAuthToken,
  saveAuthSession,
  validateNavigationSession
} from './authSession.js'

const SUCCESS_CODES = new Set([0, 200, '0', '200', 'success', 'SUCCESS'])

// 导航守卫会话校验 TTL：校验通过后 60s 内的重复导航复用结果，不再每次请求 /user-config/session。
// 一致性兜底：TTL 窗口内他处登出/会话失效，由 http 拦截器 401 统一踢回登录页（见 utils/http.js）。
const SESSION_VALIDATION_TTL_MS = 60 * 1000
let lastValidatedAt = 0

const getResponseMessage = payload => {
  const msg = payload?.msg || payload?.message || payload?.error
  if (msg) return msg
  // 兜底：把后端原始结构带出来，避免只剩一句“抽象”看不出原因
  if (payload && typeof payload === 'object') {
    const { ok, code, status } = payload
    if (ok !== undefined) return `请求未成功（ok=${ok}）`
    if (code !== undefined || status !== undefined) return `请求未成功（code=${code ?? status}）`
  }
  return '接口请求失败，请稍后重试'
}

const normalizeAuthResponse = response => {
  // http 拦截器已解包为业务体，例如 { ok: 1, msg, data } 或 { code: 0, msg, data }
  const wrapper = response
  const ok = wrapper?.ok
  const code = wrapper?.code ?? wrapper?.status

  // 兼容后端两种返回结构：ok 或 code 任一表示成功即可
  const isOk =
    (ok !== undefined && ok !== null && ok !== 0 && ok !== '0' && ok !== false) ||
    (code !== undefined && code !== null && SUCCESS_CODES.has(code))

  if (!isOk) {
    throw new Error(getResponseMessage(wrapper))
  }

  // 返回完整业务体（含 ok / data），供调用方判断 data.ok 与读取 data.data
  return wrapper
}

export const authService = {
  login({ account, password, captcha = '' }) {
    return http({
      url: '/user-config/login',
      method: 'post',
      data: {
        account,
        password,
        captcha
      }
    })
      .then(normalizeAuthResponse)
      .then(result => {
        // 登录成功即一次有效校验，TTL 内导航不再重复请求 session 接口
        lastValidatedAt = Date.now()
        return result
      })
  },

  register({ account, password, rePassword }) {
    return http({
      url: '/user-config/register',
      method: 'post',
      data: {
        account,
        password,
        rePassword
      }
    }).then(normalizeAuthResponse)
  },

  saveSession(session, remember = true) {
    saveAuthSession(session, remember)
  },

  getSession() {
    return getAuthSession()
  },

  isAuthenticated() {
    return Boolean(getAuthToken())
  },

  validateSession() {
    // TTL 内复用上次有效结果：守卫调用方先查 hasLocalToken，本地无 token 不会走到这里
    if (Date.now() - lastValidatedAt < SESSION_VALIDATION_TTL_MS) {
      return Promise.resolve(true)
    }
    return validateNavigationSession(async () => {
      // 导航守卫的会话校验：失败由 validateNavigationSession fail-open 静默放行，
      // 这里必须 silent，否则 502/网络错误时拦截器全局弹窗与放行策略冲突（用户既看到"无法连接"又被放进页面）
      const response = await http.get('/user-config/session', { silent: true })
      return response?.ok === 1
    }).then(result => {
      // 仅明确有效才记时间戳；false（已清会话）与 'unavailable'（网络异常）不缓存，下次导航重试
      if (result === true) lastValidatedAt = Date.now()
      return result
    })
  },

  async logout() {
    try {
      await http.post('/user-config/logout')
    } finally {
      lastValidatedAt = 0
      clearAuthSession()
    }
  },

  clearSession() {
    lastValidatedAt = 0
    clearAuthSession()
  }
}
