// 语音播放（TTS）模块级单例：
// 所有 ChatMessage / 自动播放共享同一个播放器，同时只允许一条消息在播。
// 流式：直连 MiMo chat/completions（stream:true + pcm16），SSE 逐块 base64 PCM16(24kHz) → Web Audio 实时播放。
// 扩展能力：
//  - 音色克隆（mimo-v2.5-tts-voiceclone）：配置 mp3/wav 样本 base64 后，朗读/导出用克隆音色
//  - 语音导出（下载 mp3/wav）：短文本非流式 mp3，长文本流式收集 PCM 拼 WAV
//  - 用量统计：捕获流式末 chunk 与非流式响应里的 usage，累计展示
import { computed, reactive, ref } from 'vue'
import {
  DEFAULT_TTS_VOICE,
  TTS_MODELS,
  TTS_VOICES,
  base64ToBlob,
  clearClonedVoiceSample,
  getClonedVoiceSample,
  setClonedVoiceSample,
  ttsAPI
} from '../services/index.js'
import { useDictStore } from '@/stores/dictStore'
import { extractPlainText, splitTextIntoSegments } from '../utils/index.js'

const TTS_VOICE_STORAGE_KEY = 'mimo-tts-voice'
const TTS_AUTOPLAY_STORAGE_KEY = 'mimo-tts-autoplay'
const TTS_USAGE_STORAGE_KEY = 'mimo-tts-usage'
const TTS_VOICE_DESIGN_STORAGE_KEY = 'mimo-tts-voicedesign'
const TTS_OPTIMIZE_PREVIEW_STORAGE_KEY = 'mimo-tts-optimize-preview'
const TTS_PLAYBACK_RATE_STORAGE_KEY = 'mimo-tts-rate'

// 客户端播放倍速（API 无 speed 参数，纯 Web Audio playbackRate 实现）
export const TTS_PLAYBACK_RATES = [
  { value: 0.75, label: '0.75x' },
  { value: 1, label: '1x' },
  { value: 1.25, label: '1.25x' },
  { value: 1.5, label: '1.5x' }
]

// MiMo TTS 单次合成文本长度有限，超过该长度音频会被截断导致少读/跳段，需分段合成。
// 300 字仍偶发截断，进一步收紧到 200 字，降低接口超长截断风险。
const TTS_MAX_SEGMENT_CHARS = 200

const readStorage = key => {
  if (typeof window === 'undefined') return null
  return window.localStorage.getItem(key)
}

const writeStorage = (key, value) => {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(key, value)
}

// —— 模块级共享状态 ——
const voice = ref(readStorage(TTS_VOICE_STORAGE_KEY) || DEFAULT_TTS_VOICE)
const autoPlay = ref(readStorage(TTS_AUTOPLAY_STORAGE_KEY) === '1')
const voiceDesign = ref(readStorage(TTS_VOICE_DESIGN_STORAGE_KEY) || '')
const optimizePreview = ref(readStorage(TTS_OPTIMIZE_PREVIEW_STORAGE_KEY) === '1')
const playbackRate = ref(Number(readStorage(TTS_PLAYBACK_RATE_STORAGE_KEY)) || 1)
const playingMessageNo = ref(null)
const isPlaying = ref(false)
const downloadingMessageNo = ref(null)

// 音色克隆样本：{ name, base64, mime } 或 null
const cloneSample = ref(getClonedVoiceSample())

// 最近一次合成的音频 id（来自响应 audio.id）
const lastAudioId = ref(null)

// 用量统计：sessionTokens 仅本次页面会话累计；totalTokens 持久化累计
const readUsage = () => {
  try {
    return JSON.parse(readStorage(TTS_USAGE_STORAGE_KEY)) || { totalTokens: 0 }
  } catch {
    return { totalTokens: 0 }
  }
}
const usage = reactive({ sessionTokens: 0, totalTokens: readUsage().totalTokens })

const recordUsage = u => {
  if (!u) return
  const tokens = (Number(u.prompt_tokens) || 0) + (Number(u.completion_tokens) || 0)
  if (!tokens) return
  usage.sessionTokens += tokens
  usage.totalTokens += tokens
  writeStorage(TTS_USAGE_STORAGE_KEY, JSON.stringify({ totalTokens: usage.totalTokens }))
}

const resetUsage = () => {
  usage.sessionTokens = 0
  usage.totalTokens = 0
  writeStorage(TTS_USAGE_STORAGE_KEY, JSON.stringify({ totalTokens: 0 }))
}

