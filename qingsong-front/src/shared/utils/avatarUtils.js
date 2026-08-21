/**
 * DiceBear avatar generation helpers.
 */

const DICEBEAR_STYLES = {
  USER: 'avataaars',
  AI: 'bottts',
  USER_ALT: 'miniavs',
  AI_ALT: 'pixel-art'
}

const DICEBEAR_BASE_URL = 'https://api.dicebear.com/10.x'

// 默认背景色（非透明时使用）
const DEFAULT_BG = 'b6e3f4'

function hashCode(str) {
  let hash = 0
  if (str.length === 0) return hash
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash
  }
  return Math.abs(hash)
}

/**
 * 根据配置对象拼装 DiceBear 头像 URL（系统设置页 / 导航栏 / 聊天消息共用的单一真源）。
 * 注意：10.x 不再接受 backgroundColor=transparent（400），透明背景需省略该参数。
 * @param {{style?:string, seed?:string, transparent?:boolean, backgroundColor?:string}} cfg
 * @returns {string}
 */
export function buildAvatarUrl(cfg) {
  const style = cfg?.style || DICEBEAR_STYLES.USER
  const seed = cfg?.seed || 'default'
  const params = new URLSearchParams()
  params.set('seed', seed)
  if (!cfg?.transparent) {
    const bg = (cfg?.backgroundColor || '').replace('#', '')
    params.set('backgroundColor', bg || DEFAULT_BG)
  }
  return `${DICEBEAR_BASE_URL}/${style}/svg?${params.toString()}`
}

export function generateUserAvatar(userId, options = {}) {
  const { style = DICEBEAR_STYLES.USER } = options
  const seed = String(userId || 'default-user')
  return `${DICEBEAR_BASE_URL}/${style}/svg?seed=${encodeURIComponent(seed)}`
}

export function generateAIAvatar(aiId, options = {}) {
  const { style = DICEBEAR_STYLES.AI } = options
  const seed = String(aiId || 'default-ai')
  return `${DICEBEAR_BASE_URL}/${style}/svg?seed=${encodeURIComponent(seed)}`
}

export function generateChatAvatar(chatId, role, options = {}) {
  const style = role === 'user' ? DICEBEAR_STYLES.USER : DICEBEAR_STYLES.AI
  const seed = `${chatId}-${role}`
  return `${DICEBEAR_BASE_URL}/${style}/svg?seed=${encodeURIComponent(seed)}`
}

export function generateRandomAvatar(role, options = {}) {
  const seed = `${Date.now()}-${Math.random().toString(36).substring(7)}`
  const style = role === 'user' ? DICEBEAR_STYLES.USER : DICEBEAR_STYLES.AI
  return `${DICEBEAR_BASE_URL}/${style}/svg?seed=${encodeURIComponent(seed)}`
}

export function getAvailableStyles(type = 'all') {
  const userStyles = [
    { name: 'avataaars', label: '经典扁平', description: '经典的扁平化小人风格' },
    { name: 'miniavs', label: '极简风', description: '极简的小人风格' },
    { name: 'adventurer', label: '冒险者', description: '游戏角色冒险者风格' },
    { name: 'adventurer-neutral', label: '中性冒险者', description: '更朴素克制的冒险者风格' },
    { name: 'lorelei', label: '现代风', description: '现代女性 / 中性风格' },
    { name: 'lorelei-neutral', label: '现代中性', description: '简洁的中性插画风格' },
    { name: 'notionists', label: '手绘风', description: 'Notion 风格手绘头像' },
    { name: 'notionists-neutral', label: '素雅手绘', description: '去饱和色调的手绘风格' },
    { name: 'big-smile', label: '大脸微笑', description: '圆脸微笑卡通' },
    { name: 'croodles', label: '涂鸦卡通', description: '线条涂鸦漫画风格' },
    { name: 'micah', label: '插画风', description: '可爱的扁平插画头像' },
    { name: 'open-peeps', label: '人物素描', description: '手绘人物肖像风格' },
    { name: 'personas', label: '拟人角色', description: '3D 拟人角色风格' },
    { name: 'fun-emoji', label: '趣味表情', description: '圆润的 emoji 风格' },
    { name: 'toon-head', label: '卡通大头', description: '夸张的卡通大头风格' },
    { name: 'initials', label: '首字母', description: '纯色底 + 首字母' },
    { name: 'glass', label: '玻璃拟态', description: '毛玻璃质感的字母头像' }
  ]

  const aiStyles = [
    { name: 'bottts', label: '机器人', description: '零件组装的机器人' },
    { name: 'bottts-neutral', label: '素色机器人', description: '去饱和的机器人风格' },
    { name: 'pixel-art', label: '像素风', description: '复古 8-bit 像素风格' },
    { name: 'pixel-art-neutral', label: '像素黑白', description: '黑白像素风格' },
    { name: 'identicon', label: '几何图形', description: 'GitHub 默认几何对称图形' },
    { name: 'rings', label: '圆形纹理', description: '抽象的圆形纹理' },
    { name: 'thumbs', label: '竖起拇指', description: '拟人手掌风格' },
    { name: 'shapes', label: '形状', description: '扁平几何形状组合' },
    { name: 'shape-grid', label: '网格形状', description: '规则网格上的形状组合' },
    { name: 'stripes', label: '条纹', description: '线性条纹纹理' },
    { name: 'triangles', label: '三角', description: '三角形拼接纹理' },
    { name: 'glyphs', label: '字形', description: '抽象字形符号' },
    { name: 'icons', label: '图标', description: '简洁的线性图标' },
    { name: 'disco', label: '迪斯科', description: '复古迪斯科球风格' },
    { name: 'big-ears', label: '大耳朵', description: '可爱的外星小怪物' },
    { name: 'big-ears-neutral', label: '素色大耳', description: '去饱和的小怪物风格' }
  ]

  if (type === 'user') return userStyles
  if (type === 'ai') return aiStyles
  return [...userStyles, ...aiStyles]
}

export function preloadAvatar(url) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(url)
    img.onerror = reject
    img.src = url
  })
}

export default {
  buildAvatarUrl,
  generateUserAvatar,
  generateAIAvatar,
  generateChatAvatar,
  generateRandomAvatar,
  getAvailableStyles,
  preloadAvatar,
  DICEBEAR_STYLES,
  DICEBEAR_BASE_URL,
  hashCode
}
