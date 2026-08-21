// 全局环境配置：后端基础地址单一出口
// 通过 VITE_API_BASE_URL 覆盖：
// - 设为 ''（空字符串）时走相对路径，由 Vite dev server 代理转发（推荐局域网/多端访问）
// - 不设置时回退到本地后端 http://localhost:8088（仅本机开发默认）
const RAW_API_BASE_URL = import.meta.env.VITE_API_BASE_URL
export const API_BASE_URL =
  RAW_API_BASE_URL !== undefined ? RAW_API_BASE_URL : 'http://localhost:8088'

// MiMo TTS 语音合成（纯前端直连）
// 注意：内置 key 会打进前端产物，存在泄露风险；请勿在此提交真实 key。
// 请通过环境变量 VITE_MIMO_TTS_DEFAULT_KEY 注入，或运行时在 localStorage('mimo-tts-api-key') 覆盖
export const MIMO_API_BASE_URL = 'https://api.xiaomimimo.com/v1'
export const MIMO_TTS_DEFAULT_KEY = import.meta.env.VITE_MIMO_TTS_DEFAULT_KEY || ''
