<template>
  <div>
    <NModal
      :show="show"
      preset="card"
      title="分享图片"
      style="width: min(760px, 94vw)"
      :bordered="false"
      @update:show="handleUpdateShow"
    >
      <div class="share-modal">
        <div class="share-modal-controls">
          <span class="share-modal-controls-item">
            <NSwitch size="small" v-model:value="showHeader" />
            <span>显示头部</span>
          </span>
          <span class="share-modal-controls-item">
            <NSwitch size="small" v-model:value="showFooter" />
            <span>显示底部</span>
          </span>
          <span v-if="previousText" class="share-modal-controls-item">
            <NSwitch size="small" v-model:value="showPrevious" />
            <span>包含上一条消息</span>
          </span>
        </div>

        <div class="share-modal-preview">
          <div class="share-card" ref="cardRef">
            <div v-if="showHeader" class="share-card-author">
              <img v-if="avatarUrl" :src="avatarUrl" class="share-card-avatar" alt="" />
              <span v-else class="share-card-avatar share-card-avatar-fallback">{{ senderName.charAt(0) }}</span>
              <div class="share-card-author-info">
                <span class="share-card-author-name">{{ senderName }}</span>
                <span v-if="chatModel && !isUser" class="share-card-author-model">· {{ chatModel }}</span>
              </div>
              <span class="share-card-time">{{ fullTime }}</span>
            </div>

            <div v-if="showHeader" class="share-card-divider"></div>

            <div v-if="showPrevious && previousText" class="share-card-previous">
              <span class="share-card-previous-label">Q</span>
              <span class="share-card-previous-text">{{ previousText }}</span>
            </div>
            <div v-if="showPrevious && previousText" class="share-card-divider"></div>

            <div ref="cardBodyRef" class="share-card-body"></div>

            <div v-if="showFooter" class="share-card-divider"></div>

            <div v-if="showFooter" class="share-card-footer">
              <span class="share-card-footer-left">青松 - 让人生更轻松</span>
              <span class="share-card-footer-right">
                {{ isUser ? '用户分享内容' : '内容由 AI 生成 · 请注意甄别' }}
              </span>
            </div>
          </div>
        </div>

        <div class="share-modal-actions">
          <NButton :loading="generating" :disabled="generating" @click="copyImage">复制图片</NButton>
          <NButton type="primary" :loading="generating" :disabled="generating" @click="downloadImage">下载图片</NButton>
        </div>
        <p class="share-modal-tip">点击「下载图片」或「复制图片」时会生成 PNG，内容与上方卡片一致。</p>
      </div>
    </NModal>
  </div>
</template>

<script setup>
import { NModal, NButton, NSwitch, useMessage } from 'naive-ui'
import { computed, nextTick, ref, watch } from 'vue'
import { readShareCardSettings, writeShareCardSettings } from '../utils/shareCardSettings.js'

