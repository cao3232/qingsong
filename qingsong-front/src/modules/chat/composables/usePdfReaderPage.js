import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useTtsPlayback } from './useTtsPlayback.js'
import { splitPdfTextIntoSegments, findStartIndexFromPage } from '../utils/pdfReaderText.js'
import {
  getConfiguredTtsApiKey,
  setConfiguredTtsApiKey,
  listRecentPdfs,
  saveRecentPdf,
  removeRecentPdf,
  clearRecentPdfs
} from '../services/index.js'

export const usePdfReaderPage = () => {
  const router = useRouter()
  const file = ref(null)
  const pageNumber = ref(1)
  const pageCount = ref(0)
  const scale = ref(1.1)
  const pageTexts = ref({})
  const outline = ref([])
  const recentPdfs = ref([])
  const showOutline = ref(false)
  const isDragging = ref(false)
  const errorMessage = ref('')
  const fileInputRef = ref(null)
  const apiKey = ref(getConfiguredTtsApiKey())
  const {
    cloneSample,
    isPlaying,
    playbackRate,
    setPlaybackRate,
    setVoice,
    setVoiceDesign,
    stop,
    playSegments,
    TTS_PLAYBACK_RATES,
    TTS_VOICES,
    voice,
    voiceDesign
  } = useTtsPlayback()

  const fileName = computed(() => file.value?.name || '')
  const currentText = computed(() => pageTexts.value[pageNumber.value] || '')
  // 单次 TTS 请求控制在 300 字内，避免接口超长截断导致少字/跳句
  const allSegments = computed(() => Object.entries(pageTexts.value)
    .sort(([a], [b]) => Number(a) - Number(b))
    .flatMap(([page, text]) => splitPdfTextIntoSegments(text, 300).map(content => ({ page: Number(page), content }))))
  const progressText = computed(() => {
    if (!allSegments.value.length) return '暂无可朗读文本'
    return `${allSegments.value.length} 段文字`
  })

  const flatOutline = computed(() => {
    const result = []
    const walk = (items, depth) => {
      for (const item of items) {
        result.push({ title: item.title, page: item.page, depth })
        walk(item.children || [], depth + 1)
      }
    }
    walk(outline.value, 0)
    return result
  })

  const openFile = nextFile => {
    if (!nextFile) return
    if (nextFile.type !== 'application/pdf' && !nextFile.name.toLowerCase().endsWith('.pdf')) {
      errorMessage.value = '请选择 PDF 文件'
      return
    }
    stop()
    file.value = nextFile
    pageNumber.value = 1
    pageCount.value = 0
    pageTexts.value = {}
    outline.value = []
    showOutline.value = false
    errorMessage.value = ''
    resetExtraction()
  }

  // 关闭当前文件，回到阅读器首页（可看到最近阅读列表）
  const closeFile = () => {
    stop()
    file.value = null
    pageNumber.value = 1
    pageCount.value = 0
    pageTexts.value = {}
    outline.value = []
    showOutline.value = false
    errorMessage.value = ''
    resetExtraction()
  }

  // —— 全文提取完成前，禁止用“半截快照”播放 ——
  let extractionDone = true
  const extractionWaiters = []
  const resetExtraction = () => { extractionDone = false }
  const onTextDone = () => {
    extractionDone = true
    extractionWaiters.splice(0).forEach(resolve => resolve())
  }
  const waitForExtraction = (timeoutMs = 60000) => new Promise(resolve => {
    if (extractionDone) return resolve()
    let settled = false
    const timer = setTimeout(() => {
      if (!settled) { settled = true; resolve() }
    }, timeoutMs)
    const finish = () => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve()
    }
    extractionWaiters.push(finish)
  })
  const handleLoadError = () => {
    errorMessage.value = 'PDF 加载失败，请重新选择文件'
    onTextDone()
  }

  const handleFileInput = event => openFile(event.target.files?.[0])
  const handleDrop = event => { isDragging.value = false; openFile(event.dataTransfer.files?.[0]) }
  const setPageText = ({ pageNumber: page, text }) => { pageTexts.value = { ...pageTexts.value, [page]: text } }
  const setPageCount = count => {
    pageCount.value = count
    // 打开成功（pdfjs 已解析文档）即写入最近阅读，便于下次快捷打开
    if (file.value) saveRecentPdf(file.value).then(refreshRecentList)
  }
  const refreshRecentList = async () => { recentPdfs.value = await listRecentPdfs() }
  const openRecent = async id => {
    const row = recentPdfs.value.find(item => item.id === id)
    if (!row?.blob) return
    openFile(new File([row.blob], row.name, { type: 'application/pdf' }))
  }
  const removeRecent = async id => {
    await removeRecentPdf(id)
    await refreshRecentList()
  }
  const clearRecent = async () => {
    await clearRecentPdfs()
    await refreshRecentList()
  }
  const setPage = page => {
    const nextPage = Math.min(Math.max(Number(page) || 1, 1), pageCount.value || 1)
    if (nextPage === pageNumber.value) return
    stop()
    pageNumber.value = nextPage
  }
  const setScale = value => { scale.value = value }
  const setOutline = value => {
    outline.value = value || []
    if (outline.value.length) showOutline.value = true
  }
  const toggleOutline = () => { showOutline.value = !showOutline.value }
  const jumpToOutline = entry => {
    if (!entry?.page) return
    stop()
    pageNumber.value = entry.page
  }
  const selectFile = () => fileInputRef.value?.click()

  const saveApiKey = value => {
    setConfiguredTtsApiKey(value)
    apiKey.value = getConfiguredTtsApiKey()
    errorMessage.value = apiKey.value
      ? 'TTS API Key 已保存'
      : '已清除自定义 Key，恢复使用内置 Key（内置 Key 可能失效）'
  }

  const play = async () => {
    errorMessage.value = '正在提取全文文本，请稍候…'
    await waitForExtraction()
    errorMessage.value = ''
    if (!allSegments.value.length) {
      errorMessage.value = currentText.value ? '正在提取 PDF 文本，请稍后再试' : '该 PDF 没有可提取文本，暂不支持朗读'
      return
    }
    const startAt = findStartIndexFromPage(allSegments.value, pageNumber.value)
    await playSegments(allSegments.value.slice(startAt).map(item => item.content), {
      onSegmentStart: (segment, segmentIndex) => {
        const item = allSegments.value[startAt + segmentIndex]
        if (item) pageNumber.value = item.page
      },
      // 阅读器强制关闭“智能优化”，避免接口改写/压缩播报文本导致跳句子
      optimizeTextPreview: false,
      messageApi: {
        warning: msg => { errorMessage.value = msg },
        error: msg => { errorMessage.value = msg }
      }
    })
  }

  const goBack = () => { stop(); router.back() }
  onUnmounted(stop)

  // 进入阅读器即加载最近阅读记录
  refreshRecentList()

  return {
    apiKey, clearRecent, closeFile, errorMessage, file, fileInputRef, fileName, flatOutline, handleDrop,
    handleFileInput, handleLoadError, isDragging, jumpToOutline, cloneSample, isPlaying, onTextDone, openRecent,
    pageNumber, play, playbackRate, progressText, recentPdfs, removeRecent, saveApiKey, selectFile, setPage,
    setPageCount, setPageText, setPlaybackRate, setScale, setVoice, setVoiceDesign, setOutline, showOutline,
    scale, stop, toggleOutline, TTS_PLAYBACK_RATES, TTS_VOICES, voice, voiceDesign, goBack
  }
}
