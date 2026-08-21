const SA_TOKEN_INVALID_CODES = new Set([
  11011, 11012, 11013, 11014, 11015, 11016, 11017,
  '11011', '11012', '11013', '11014', '11015', '11016', '11017'
])

const AUTH_FAILURE_MESSAGE = /token\s*无效|无效\s*token|未登录|登录已过期|请重新登录|token\s*已过期|被踢下线|被顶下线/i

export const isAuthFailure = ({ status, payload } = {}) => {
  if (status !== undefined && status !== null) {
    return status === 401
  }

  if (!payload || typeof payload !== 'object') return false

  const code = payload.code ?? payload.status
  if (SA_TOKEN_INVALID_CODES.has(code)) return true

  const message = payload.msg || payload.message || ''
  return payload.ok === 0 && typeof message === 'string' && AUTH_FAILURE_MESSAGE.test(message)
}

export const getResponseErrorMessage = ({ status, payload, fallback } = {}) => {
  const serverMessage = payload?.msg || payload?.message || payload?.error
  if (serverMessage) return serverMessage
  if (status === 500) return '服务器内部错误，请稍后重试'
  if (status === 403) return '没有权限执行此操作'
  return fallback || '网络请求失败，请稍后重试'
}