const props = defineProps({
  show: {
    type: Boolean,
    default: false
  },
  message: {
    type: Object,
    default: null
  },
  isUser: {
    type: Boolean,
    default: false
  },
  senderName: {
    type: String,
    default: 'AI 助手'
  },
  avatarUrl: {
    type: String,
    default: ''
  },
  chatModel: {
    type: String,
    default: ''
  },
  contentNode: {
    type: [Node, HTMLElement, Object],
    default: null
  },
  previousMessage: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:show'])

const messageApi = useMessage()

const cardRef = ref(null)
const cardBodyRef = ref(null)
const generating = ref(false)
const persistedShareSettings = readShareCardSettings()
const showHeader = ref(persistedShareSettings.showHeader)
const showFooter = ref(persistedShareSettings.showFooter)
const showPrevious = ref(persistedShareSettings.showPrevious)

watch([showHeader, showFooter, showPrevious], () => {
  writeShareCardSettings({
    showHeader: showHeader.value,
    showFooter: showFooter.value,
    showPrevious: showPrevious.value
  })
})
let htmlToImageModule = null

const previousText = computed(() => {
  const content = props.previousMessage?.content
  return typeof content === 'string' && content.trim() ? content : ''
})

const fullTime = computed(() => {
  const ts = props.message?.timestamp
  if (!ts) return ''
  const date = new Date(ts)
  if (Number.isNaN(date.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
})

const loadHtmlToImage = async () => {
  if (!htmlToImageModule) {
    htmlToImageModule = await import('html-to-image')
  }
  return htmlToImageModule
}

const fillCardBody = () => {
  const body = cardBodyRef.value
  if (!body) return
  body.innerHTML = ''
  if (props.contentNode) {
    body.appendChild(props.contentNode)
  }
}

// 图片转 dataURL；失败则替换为占位，避免导出时跨域/坏图导致白图或报错
const blobToDataURL = (blob) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })

const isSameOrigin = (url) => {
  try {
    return new URL(url, window.location.href).origin === window.location.origin
  } catch (error) {
    return false
  }
}

const fetchWithTimeout = (url, timeout = 5000) =>
  new Promise((resolve, reject) => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeout)
    fetch(url, { mode: 'cors', signal: controller.signal })
      .then(resolve)
      .catch(reject)
      .finally(() => clearTimeout(timer))
  })

const prepareCardImages = async () => {
  const card = cardRef.value
  if (!card) return

  const imgs = [...card.querySelectorAll('img')]
  await Promise.all(imgs.map(async (img) => {
    const src = img.getAttribute('src')
    if (!src || /^data:/i.test(src) || /^blob:/i.test(src)) return
    // 同源图片导出工具可直接处理，无需转换
    if (isSameOrigin(src)) return

    let dataUrl = null
    try {
      const res = await fetchWithTimeout(src)
      if (!res.ok) throw new Error(`http ${res.status}`)
      const blob = await res.blob()
      if (blob.type && blob.type.startsWith('image/')) {
        dataUrl = await blobToDataURL(blob)
      }
    } catch (error) {
      console.warn('分享图片：跨域图片加载失败，使用占位', src, error)
    }

    if (dataUrl) {
      img.src = dataUrl
      img.crossOrigin = ''
    } else if (img.classList.contains('share-card-avatar')) {
      // 头像失败 → 换回首字母占位
      const fallback = document.createElement('span')
      fallback.className = 'share-card-avatar share-card-avatar-fallback'
      fallback.textContent = (props.senderName || '青').charAt(0)
      img.replaceWith(fallback)
    } else {
      // 内容图片失败 → 灰色占位，避免截图留白
      const placeholder = document.createElement('div')
      placeholder.className = 'share-img-placeholder'
      placeholder.textContent = '图片加载失败'
      img.replaceWith(placeholder)
    }
  }))
}

const waitForCardImages = () => {
  const imgs = cardRef.value ? [...cardRef.value.querySelectorAll('img')] : []
  const pending = imgs.filter(img => !img.complete)
  if (pending.length === 0) return Promise.resolve()
  return Promise.allSettled(
    pending.map(img =>
      new Promise(resolve => {
        const timer = setTimeout(resolve, 3000)
        img.addEventListener('load', () => { clearTimeout(timer); resolve() }, { once: true })
        img.addEventListener('error', () => { clearTimeout(timer); resolve() }, { once: true })
      })
    )
  )
}