// 根据配置决定本次请求使用的模型与参数：
// 克隆样本 > 音色设计 > 预置音色
const resolveRequestParams = () => {
  if (cloneSample.value?.dataUrl) {
    // voiceclone 模型要求 audio.voice 为完整 DataURL
    return { model: TTS_MODELS.voiceclone, voice: cloneSample.value.dataUrl }
  }
  if (voiceDesign.value?.trim()) {
    return {
      model: TTS_MODELS.voicedesign,
      voiceDesign: voiceDesign.value.trim(),
      optimizeTextPreview: optimizePreview.value
    }
  }
  return { model: TTS_MODELS.tts, voice: voice.value || DEFAULT_TTS_VOICE }
}

const setCloneSample = sample => {
  setClonedVoiceSample(sample)
  cloneSample.value = sample
}

const clearClone = () => {
  clearClonedVoiceSample()
  cloneSample.value = null
}

const setVoiceDesign = value => {
  voiceDesign.value = String(value || '').trim()
  writeStorage(TTS_VOICE_DESIGN_STORAGE_KEY, voiceDesign.value)
}

const setOptimizePreview = value => {
  optimizePreview.value = Boolean(value)
  writeStorage(TTS_OPTIMIZE_PREVIEW_STORAGE_KEY, optimizePreview.value ? '1' : '0')
}

const setPlaybackRate = value => {
  playbackRate.value = Number(value) || 1
  writeStorage(TTS_PLAYBACK_RATE_STORAGE_KEY, String(playbackRate.value))
}

// —— 内部播放器状态 ——
let audioContext = null
let abortController = null
let activeSources = []
let nextStartTime = 0
let pendingSourceCount = 0
let playbackToken = 0
let segmentTimer = null
let lastScheduledSegmentIndex = -1

const getAudioContext = () => {
  if (!audioContext) {
    const AudioCtor = window.AudioContext || window.webkitAudioContext
    audioContext = new AudioCtor({ sampleRate: 24000 })
  }
  return audioContext
}

// base64(PCM16LE) → Float32Array（Web Audio 播放用）
const decodePcmChunk = base64Data => {
  const binary = atob(base64Data)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }

  const sampleCount = Math.floor(bytes.length / 2)
  const samples = new Float32Array(sampleCount)
  const view = new DataView(bytes.buffer)

  for (let i = 0; i < sampleCount; i++) {
    samples[i] = view.getInt16(i * 2, true) / 32768.0
  }

  return samples
}

// base64(PCM16LE) → Int16Array（WAV 导出用）
const decodePcmChunkToInt16 = base64Data => {
  const binary = atob(base64Data)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new Int16Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.length / 2))
}

const stopAllSources = () => {
  activeSources.forEach(source => {
    try {
      source.onended = null
      source.stop()
    } catch {
      // 已停止的节点忽略
    }
  })
  activeSources = []
  pendingSourceCount = 0
  nextStartTime = 0
}

// 将一段 Float32 追加到播放队列末尾（back-to-back 调度，支持倍速）
// onSegmentStart 在该段首个音频块真正开始播放的时刻触发（而非请求到达时刻），保证翻页与发声一致
const scheduleChunk = (samples, { onSegmentStart, segment, segmentIndex, token } = {}) => {
  if (!samples || samples.length === 0) return

  const ctx = getAudioContext()
  const buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate)
  buffer.copyToChannel(samples, 0)

  const source = ctx.createBufferSource()
  source.buffer = buffer
  source.connect(ctx.destination)

  const rate = playbackRate.value || 1
  source.playbackRate.value = rate

  const startAt = Math.max(nextStartTime, ctx.currentTime + 0.05)
  source.start(startAt)
  nextStartTime = startAt + buffer.duration / rate

  pendingSourceCount += 1
  activeSources.push(source)

  source.onended = () => {
    pendingSourceCount -= 1
    const index = activeSources.indexOf(source)
    if (index !== -1) {
      activeSources.splice(index, 1)
    }
    if (pendingSourceCount <= 0) {
      playingMessageNo.value = null
      isPlaying.value = false
    }
  }

  if (typeof onSegmentStart === 'function' && segmentIndex !== lastScheduledSegmentIndex) {
    lastScheduledSegmentIndex = segmentIndex
    const delayMs = Math.max(0, Math.ceil((startAt - ctx.currentTime) * 1000))
    segmentTimer = setTimeout(() => {
      if (token === playbackToken) onSegmentStart(segment, segmentIndex)
    }, delayMs)
  }
}

const finishPlayback = () => {
  playingMessageNo.value = null
  isPlaying.value = false
  abortController = null
}

const stop = () => {
  playbackToken += 1
  if (segmentTimer) {
    clearTimeout(segmentTimer)
    segmentTimer = null
  }
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  stopAllSources()
  if (audioContext && audioContext.state === 'running') {
    audioContext.suspend()
  }
  finishPlayback()
}

