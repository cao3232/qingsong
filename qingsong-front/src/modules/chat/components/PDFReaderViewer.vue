<template>
  <section class="pdf-reader-viewer">
    <div class="viewer-toolbar">
      <div class="page-controls">
        <button type="button" title="上一页" :disabled="isLoading || pageNumber <= 1" @click="goToPage(pageNumber - 1)">
          <ChevronLeftIcon />
        </button>
        <label class="page-input">
          <span class="sr-only">当前页</span>
          <input :value="pageNumber" type="number" min="1" :max="pageCount || 1" @change="handlePageInput" />
          <span>/ {{ pageCount || '-' }}</span>
        </label>
        <button type="button" title="下一页" :disabled="isLoading || pageNumber >= pageCount" @click="goToPage(pageNumber + 1)">
          <ChevronRightIcon />
        </button>
      </div>
      <div class="zoom-controls">
        <button type="button" title="缩小" :disabled="scale <= 0.3" @click="changeScale(-0.1)">−</button>
        <span>{{ Math.round(scale * 100) }}%</span>
        <button type="button" title="放大" :disabled="scale >= 2" @click="changeScale(0.1)">+</button>
      </div>
    </div>

    <div ref="canvasHost" class="canvas-host">
      <div v-if="isLoading" class="state">正在加载 PDF...</div>
      <div v-else-if="errorMessage" class="state error-state">{{ errorMessage }}</div>
      <canvas ref="canvasRef" aria-label="PDF 页面"></canvas>
    </div>
  </section>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { extractPdfPageText } from '../utils/pdfReaderText.js'
import { buildPdfOutline } from '../utils/pdfReaderOutline.js'

const props = defineProps({
  file: { type: [File, null], default: null },
  pageNumber: { type: Number, default: 1 },
  scale: { type: Number, default: 1.1 }
})

const emit = defineEmits(['page-count', 'page-text', 'page-change', 'scale-change', 'outline', 'text-done', 'load-error', 'render-error'])
const canvasHost = ref(null)
const canvasRef = ref(null)
const pageCount = ref(0)
const isLoading = ref(false)
const errorMessage = ref('')
let pdfDocument = null
let loadingTask = null
let renderTask = null
let loadToken = 0

const goToPage = page => {
  const nextPage = Math.min(Math.max(Number(page) || 1, 1), pageCount.value || 1)
  emit('page-change', nextPage)
}

const handlePageInput = event => goToPage(event.target.value)
const changeScale = amount => emit('scale-change', Math.min(Math.max(props.scale + amount, 0.3), 2))

const destroyDocument = async () => {
  renderTask?.cancel()
  renderTask = null
  if (loadingTask) {
    try { await loadingTask.destroy() } catch {}
    loadingTask = null
  }
  if (pdfDocument) {
    try { await pdfDocument.destroy() } catch {}
    pdfDocument = null
  }
}

const renderPage = async () => {
  if (!pdfDocument || !canvasRef.value) return
  try {
    renderTask?.cancel()
    const page = await pdfDocument.getPage(props.pageNumber)
    const viewport = page.getViewport({ scale: props.scale })
    const outputScale = window.devicePixelRatio || 1
    const canvas = canvasRef.value
    const context = canvas.getContext('2d')
    canvas.width = Math.floor(viewport.width * outputScale)
    canvas.height = Math.floor(viewport.height * outputScale)
    canvas.style.width = `${Math.floor(viewport.width)}px`
    canvas.style.height = `${Math.floor(viewport.height)}px`
    const textContent = await page.getTextContent()
    emit('page-text', { pageNumber: props.pageNumber, text: extractPdfPageText(textContent.items) })
    renderTask = page.render({
      canvasContext: context,
      viewport,
      transform: outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : null
    })
    await renderTask.promise
  } catch (error) {
    if (error?.name === 'RenderingCancelledException') return
    errorMessage.value = 'PDF 页面渲染失败，请重试。'
    emit('render-error', error)
  }
}

const extractAllPageTexts = async () => {
  if (!pdfDocument) return
  for (let number = 1; number <= pdfDocument.numPages; number += 1) {
    const page = await pdfDocument.getPage(number)
    const textContent = await page.getTextContent()
    emit('page-text', { pageNumber: number, text: extractPdfPageText(textContent.items) })
  }
}