// 代码块/表格横向溢出时等比缩小到卡片宽度，替代滚动条（所见即所得，导出同样生效）
const fitWideElements = () => {
  const card = cardRef.value
  if (!card) return

  const shrinkToFit = (el) => {
    const parent = el.parentElement
    if (!parent) return
    const available = parent.clientWidth
    const scrollW = el.scrollWidth
    if (scrollW <= available + 1) return
    const ratio = Math.min(1, available / scrollW)
    el.style.transformOrigin = 'top left'
    el.style.transform = `scale(${ratio})`
    parent.style.overflow = 'hidden'
    parent.style.height = `${Math.ceil(parent.scrollHeight * ratio)}px`
  }

  // 代码块：缩放 pre（含头部保留全宽，仅压缩正文与整体高度）
  card.querySelectorAll('.code-block-wrapper').forEach(wrapper => {
    const pre = wrapper.querySelector('pre')
    if (!pre) return
    const available = wrapper.clientWidth
    const scrollW = pre.scrollWidth
    if (scrollW <= available + 1) return
    const header = wrapper.querySelector('.code-header')
    const headerH = header ? header.offsetHeight : 0
    const ratio = Math.min(1, available / scrollW)
    pre.style.transformOrigin = 'top left'
    pre.style.transform = `scale(${ratio})`
    wrapper.style.overflow = 'hidden'
    wrapper.style.height = `${Math.ceil(headerH + pre.scrollHeight * ratio)}px`
  })

  // 表格：缩放 table
  card.querySelectorAll('.table-wrapper').forEach(wrapper => {
    const table = wrapper.querySelector('table')
    if (!table) return
    shrinkToFit(table)
  })
}

const generatePng = async () => {
  const { toPng } = await loadHtmlToImage()
  if (!cardRef.value) return null

  // 等卡片内图片加载完成，避免导出空白图
  await waitForCardImages()
  fitWideElements()
  await nextTick()

  const scale = Math.min(window.devicePixelRatio || 1, 2)

  return toPng(cardRef.value, {
    pixelRatio: scale,
    backgroundColor: '#ffffff',
    cacheBust: false,
    // 卡片使用系统字体，跳过 @font-face 抓取，避免字体 404 导致导出失败
    skipFonts: true,
    width: cardRef.value.offsetWidth,
    height: cardRef.value.offsetHeight,
    filter: (node) => {
      // 隐藏分享卡片中不需要的交互元素
      if (node instanceof Element) {
        if (node.classList?.contains('code-copy-btn') ||
            node.classList?.contains('mermaid-toggle-btn') ||
            node.classList?.contains('heading-link')) {
          return false
        }
      }
      return true
    }
  })
}

const open = async () => {
  await nextTick()
  fillCardBody()
  await nextTick()
  // 先处理跨域图片，确保后续导出稳定
  await prepareCardImages()
  fitWideElements()
  await nextTick()
}

watch(() => props.show, (visible) => {
  if (visible) {
    open()
  } else {
    generating.value = false
    if (cardBodyRef.value) {
      cardBodyRef.value.innerHTML = ''
    }
  }
})

const handleUpdateShow = (value) => {
  emit('update:show', value)
}

const downloadImage = async () => {
  if (generating.value) return
  generating.value = true
  try {
    const dataUrl = await generatePng()
    if (!dataUrl) return
    const link = document.createElement('a')
    link.href = dataUrl
    link.download = `青松对话分享_${Date.now()}.png`
    link.click()
    messageApi.success('图片已开始下载')
  } catch (error) {
    console.error('生成分享图片失败:', error)
    messageApi.error('生成图片失败，请重试')
  } finally {
    generating.value = false
  }
}

const copyImage = async () => {
  if (generating.value) return
  if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    messageApi.warning('当前浏览器不支持直接复制图片，请使用下载')
    return
  }

  generating.value = true
  try {
    const dataUrl = await generatePng()
    if (!dataUrl) return
    const blob = await (await fetch(dataUrl)).blob()
    await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
    messageApi.success('图片已复制到剪贴板')
  } catch (error) {
    console.error('复制图片失败:', error)
    messageApi.error('复制失败，请使用下载')
  } finally {
    generating.value = false
  }
}
</script>

<style scoped lang="scss">
.share-card {
  width: 640px;
  max-width: 100%;
  box-sizing: border-box;
  background: linear-gradient(180deg, #ffffff 0%, #fefdfa 100%);
  padding: 40px 48px 36px;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.04), 0 6px 24px rgba(15, 23, 42, 0.06);
  font-family: system-ui, -apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #1f2937;
}

