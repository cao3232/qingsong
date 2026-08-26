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
const isPaused = ref(false)
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
// 各 segment 首个音频块的 onSegmentStart 定时器：必须各自记录，
// 否则后一个 segment 的定时器会覆盖前一个尚未触发的，导致翻页/高亮跳段
let segmentTimers = []
let lastScheduledSegmentIndex = -1
// 段间停顿记录：标记“该段已追加过段间停顿”，避免同一段多个 chunk 重复追加
let lastGapSegmentIndex = -1
// 是否仍在合成/调度中：合成追赶播放时（后续段尚未调度）音频队列会短暂为空，
// 此时不能把 isPlaying 置为 false，否则短段播完后的段间间隙会被误判为“播放结束”
let synthesizing = false

// 用于 pause/resume：保存当前播放任务的分段数组与配置，并记录被暂停时的段索引
let currentSegments = []
let currentPlayOptions = {}
let resumeSegmentIndex = 0

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
// segmentGapMs > 0 时，每段首个音频块在队列末尾追加段间停顿，实现“一段播完再下一段”
const scheduleChunk = (samples, { onSegmentStart, segment, segmentIndex, token, segmentGapMs = 0 } = {}) => {
  if (!samples || samples.length === 0) return

  const ctx = getAudioContext()
  const buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate)
  buffer.copyToChannel(samples, 0)

  const source = ctx.createBufferSource()
  source.buffer = buffer
  source.connect(ctx.destination)

  const rate = playbackRate.value || 1
  source.playbackRate.value = rate

  if (segmentGapMs > 0 && segmentIndex !== lastGapSegmentIndex) {
    lastGapSegmentIndex = segmentIndex
    if (nextStartTime > 0) nextStartTime += segmentGapMs / 1000
  }

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
    // 仅在真正结束时（无合成任务、队列已空）才置为停止，段间间隙不触发
    if (pendingSourceCount <= 0 && !synthesizing) {
      playingMessageNo.value = null
      isPlaying.value = false
      isPaused.value = false
    }
  }

  if (typeof onSegmentStart === 'function' && segmentIndex !== lastScheduledSegmentIndex) {
    lastScheduledSegmentIndex = segmentIndex
    const delayMs = Math.max(0, Math.ceil((startAt - ctx.currentTime) * 1000))
    const timer = setTimeout(() => {
      const index = segmentTimers.indexOf(timer)
      if (index !== -1) segmentTimers.splice(index, 1)
      if (token === playbackToken) onSegmentStart(segment, segmentIndex)
    }, delayMs)
    segmentTimers.push(timer)
  }
}

const finishPlayback = () => {
  playingMessageNo.value = null
  isPlaying.value = false
  isPaused.value = false
  abortController = null
}

const pause = () => {
  if (!isPlaying.value || isPaused.value) return
  isPaused.value = true
  isPlaying.value = false
  // 暂停时立即中止 SSE 流式合成，避免后台继续消耗 token 并推送字幕/音频
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  const ctx = getAudioContext()
  if (ctx.state === 'running') {
    ctx.suspend()
  }
}

const resume = async () => {
  if (!isPaused.value) return
  isPaused.value = false
  isPlaying.value = true
  const ctx = getAudioContext()
  if (ctx.state === 'suspended') {
    await ctx.resume()
  }
  // 从暂停时的段索引继续合成与播放
  if (!currentSegments.length || resumeSegmentIndex < 0 || resumeSegmentIndex >= currentSegments.length) {
    finishPlayback()
    return
  }
  const token = ++playbackToken
  try {
    const remaining = currentSegments.slice(resumeSegmentIndex)
    const { onSegmentStart, messageApi, optimizeTextPreview, segmentGapMs } = currentPlayOptions
    const { scheduledCount } = await streamSegments(remaining, token, {
      mode: 'play',
      onSegmentStart,
      optimizeTextPreview,
      segmentGapMs,
      segmentIndexOffset: resumeSegmentIndex
    })
    if (token !== playbackToken) return
    if (scheduledCount === 0) {
      currentPlayOptions.messageApi?.warning?.('未获取到语音数据，请检查 TTS 配置')
      finishPlayback()
      return false
    }
    await waitForPlaybackEnd(token)
    if (token !== playbackToken) return
    finishPlayback()
    return true
  } catch (error) {
    if (error?.name !== 'AbortError' && token === playbackToken) {
      currentPlayOptions.messageApi?.error?.(error?.message || '语音播放失败，请稍后重试')
    }
    stop()
    return false
  }
}