// 当前缩放放不下容器时，自动缩小到“适应宽度”（只在超宽时缩小，不放大桌面）
const fitToWidth = async () => {
  const host = canvasHost.value
  if (!host || !pdfDocument) return false
  const containerWidth = host.clientWidth
  if (!containerWidth) return false
  const style = window.getComputedStyle(host)
  const padX = (parseFloat(style.paddingLeft) || 0) + (parseFloat(style.paddingRight) || 0)
  const contentWidth = containerWidth - padX
  if (contentWidth <= 0) return false
  const page = await pdfDocument.getPage(1)
  const viewport = page.getViewport({ scale: 1 })
  if (viewport.width * props.scale <= contentWidth) return false
  const fitScale = Math.min(2, Math.max(0.3, contentWidth / viewport.width))
  const rounded = Math.round(fitScale * 100) / 100
  emit('scale-change', rounded)
  return true
}

const extractOutline = async () => {
  if (!pdfDocument) return []
  try {
    const rawOutline = await pdfDocument.getOutline()
    if (!rawOutline?.length) return []
    const resolvePage = async dest => {
      try {
        if (typeof dest === 'string') {
          dest = await pdfDocument.getDestination(dest)
          if (!dest) return null
        }
        const ref = Array.isArray(dest) ? dest[0] : dest
        if (ref == null) return null
        const index = await pdfDocument.getPageIndex(ref)
        return index + 1
      } catch {
        return null
      }
    }
    return await buildPdfOutline(rawOutline, resolvePage)
  } catch {
    return []
  }
}

const loadFile = async file => {
  const token = ++loadToken
  await destroyDocument()
  pageCount.value = 0
  errorMessage.value = ''
  if (!file) return

  isLoading.value = true
  try {
    const pdfjs = await import('pdfjs-dist')
    pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.mjs', import.meta.url).toString()
    const data = new Uint8Array(await file.arrayBuffer())
    loadingTask = pdfjs.getDocument({ data })
    pdfDocument = await loadingTask.promise
    if (token !== loadToken) return
    pageCount.value = pdfDocument.numPages
    emit('page-count', pageCount.value)
    emit('outline', await extractOutline())
    const fitted = await fitToWidth()
    await extractAllPageTexts()
    emit('text-done')
    if (!fitted) await renderPage()
  } catch (error) {
    if (token !== loadToken) return
    errorMessage.value = 'PDF 加载失败，请确认文件未损坏。'
    emit('load-error', error)
  } finally {
    if (token === loadToken) isLoading.value = false
  }
}

watch(() => props.file, loadFile, { immediate: true })
watch(() => [props.pageNumber, props.scale], renderPage)
onBeforeUnmount(() => { loadToken += 1; destroyDocument() })
</script>

<style scoped lang="scss">
.pdf-reader-viewer { min-width: 0; min-height: 0; display: flex; flex: 1; flex-direction: column; background: #e9edf2; }
.viewer-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 10px 16px; background: #fff; border-bottom: 1px solid #d8dee6; }
.page-controls, .zoom-controls { display: flex; align-items: center; gap: 8px; }
.viewer-toolbar button { width: 30px; height: 30px; border: 1px solid #cbd5e1; background: #fff; color: #334155; cursor: pointer; }
.viewer-toolbar button:disabled { cursor: not-allowed; opacity: .45; }
.viewer-toolbar svg { width: 16px; height: 16px; margin-top: 3px; }
.page-input { display: inline-flex; align-items: center; gap: 6px; color: #64748b; font-size: 13px; }
.page-input input { width: 48px; height: 30px; border: 1px solid #cbd5e1; text-align: center; }
.zoom-controls span { min-width: 46px; color: #64748b; font-size: 13px; text-align: center; }
.canvas-host { position: relative; display: flex; flex: 1; justify-content: center; overflow: auto; padding: 24px; }
canvas { display: block; max-width: none; background: #fff; box-shadow: 0 4px 16px rgba(15, 23, 42, .16); }
.state { position: absolute; inset: 0; display: grid; place-items: center; color: #64748b; font-size: 14px; }
.error-state { color: #b42318; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 640px) { .viewer-toolbar { padding: 8px; } .canvas-host { padding: 12px 6px 72px; } }
</style>