.share-card-top,
.share-card-brand,
.share-card-logo,
.share-card-name-text {
  display: none;
}

.share-card-time {
  margin-left: auto;
  font-size: 12px;
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.share-card-author {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}

.share-card-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12);
}

.share-card-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}

.share-card-author-info {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.share-card-author-name {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
}

.share-card-author-model {
  font-size: 12px;
  color: #94a3b8;
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
}

.share-card-divider {
  height: 1px;
  margin: 18px 0;
  background: linear-gradient(to right, rgba(148, 163, 184, 0.16), rgba(226, 232, 240, 0.34));
}

.share-card-body {
  font-size: 15px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  word-break: break-word;
  color: #314155;
  text-align: justify;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;

  :deep(*) {
    box-sizing: border-box;
  }

  // 克隆进来的消息内容：抹掉聊天气泡背景，统一成文档排版
  :deep(.ai-text),
  :deep(.user-text) {
    background: transparent;
    border: none;
    box-shadow: none;
    padding: 0;
    max-width: none;
    width: 100%;
    white-space: normal;
    text-align: justify;
  }

  :deep(.user-text) {
    color: #314155;
    white-space: pre-wrap;
    text-align: left;
  }

  :deep(> :first-child) {
    margin-top: 0 !important;
  }

  :deep(> :last-child) {
    margin-bottom: 0 !important;
  }

  :deep(.emoji-img),
  :deep(.twemoji-img) {
    display: inline;
    width: 1.2em !important;
    height: 1.2em !important;
    margin: 0;
    padding: 0;
    vertical-align: -0.25em;
    pointer-events: none;
  }

  :deep(svg) {
    max-width: 100%;
    height: auto;
  }

  // 标题层级
  :deep(h1) {
    margin-top: 18px;
    margin-bottom: 10px;
    font-weight: 700;
    font-size: 20px;
    line-height: 1.3;
    color: #0f172a;
  }

  :deep(h2) {
    margin-top: 16px;
    margin-bottom: 10px;
    font-weight: 700;
    font-size: 17px;
    line-height: 1.35;
    color: #111827;
    border-bottom: 1px solid rgba(100, 116, 139, 0.18);
    padding-bottom: 5px;
  }

  :deep(h3) {
    margin-top: 14px;
    margin-bottom: 8px;
    font-weight: 700;
    font-size: 15px;
    line-height: 1.45;
    color: #1f3b57;
  }

  :deep(h4) {
    margin-top: 12px;
    margin-bottom: 8px;
    font-weight: 600;
    font-size: 15px;
    line-height: 1.5;
    color: #1f2937;
  }

  :deep(h5),
  :deep(h6) {
    margin-top: 10px;
    margin-bottom: 6px;
    font-weight: 600;
    font-size: 14px;
    line-height: 1.5;
    color: #374151;
  }

  // 段落
  :deep(p) {
    margin: 0 0 12px;
    line-height: 1.75;
    font-size: 15px;
    color: #314155;
  }

  :deep(li > p) {
    margin: 2px 0;
  }

  // 列表
  :deep(ul) {
    margin: 8px 0;
    padding-left: 1.3em;
    list-style: disc;
    font-size: 15px;
  }

  :deep(ol) {
    margin: 8px 0;
    padding-left: 1.4em;
    list-style: decimal;
  }

  :deep(ul ul),
  :deep(ul ol),
  :deep(ol ul),
  :deep(ol ol) {
    margin: 4px 0 0;
  }

  :deep(ul ul) {
    list-style-type: circle;
  }

  :deep(ul ul ul) {
    list-style-type: square;
  }

  :deep(ol ol) {
    list-style-type: lower-alpha;
  }

  :deep(ol ol ol) {
    list-style-type: lower-roman;
  }

  :deep(li) {
    margin: 3px 0;
    line-height: 1.6;
    color: #314155;
  }

  :deep(ul li::marker),
  :deep(ol li::marker) {
    color: #36536b;
    font-weight: 700;
  }

  // 任务列表
  :deep(li:has(input[type="checkbox"])) {
    display: flex;
    align-items: flex-start;
    gap: 4px;
    margin-left: -1.4em;
    list-style: none;
  }

  :deep(li:has(input[type="checkbox"]) > input[type="checkbox"]) {
    margin-top: 0.35em;
    flex-shrink: 0;
    accent-color: #3b82f6;
    pointer-events: none;
  }

  // 强调与删除线
  :deep(strong) {
    font-weight: 700;
    color: #0f172a;
  }

  :deep(em) {
    font-style: italic;
    color: #334155;
  }

  :deep(del),
  :deep(s) {
    text-decoration: line-through;
    color: #9ca3af;
    opacity: 0.85;
  }

  // 链接
  :deep(a) {
    color: #1d4ed8;
    text-decoration: underline;
    text-decoration-color: rgba(29, 78, 216, 0.24);
    text-underline-offset: 0.2em;
    word-break: break-word;
  }

  // 引用块
  :deep(blockquote) {
    margin: 10px 0;
    padding: 9px 14px;
    border-left: 2px solid #54708a;
    background: #f8fafc;
    border-radius: 0 10px 10px 0;
    color: #475569;
  }

  :deep(blockquote p) {
    margin: 0 !important;
  }

  :deep(blockquote p + p) {
    margin-top: 6px !important;
  }

  // 水平线
  :deep(hr) {
    border: none;
    height: 1px;
    margin: 18px 0;
    background: linear-gradient(to right, transparent, #cbd5e1, transparent);
  }

  // 行内代码
  :deep(code) {
    display: inline;
    background: #f8fafc !important;
    padding: 1px 6px;
    border-radius: 5px;
    font-size: 0.86em;
    font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
    color: #b42318;
    font-weight: 600;
    line-height: 1.4;
    border: 1px solid rgba(203, 213, 225, 0.9);
  }

  // 代码块（深墨底 + 头部语言标签，隐藏复制按钮）
  :deep(.code-block-wrapper) {
    position: relative;
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 10px 0;
    border: 1px solid rgba(148, 163, 184, 0.24);
    border-radius: 8px;
    overflow: hidden;
    background: #1e293b;
  }

  :deep(.code-block-wrapper .code-header) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 14px;
    background: rgba(148, 163, 184, 0.08);
    border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  }

  :deep(.code-block-wrapper .code-lang) {
    font-size: 11px;
    color: #94a3b8;
    font-weight: 500;
  }

  :deep(.code-block-wrapper .code-copy-btn) {
    display: none;
  }

  :deep(pre) {
    width: 100%;
    margin: 0;
    background: #1e293b !important;
    padding: 14px 18px;
    border-radius: 0;
    overflow: visible !important;
    box-sizing: border-box;
  }

  :deep(pre code) {
    display: block;
    padding: 0;
    background: transparent !important;
    border: none;
    color: #e2e8f0;
    font-size: 13px;
    font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
    line-height: 1.6;
    white-space: pre;
    word-break: normal;
    font-weight: 400;
  }

  // 宽元素缩放支撑（fitWideElements 依赖）
  :deep(.code-block-wrapper),
  :deep(pre),
  :deep(.table-wrapper) {
    transform-origin: top left;
  }

  :deep(.code-block-wrapper) {
    overflow: visible !important;
  }

  :deep(.code-block-wrapper pre),
  :deep(.code-block-wrapper code) {
    overflow: visible !important;
    white-space: pre !important;
  }

  // 表格
  :deep(.table-wrapper) {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 10px 0;
    overflow: visible !important;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    background: #fff;
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
    line-height: 1.55;
  }

  :deep(th),
  :deep(td) {
    border: 1px solid #e5e7eb;
    padding: 8px 11px;
    vertical-align: middle;
  }

  :deep(thead th) {
    background: #f8fafc;
    font-weight: 600;
    color: #334155;
    text-align: center;
  }

  :deep(tbody td) {
    color: #334155;
    text-align: left;
  }

  :deep(tbody tr:nth-child(even)) {
    background: #fbfdff;
  }

  // 公式（KaTeX）
  :deep(.math-block) {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 12px 0;
    padding: 10px 14px;
    overflow-x: auto;
    text-align: center;
    background: rgba(99, 102, 241, 0.04);
    border: 1px solid rgba(99, 102, 241, 0.15);
    border-radius: 10px;
  }

  :deep(.math-block .katex) {
    font-size: 1.05em;
  }

  :deep(.math-error) {
    color: #dc2626;
    background: #fef2f2;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 0.9em;
  }

  // 图片
  :deep(.image-wrapper) {
    margin: 12px 0;
    border-radius: 8px;
    overflow: hidden;
    min-height: 80px;
    background-color: #f8fafc;
    border: 1px solid rgba(226, 232, 240, 0.9);
  }

  :deep(img:not(.twemoji-img):not(.emoji-img)) {
    display: block;
    max-width: 100%;
    height: auto;
    object-fit: contain;
  }

  // Mermaid 流程图：整幅铺开、按卡宽缩放，禁止内部滚动/裁剪
  :deep(.mermaid-block) {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 12px 0;
    overflow: hidden;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    background: #fff;
    padding: 12px;
  }

  :deep(.mermaid-block .mermaid-render) {
    display: flex;
    justify-content: center;
    align-items: flex-start;
    width: 100%;
    max-width: 100%;
    overflow: visible !important;
    max-height: none !important;
  }

  :deep(.mermaid-block .mermaid-render svg) {
    display: block;
    width: 100% !important;
    max-width: 100% !important;
    height: auto !important;
    margin-inline: auto;
  }

  :deep(.mermaid-block .mermaid-toolbar) {
    display: none;
  }

  :deep(.mermaid-block .mermaid-source.hidden) {
    display: none !important;
  }

  // 标题锚点链接在分享卡片中隐藏
  :deep(.heading-link) {
    display: none;
  }

  // 禁交互 + 隐藏流式/折叠元素
  :deep(button),
  :deep(a) {
    pointer-events: none;
  }

  :deep(.streaming-code),
  :deep(.incomplete),
  :deep(.incomplete-link) {
    display: none;
  }
}

.share-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-size: 11px;
  letter-spacing: 0.02em;
  color: #9ca3af;
}