// 逐段流式合成并消费每个 chunk（播放 / 收集 PCM 共用），返回结果与用量
// mode: 'play'（调度 Web Audio 播放） | 'collect'（收集 PCM16 供 WAV 导出）
const streamSegments = async (segments, token, { mode = 'play', onSegmentStart, optimizeTextPreview } = {}) => {
  const { model, voice: voiceParam, voiceDesign, optimizeTextPreview: defaultOptimize } = resolveRequestParams()
  const effectiveOptimize = optimizeTextPreview ?? defaultOptimize
  const collected = mode === 'collect' ? [] : null
  let totalSamples = 0
  let scheduledCount = 0
  lastScheduledSegmentIndex = -1

  const handleLine = (line, { segment, segmentIndex } = {}) => {
    if (token !== playbackToken) return
    const trimmed = line.trim()
    if (!trimmed.startsWith('data:')) return

    const data = trimmed.slice(5).trim()
    if (data === '[DONE]') return

    let payload
    try {
      payload = JSON.parse(data)
    } catch {
      return
    }

    const audio = payload?.choices?.[0]?.delta?.audio
    if (audio?.data) {
      if (mode === 'collect') {
        const int16 = decodePcmChunkToInt16(audio.data)
        collected.push(int16)
        totalSamples += int16.length
      } else {
        scheduleChunk(decodePcmChunk(audio.data), { onSegmentStart, segment, segmentIndex, token })
        scheduledCount += 1
      }
      if (audio.id) {
        lastAudioId.value = audio.id
      }
    }
    // 流式末尾 chunk 携带 usage
    if (payload?.usage) {
      recordUsage(payload.usage)
    }
  }

  for (const [segmentIndex, segment] of segments.entries()) {
    if (token !== playbackToken) break

    abortController = new AbortController()
    const { reader } = await ttsAPI.synthesizeChatStream(segment, voiceParam, {
      model,
      voiceDesign,
      optimizeTextPreview: effectiveOptimize,
      signal: abortController.signal
    })

    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      if (token !== playbackToken) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        handleLine(line, { segment, segmentIndex })
      }
    }

    // 流结束时刷新遗留在 buffer 中的末行：部分实现最后一条 SSE 事件不带换行，
    // 不处理会丢掉本段最后一帧音频，表现为段尾少读/跳字（与 chatSse.finish 对齐）
    if (buffer.trim()) {
      handleLine(buffer, { segment, segmentIndex })
    }
  }

  return { collected, totalSamples, scheduledCount }
}

const waitForPlaybackEnd = token => new Promise(resolve => {
  const check = () => {
    if (token !== playbackToken || (!isPlaying.value && pendingSourceCount === 0)) {
      resolve()
      return
    }
    window.setTimeout(check, 80)
  }
  check()
})

const playSegments = async (segments, { onSegmentStart, messageApi, optimizeTextPreview } = {}) => {
  const validSegments = (segments || []).filter(Boolean)
  if (!validSegments.length) return false
  stop()
  const token = ++playbackToken
  isPlaying.value = true
  try {
    const ctx = getAudioContext()
    if (ctx.state === 'suspended') {
      await ctx.resume()
    }
    await streamSegments(validSegments, token, { mode: 'play', onSegmentStart, optimizeTextPreview })
    if (token !== playbackToken || pendingSourceCount === 0) {
      if (token === playbackToken) messageApi?.warning?.('未获取到语音数据，请检查 TTS 配置')
      return false
    }
    await waitForPlaybackEnd(token)
    return token === playbackToken
  } catch (error) {
    if (error?.name !== 'AbortError' && token === playbackToken) {
      messageApi?.error?.(error?.message || '语音播放失败，请稍后重试')
    }
    stop()
    return false
  }
}

const play = async (message, messageApi) => {
  if (!message) return

  const plainText = extractPlainText(message.content)
  if (!plainText) {
    messageApi?.warning?.('消息内容为空，无法朗读')
    return
  }

  // 切换消息时先停止当前播放
  stop()
  const token = ++playbackToken

  playingMessageNo.value = message.messageNo || message.id || null
  isPlaying.value = true

  try {
    const ctx = getAudioContext()
    if (ctx.state === 'suspended') {
      await ctx.resume()
    }

    // 长文本分段合成：每段流式请求后连续调度播放（自动启用克隆音色）
    // 强制关闭“智能优化（optimize_text_preview）”：该功能会改写/压缩播报文本，造成跳句子/少读，
    // 与阅读器行为保持一致，朗读必须按原文逐字读完整条消息
    const segments = splitTextIntoSegments(plainText, TTS_MAX_SEGMENT_CHARS)
    const { scheduledCount } = await streamSegments(segments, token, { mode: 'play', optimizeTextPreview: false })

    if (scheduledCount === 0 && token === playbackToken) {
      messageApi?.warning?.('未获取到语音数据，请稍后重试或检查 API Key')
      stop()
      return
    }
  } catch (error) {
    if (error?.name === 'AbortError' || token !== playbackToken) {
      return
    }
    console.error('语音播放失败:', error)
    messageApi?.error?.(error?.message || '语音播放失败，请稍后重试')
    stop()
  }
}

