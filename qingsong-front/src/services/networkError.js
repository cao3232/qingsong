// 通用网络/网关错误判定与统一文案（单一真源）
// 供 http 拦截器与各业务调用方复用，避免「无法连接」类文案散落到各页面
// 连接失败类错误：502/503/504、网络层失败（ERR_NETWORK）、fetch 抛 TypeError 等

const GATEWAY_STATUSES = new Set([502, 503, 504])

const CONNECTION_ERROR_REGEX =
  /failed to fetch|network error|networkerror|load failed|connect(ion)? (refused|reset|timed? out)/i

// 判定「无法连接后端」类错误（网络层失败或网关不可达），用于给用户明确的连接失败提示
export const isConnectionError = (error) => {
  if (!error) return false
  const message = String(error?.message || '')
  return (
    GATEWAY_STATUSES.has(error?.status) ||
    GATEWAY_STATUSES.has(error?.response?.status) ||
    !error?.response ||
    error?.code === 'ERR_NETWORK' ||
    error?.name === 'TypeError' ||
    CONNECTION_ERROR_REGEX.test(message)
  )
}

export const CONNECTION_ERROR_MESSAGE = '无法连接服务器，请检查网络或后端服务'

export const getConnectionErrorMessage = () => CONNECTION_ERROR_MESSAGE

// 防重复弹窗标记：拦截器已通知过的错误，调用方可据此跳过重复提示
const NOTIFIED_KEY = '__httpNotified'

export const markNotified = (error) => {
  if (error && typeof error === 'object') {
    error[NOTIFIED_KEY] = true
  }
  return error
}

export const isNotified = (error) =>
  Boolean(error && typeof error === 'object' && error[NOTIFIED_KEY])