.share-card-footer-left {
  font-weight: 600;
  color: #64748b;
}

.share-img-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 80px;
  margin: 8px 0;
  border-radius: 10px;
  background: #f1f5f9;
  border: 1px dashed #cbd5e1;
  color: #94a3b8;
  font-size: 13px;
}

.share-modal-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px 16px;
  margin-bottom: 12px;
}

.share-modal-controls-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
}

.share-card-previous {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-size: 14px;
  line-height: 1.65;
  color: #314155;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.share-card-previous-label {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-top: 2px;
  border-radius: 50%;
  background: #1f3b57;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.share-card-previous-text {
  min-width: 0;
  flex: 1;
}

.share-modal-preview {
  max-height: 62vh;
  overflow: auto;
  border-radius: 12px;
  padding: 16px;
  background:
    linear-gradient(45deg, #f1f5f9 25%, transparent 25%) 0 0 / 20px 20px,
    linear-gradient(45deg, transparent 75%, #f1f5f9 75%) 0 0 / 20px 20px,
    linear-gradient(45deg, transparent 75%, #f1f5f9 75%) 10px -10px / 20px 20px,
    linear-gradient(45deg, #f1f5f9 25%, #f8fafc 25%) 10px -10px / 20px 20px;
}

.share-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.share-modal-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: right;
}
</style>
