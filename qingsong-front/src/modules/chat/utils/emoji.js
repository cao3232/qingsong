// 表情渲染 provider：统一将消息中的 Unicode emoji 渲染为风格统一的图片（或保留原生字体）
// 资源命名约定：大写 HEX、codepoint 以 - 连接、去除 FE0F 变体选择符。
//   - twemoji：public/emoji（本地全量，Twitter 风格）
//   - openmoji：public/emoji-openmoji（由 scripts/copy-openmoji.cjs 生成，CC BY-SA 4.0）
//   - fluent：public/emoji-fluent（由 scripts/build-emoji-fluent.cjs 生成精选集，MIT）
//   - blob：public/emoji-blob（由 scripts/build-trend-emoji.cjs 生成精选集，MIT）
//   - doodle：public/emoji-doodle（由 scripts/build-trend-emoji.cjs 生成精选集，CC BY-SA 4.0）
import emojiRegex from 'emoji-regex'
import { FLUENT_EMOJI_SET } from './fluentEmojiSet.js'
import { TREND_EMOJI_SETS } from './trendEmojiSets.js'

export const EMOJI_PROVIDERS = [
  { key: 'native', label: '系统原生', description: '使用系统字体渲染，风格随设备而异' },
  { key: 'twemoji', label: 'Twemoji', description: 'Twitter 风格，全量覆盖（CC BY 4.0）' },
  { key: 'openmoji', label: 'OpenMoji', description: '手绘彩色风格，全量覆盖（CC BY-SA 4.0）' },
  { key: 'fluent', label: 'Fluent 3D', description: '微软 3D 圆润风，精选常用表情（MIT）' },
  { key: 'blob', label: 'Blob', description: '圆润 Blob 风，精选常用表情（MIT）' },
  { key: 'doodle', label: 'Doodle', description: 'OpenMoji 黑白涂鸦风，精选常用表情（CC BY-SA 4.0）' }
]

export const DEFAULT_EMOJI_PROVIDER = 'native'

// 各 provider 对应的静态资源目录（相对 public/）与替换策略
const PROVIDERS = {
  twemoji: { base: '/emoji/svg', extension: 'svg', lowerCase: true },
  openmoji: { base: '/emoji-openmoji', extension: 'svg' },
  fluent: { base: '/emoji-fluent', extension: 'svg', set: FLUENT_EMOJI_SET },
  blob: { base: '/emoji-blob', extension: 'svg', set: TREND_EMOJI_SETS.blob },
  doodle: { base: '/emoji-doodle', extension: 'svg', set: TREND_EMOJI_SETS.doodle }
}

const EMOJI_IMAGE_RE = emojiRegex()

// 保护已有 <img> 标签，避免 emoji 正则把 alt 文本再次替换成嵌套图片
const IMG_TAG_RE = /<img[^>]*>/g
const IMG_PLACEHOLDER_RE = /\u0000EMOJI_IMG_(\d+)\u0000/g

// 将 emoji 字符串转换为大写 HEX codepoint 序列（去除 FE0F 变体选择符）
const toCodepointHex = emoji => {
  const parts = []
  for (const char of emoji) {
    const hex = char.codePointAt(0).toString(16).toUpperCase()
    if (hex === 'FE0F') continue
    parts.push(hex)
  }
  return parts.join('-')
}

const createEmojiImage = (emoji, hex, providerKey) => {
  const provider = PROVIDERS[providerKey]
  if (!provider) return emoji
  const fileName = provider.lowerCase ? hex.toLowerCase() : hex
  return `<img class="emoji-img" src="${provider.base}/${fileName}.${provider.extension}" alt="${emoji}" loading="lazy" />`
}

/**
 * 将 HTML 文本中的 emoji 按指定 provider 渲染为图片。
 * - native：原样返回
 * - twemoji / openmoji：全量替换
 * - fluent / blob / doodle：仅替换精选集合内的 emoji，未收录的回退为原生字体
 */
export const replaceEmojis = (html, providerKey = DEFAULT_EMOJI_PROVIDER) => {
  if (!html || typeof html !== 'string' || providerKey === 'native') return html
  const provider = PROVIDERS[providerKey]
  if (!provider) return html

  const placeholders = []
  const protectedHtml = html.replace(IMG_TAG_RE, tag => {
    placeholders.push(tag)
    return `\u0000EMOJI_IMG_${placeholders.length - 1}\u0000`
  })

  const replaced = protectedHtml.replace(EMOJI_IMAGE_RE, emoji => {
    const hex = toCodepointHex(emoji)
    if (!hex || (provider.set && !provider.set[hex])) return emoji
    return createEmojiImage(emoji, hex, providerKey)
  })

  return replaced.replace(IMG_PLACEHOLDER_RE, (_match, index) => placeholders[Number(index)] || '')
}
