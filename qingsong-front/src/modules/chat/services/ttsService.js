import { MIMO_API_BASE_URL, MIMO_TTS_DEFAULT_KEY } from '@/config/env'

// localStorage 键名：用户可覆盖内置 key（内置 + 可覆盖策略）
export const TTS_API_KEY_STORAGE_KEY = 'mimo-tts-api-key'

// 音色克隆样本（base64）的 localStorage 键名
export const TTS_CLONE_SAMPLE_STORAGE_KEY = 'mimo-tts-clone-sample'

// 音色克隆样本 base64 长度上限（约 1MB 文件，base64 膨胀 4/3）
export const TTS_CLONE_MAX_BASE64_CHARS = 1_400_000

// MiMo TTS 系列模型
export const TTS_MODELS = {
  // 预置音色合成
  tts: 'mimo-v2.5-tts',
  // 音色克隆：audio.voice 传音频样本 base64（仅 mp3/wav）
  voiceclone: 'mimo-v2.5-tts-voiceclone',
  // 音色设计：user 消息传音色描述；audio.voice 不支持；optimize_text_preview 仅此模型支持
  voicedesign: 'mimo-v2.5-tts-voicedesign'
}

// MiMo 预置音色（mimo-v2.5-tts 模型）
export const TTS_VOICES = [
  { value: 'mimo_default', label: '默认音色' },
  { value: '冰糖', label: '冰糖' },
  { value: '茉莉', label: '茉莉' },
  { value: '苏打', label: '苏打' },
  { value: '白桦', label: '白桦' },
  { value: 'Mia', label: 'Mia' },
  { value: 'Chloe', label: 'Chloe' },
  { value: 'Milo', label: 'Milo' },
  { value: 'Dean', label: 'Dean' }
]

export const DEFAULT_TTS_VOICE = 'mimo_default'

// —— 音色克隆样本存储（localStorage）——
// 样本结构：{ name, dataUrl, mime }，dataUrl 为完整 DataURL（data:<mime>;base64,<data>）
// voiceclone 模型要求 audio.voice 必须是 DataURL，且 MIME 前缀必须与音频真实格式一致
const CLONE_MIME_BY_EXT = {
  '.mp3': 'audio/mpeg',
  '.wav': 'audio/wav',
  '.flac': 'audio/flac',
  '.m4a': 'audio/mp4',
  '.ogg': 'audio/ogg'
}

// 从文件名扩展名推导正确 MIME
const mimeFromName = name => {
  const extension = `.${(String(name || '').split('.').pop() || '').toLowerCase()}`
  return CLONE_MIME_BY_EXT[extension] || null
}

const readCloneSample = () => {
  if (typeof window === 'undefined') return null
  try {
    return JSON.parse(window.localStorage.getItem(TTS_CLONE_SAMPLE_STORAGE_KEY)) || null
  } catch {
    return null
  }
}

// 统一规整样本：从 DataURL/base64 提取纯 base64，并按扩展名重建正确 MIME 前缀。
// 兼容修复前的脏数据（dataUrl 前缀为空/错误，如 "data:;base64,xxx"）。
const normalizeSample = sample => {
  if (!sample) return null
  const base64 = sample.dataUrl
    ? String(sample.dataUrl).split(',')[1] || null
    : sample.base64 || null
  if (!base64) return null
  const mime = mimeFromName(sample.name) || sample.mime || 'audio/wav'
  return {
    name: sample.name || 'clone-sample',
    dataUrl: `data:${mime};base64,${base64}`,
    mime
  }
}

// 当前已配置的克隆音色样本：{ name, dataUrl, mime } 或 null
export const getClonedVoiceSample = () => normalizeSample(readCloneSample())

// 设置克隆音色样本（dataUrl 的 base64 部分由调用方保证不超过 TTS_CLONE_MAX_BASE64_CHARS）
export const setClonedVoiceSample = sample => {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(TTS_CLONE_SAMPLE_STORAGE_KEY, JSON.stringify(sample))
}

// 清除克隆音色样本
export const clearClonedVoiceSample = () => {
  if (typeof window === 'undefined') return
  window.localStorage.removeItem(TTS_CLONE_SAMPLE_STORAGE_KEY)
}

// —— base64 工具 ——
// base64 → Blob（用于下载 / 预览）
export const base64ToBlob = (base64, mime = 'application/octet-stream') => {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new Blob([bytes], { type: mime })
}

// ArrayBuffer → base64（分块避免栈溢出）
export const arrayBufferToBase64 = buffer => {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  const chunkSize = 0x8000
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize))
  }
  return btoa(binary)
}

// 通过文件字节魔数检测真实音频格式（不信任扩展名 / file.type）
// 手机录音常被改名成 .mp3/.wav，实际容器是 m4a/opus，魔数检测才能拿到正确 MIME。
// 参考 mimo-tts-studio 的 detectAudioContainer 实现。
export const detectAudioMime = buffer => {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer)
  if (bytes.length < 12) return null
  const head = String.fromCharCode(...bytes.slice(0, 12))

  if (head.startsWith('RIFF') && head.slice(8, 12) === 'WAVE') return 'audio/wav'
  if (head.startsWith('ID3') || (bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0)) return 'audio/mpeg'
  // MP4/M4A 文件在开头有 ftyp box；改名为 .mp3 也不改变它
  if (head.slice(4, 8) === 'ftyp') return 'audio/mp4'
  if (head.startsWith('OggS')) return 'audio/ogg'
  if (head.startsWith('fLaC')) return 'audio/flac'
  return null
}