const togglePlay = (message, messageApi) => {
  const targetId = message?.messageNo || message?.id
  if (isPlaying.value && playingMessageNo.value === targetId) {
    stop()
    return
  }
  play(message, messageApi)
}

// 16-bit PCM mono → WAV Blob（24kHz）
const buildWavBlob = (samples, sampleRate = 24000) => {
  const numSamples = samples.length
  const buffer = new ArrayBuffer(44 + numSamples * 2)
  const view = new DataView(buffer)
  const writeString = (offset, str) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i))
    }
  }

  writeString(0, 'RIFF')
  view.setUint32(4, 36 + numSamples * 2, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true) // PCM
  view.setUint16(22, 1, true) // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true) // blockAlign
  view.setUint16(34, 16, true) // bitsPerSample
  writeString(36, 'data')
  view.setUint32(40, numSamples * 2, true)

  new Int16Array(buffer, 44).set(samples)
  return new Blob([buffer], { type: 'audio/wav' })
}

const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// 导出消息语音：短文本走非流式 mp3，长文本流式收集 PCM 拼 WAV
const downloadAudio = async (message, messageApi) => {
  if (!message) return false

  const plainText = extractPlainText(message.content)
  if (!plainText) {
    messageApi?.warning?.('消息内容为空，无法导出语音')
    return false
  }

  const targetId = message.messageNo || message.id || null
  if (downloadingMessageNo.value === targetId) {
    return false
  }
  downloadingMessageNo.value = targetId

  const token = ++playbackToken
  const { model, voice: voiceParam, voiceDesign, optimizeTextPreview } = resolveRequestParams()
  const baseName = `语音_${(message.messageNo || message.id || Date.now())}`

  try {
    const segments = splitTextIntoSegments(plainText, TTS_MAX_SEGMENT_CHARS)

    if (segments.length === 1) {
      // 短文本：非流式 mp3，文件小、便于分享
      const { data, usage: u } = await ttsAPI.synthesizeFull(segments[0], {
        voice: voiceParam,
        format: 'mp3',
        model,
        voiceDesign,
        optimizeTextPreview,
        signal: abortController?.signal
      })
      if (!data) {
        throw new Error('未获取到语音数据，请稍后重试')
      }
      recordUsage(u)
      downloadBlob(base64ToBlob(data, 'audio/mpeg'), `${baseName}.mp3`)
    } else {
      // 长文本：流式收集 PCM16，拼成一个 WAV
      const { collected, totalSamples } = await streamSegments(segments, token, { mode: 'collect' })
      if (token !== playbackToken || totalSamples === 0) {
        throw new Error('未获取到语音数据，请稍后重试')
      }
      const samples = new Int16Array(totalSamples)
      let offset = 0
      for (const chunk of collected) {
        samples.set(chunk, offset)
        offset += chunk.length
      }
      downloadBlob(buildWavBlob(samples), `${baseName}.wav`)
    }

    messageApi?.success?.('语音已导出')
    return true
  } catch (error) {
    if (error?.name === 'AbortError' || token !== playbackToken) {
      return false
    }
    console.error('语音导出失败:', error)
    messageApi?.error?.(error?.message || '语音导出失败，请稍后重试')
    return false
  } finally {
    if (token === playbackToken) {
      downloadingMessageNo.value = null
    }
  }
}

const setVoice = value => {
  voice.value = value
  writeStorage(TTS_VOICE_STORAGE_KEY, value)
}

const setAutoPlay = value => {
  autoPlay.value = Boolean(value)
  writeStorage(TTS_AUTOPLAY_STORAGE_KEY, autoPlay.value ? '1' : '0')
}

export const useTtsPlayback = () => {
  const dictStore = useDictStore()

  // 音色设计预设选项：来自字典（dict_code=音色），value=描述文案(item_key)，label=展示名(item_label)
  const voiceDesignOptions = computed(() =>
    dictStore.getItems('音色').map(item => ({ value: item.key, label: item.label }))
  )

  return {
    TTS_PLAYBACK_RATES,
    TTS_VOICES,
    voiceDesignOptions,
  autoPlay,
  clearClone,
  cloneSample,
  downloadAudio,
  downloadingMessageNo,
  isPlaying,
  lastAudioId,
  optimizePreview,
  play,
  playSegments,
  playbackRate,
  playingMessageNo,
  resetUsage,
  setAutoPlay,
  setCloneSample,
  setOptimizePreview,
  setPlaybackRate,
  setVoice,
  setVoiceDesign,
  stop,
  togglePlay,
  usage,
  voice,
  voiceDesign
  }
}
