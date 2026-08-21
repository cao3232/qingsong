import { getAuthToken } from '../services/authSession.js'
import { redirectToLogin } from '../services/authRedirect.js'
import { isAuthFailure } from '../services/authResponse.js'

// 统一为原生 fetch（含 SSE）添加 Sa-Token 请求头，不使用 cookie。
export const fetchAuth = async (url, options = {}) => {
  const { credentials: _credentials, headers: providedHeaders, ...requestOptions } = options
  const headers = new Headers(providedHeaders)
  const token = getAuthToken()

  if (token && !headers.has('satoken')) {
    headers.set('satoken', token)
  }

  const response = await fetch(url, { ...requestOptions, headers })
  if (isAuthFailure({ status: response.status })) {
    redirectToLogin()
    throw new Error('登录态已失效，请重新登录')
  }

  const contentType = response.headers?.get?.('content-type') || ''
  if (response.ok && contentType.includes('application/json')) {
    const payload = await response.clone().json().catch(() => null)
    if (isAuthFailure({ payload })) {
      redirectToLogin()
      throw new Error('登录态已失效，请重新登录')
    }
  }

  return response
}