// 完整 DataURL → Blob（用于克隆样本预览）
export const dataUrlToBlob = dataUrl => {
  const [meta, base64] = String(dataUrl || '').split(',')
  const mime = meta?.match(/^data:([^;]+)/)?.[1] || 'application/octet-stream'
  return base64ToBlob(base64 || '', mime)
}

// base64 → Uint8Array
export const base64ToBytes = base64 => {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

const getApiKey = () => {
  if (typeof window !== 'undefined') {
    const override = window.localStorage.getItem(TTS_API_KEY_STORAGE_KEY)?.trim()
    if (override) {
      return override
    }
  }
  return MIMO_TTS_DEFAULT_KEY
}

export const hasTtsApiKey = () => Boolean(getApiKey())

// 读取"用户配置的"key（localStorage 覆盖项），未配置返回空串
export const getConfiguredTtsApiKey = () => {
  if (typeof window === 'undefined') return ''
  return window.localStorage.getItem(TTS_API_KEY_STORAGE_KEY)?.trim() || ''
}

// 保存/清除用户配置的 key（空值清除覆盖项，回到内置 key）
export const setConfiguredTtsApiKey = value => {
  if (typeof window === 'undefined') return
  const trimmed = String(value || '').trim()
  if (trimmed) {
    window.localStorage.setItem(TTS_API_KEY_STORAGE_KEY, trimmed)
  } else {
    window.localStorage.removeItem(TTS_API_KEY_STORAGE_KEY)
  }
}

const extractErrorMessage = async (response) => {
  try {
    const payload = await response.json()
    return payload?.error?.message || payload?.message || `HTTP error! status: ${response.status}`
  } catch {
    try {
      const text = await response.text()
      if (text) {
        return text
      }
    } catch {
      // ignore body read errors and fallback to status
    }
  }
  return `HTTP error! status: ${response.status}`
}

// 组装 messages：voicedesign 模型需要 user 消息携带音色描述
const buildMessages = ({ text, voiceDesign }) => {
  if (voiceDesign) {
    return [
      { role: 'user', content: voiceDesign },
      { role: 'assistant', content: text }
    ]
  }
  return [{ role: 'assistant', content: text }]
}

// 组装 audio：voicedesign 模型不支持 voice，仅可带 optimize_text_preview
const buildAudioPayload = ({ format, voice, model, optimizeTextPreview }) => {
  const audio = { format }
  if (model === TTS_MODELS.voicedesign) {
    if (optimizeTextPreview) {
      audio.optimize_text_preview = true
    }
  } else if (voice) {
    audio.voice = voice
  }
  return audio
}

/**
 * 调用 MiMo 语音合成流式接口（mimo-v2.5-tts / voiceclone / voicedesign）
 * 返回 SSE 流：每个 chunk 的 delta.audio.data 为 base64 编码的 24kHz PCM16LE 单声道音频。
 */
export const ttsAPI = {
  async synthesizeChatStream(
    text,
    voice,
    { model = TTS_MODELS.tts, voiceDesign, optimizeTextPreview = false, signal } = {}
  ) {
    const apiKey = getApiKey()

    if (!apiKey) {
      throw new Error('未配置 MiMo API Key，请在控制台设置 mimo-tts-api-key')
    }

    const response = await fetch(`${MIMO_API_BASE_URL}/chat/completions`, {
      method: 'POST',
      headers: {
        'api-key': apiKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model,
        messages: buildMessages({ text, voiceDesign }),
        audio: buildAudioPayload({
          format: 'pcm16',
          voice,
          model,
          optimizeTextPreview
        }),
        stream: true
      }),
      signal
    })

    if (!response.ok) {
      throw new Error(await extractErrorMessage(response))
    }

    return {
      reader: response.body.getReader()
    }
  },

  /**
   * 调用 MiMo 语音合成非流式接口，一次性返回完整音频 base64。
   * 支持 wav / mp3 格式（用于导出下载）。返回 { data, format, audioId, usage }。
   */
  async synthesizeFull(
    text,
    { voice, format = 'mp3', model = TTS_MODELS.tts, voiceDesign, optimizeTextPreview = false, signal } = {}
  ) {
    const apiKey = getApiKey()

    if (!apiKey) {
      throw new Error('未配置 MiMo API Key，请在控制台设置 mimo-tts-api-key')
    }

    const response = await fetch(`${MIMO_API_BASE_URL}/chat/completions`, {
      method: 'POST',
      headers: {
        'api-key': apiKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model,
        messages: buildMessages({ text, voiceDesign }),
        audio: buildAudioPayload({
          format,
          voice,
          model,
          optimizeTextPreview
        }),
        stream: false
      }),
      signal
    })

    if (!response.ok) {
      throw new Error(await extractErrorMessage(response))
    }

    const payload = await response.json()
    const audio = payload?.choices?.[0]?.message?.audio || {}

    return {
      data: audio.data || '',
      format,
      audioId: audio.id || null,
      usage: payload?.usage || null,
      finalTextPreview: payload?.choices?.[0]?.message?.final_text_preview || null
    }
  }
}
