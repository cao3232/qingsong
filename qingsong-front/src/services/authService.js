import http from '@/utils/http'
import {
  clearAuthSession,
  getAuthSession,
  getAuthToken,
  saveAuthSession,
  validateNavigationSession
} from './authSession.js'

const SUCCESS_CODES = new Set([0, 200, '0', '200', 'success', 'SUCCESS'])

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
    }).then(normalizeAuthResponse)
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
    return validateNavigationSession(async () => {
      const response = await http.get('/user-config/session')
      return response?.ok === 1
    })
  },

  async logout() {
    try {
      await http.post('/user-config/logout')
    } finally {
      clearAuthSession()
    }
  },

  clearSession() {
    clearAuthSession()
  }
}
