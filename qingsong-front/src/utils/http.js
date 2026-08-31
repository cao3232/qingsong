import axios from 'axios'
import { API_BASE_URL } from '@/config/env'
import { getAuthToken } from '@/services/authSession'
import { redirectToLogin } from '@/services/authRedirect'
import { getResponseErrorMessage, isAuthFailure } from '@/services/authResponse'
import {
  isConnectionError,
  getConnectionErrorMessage,
  markNotified,
  isNotified
} from '@/services/networkError'
import { getGlobalMessage } from '@/services/message'

// 统一 HTTP 客户端（ES module 单例）：
// - baseURL 来自环境变量（见 src/config/env.js）
// - 请求拦截器从本地会话读取 token，并通过 satoken header 传给后端
// - 响应拦截器统一解包 res.data、兜底错误处理
const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

http.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers.set('satoken', token)
  }
  // FormData 场景下不能带显式的 application/json，否则后端 multipart 解析失败
  if (config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
})

// 响应拦截：直接返回业务数据，失败时抛出（由调用方 catch）
http.interceptors.response.use(
  (response) => {
    const data = response.data
    // 兼容历史接口用 HTTP 200 返回登录失效业务体的情况。
    if (isAuthFailure({ payload: data })) {
      redirectToLogin()
      return Promise.reject(new Error('登录态已失效，请重新登录'))
    }
    return data
  },
  (error) => {
    const status = error?.response?.status
    const payload = error?.response?.data
    if (isAuthFailure({ status, payload })) {
      redirectToLogin()
      return Promise.reject(new Error('登录态已失效，请重新登录'))
    }

    const silent = Boolean(error?.config?.silent || error?.response?.config?.silent)
    const conn = isConnectionError(error)
    const isServerError = status !== undefined && status >= 500
    const errorMessage = conn
      ? getConnectionErrorMessage()
      : getResponseErrorMessage({ status, payload, fallback: error?.message })

    const responseError = new Error(errorMessage)
    responseError.status = status
    responseError.cause = error

    // 5xx 服务端错误（500/502/503/504）与网络层失败由拦截器统一弹一次提示，避免各调用方
    // 重复硬编码文案；4xx 等业务错误只统一文案、不主动弹（交调用方按上下文提示，防止与页面重复）
    if ((conn || isServerError) && !silent) {
      const api = getGlobalMessage()
      if (api && !isNotified(responseError)) {
        api.error(errorMessage)
        markNotified(responseError)
      }
    }

    console.error('[http]', errorMessage)
    return Promise.reject(responseError)
  }
)

export { API_BASE_URL }
export default http