const stop = () => {
  playbackToken += 1
  synthesizing = false
  isPaused.value = false
  currentSegments = []
  currentPlayOptions = {}
  resumeSegmentIndex = 0
  if (segmentTimers.length) {
    segmentTimers.forEach(timer => clearTimeout(timer))
    segmentTimers = []
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
// segmentGapMs > 0：段与段之间插入停顿（一段播完再下一段）
const streamSegments = async (segments, token, { mode = 'play', onSegmentStart, optimizeTextPreview, segmentGapMs = 0, segmentIndexOffset = 0 } = {}) => {
  const { model, voice: voiceParam, voiceDesign, optimizeTextPreview: defaultOptimize } = resolveRequestParams()
  const effectiveOptimize = optimizeTextPreview ?? defaultOptimize
  const collected = mode === 'collect' ? [] : null
  let totalSamples = 0
  let scheduledCount = 0
  lastScheduledSegmentIndex = -1
  lastGapSegmentIndex = -1
  synthesizing = true

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
        // segmentIndexOffset 保证 resume 后 onSegmentStart 仍返回原数组索引
        const absoluteIndex = segmentIndex + segmentIndexOffset
        scheduleChunk(decodePcmChunk(audio.data), { onSegmentStart, segment, segmentIndex: absoluteIndex, token, segmentGapMs })
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

  try {
    for (const [segmentIndex, segment] of segments.entries()) {
      if (token !== playbackToken) break
      resumeSegmentIndex = segmentIndex + segmentIndexOffset

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
  } finally {
    synthesizing = false
  }

  return { collected, totalSamples, scheduledCount }
}

// 等待播放队列播空。仅以 pendingSourceCount 为准（不依赖 isPlaying）：
// isPlaying 可能因段间间隙/边界状态未及时复位，若一并判断会导致提前结束或卡死。
const waitForPlaybackEnd = token => new Promise(resolve => {
  const check = () => {
    if (token !== playbackToken || pendingSourceCount === 0) {
      resolve()
      return
    }
    window.setTimeout(check, 80)
  }
  check()
})

const playSegments = async (segments, { playingId, onSegmentStart, messageApi, optimizeTextPreview, segmentGapMs = 0 } = {}) => {
  const validSegments = (segments || []).filter(Boolean)
  if (!validSegments.length) return false
  stop()
  currentSegments = validSegments
  currentPlayOptions = { onSegmentStart, messageApi, optimizeTextPreview, segmentGapMs }
  resumeSegmentIndex = 0
  if (playingId != null) playingMessageNo.value = playingId
  const token = ++playbackToken
  isPlaying.value = true
  try {
    const ctx = getAudioContext()
    if (ctx.state === 'suspended') {
      await ctx.resume()
    }
    const { scheduledCount } = await streamSegments(validSegments, token, { mode: 'play', onSegmentStart, optimizeTextPreview, segmentGapMs, segmentIndexOffset: 0 })
    if (token !== playbackToken) return false
    // 用“调度过音频块数量”判断是否有声音，而不是当前队列是否为空：
    // 播放进度可能快于合成进度，队列在段间间隙短暂为空是正常现象，不代表没有语音
    if (scheduledCount === 0) {
      messageApi?.warning?.('未获取到语音数据，请检查 TTS 配置')
      return false
    }
    await waitForPlaybackEnd(token)
    if (token !== playbackToken) return false
    // 统一收尾：无论是否经历段间间隙，播放队列播空即视为播放结束
    finishPlayback()
    return true
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

  // 长文本分段合成：每段流式请求后连续调度播放（自动启用克隆音色）
  // 强制关闭“智能优化（optimize_text_preview）”：该功能会改写/压缩播报文本，造成跳句子/少读，
  // 与阅读器行为保持一致，朗读必须按原文逐字读完整条消息
  const segments = splitTextIntoSegments(plainText, TTS_MAX_SEGMENT_CHARS)
  await playSegments(segments, {
    playingId: message.messageNo || message.id || null,
    messageApi,
    optimizeTextPreview: false
  })
}

const togglePlay = (message, messageApi) => {
  const targetId = message?.messageNo || message?.id
  if (isPaused.value && playingMessageNo.value === targetId) {
    resume()
    return
  }
  if (isPlaying.value && playingMessageNo.value === targetId) {
    pause()
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
    isPaused,
    isPlaying,
    lastAudioId,
    optimizePreview,
    pause,
    play,
    playSegments,
    playbackRate,
    playingMessageNo,
    resetUsage,
    resume,
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
