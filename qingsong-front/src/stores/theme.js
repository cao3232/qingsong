import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

// 简单的防抖函数，避免过于频繁地写入 localStorage
function debounce(fn, delay) {
  let timeoutID = null
  return function (...args) {
    clearTimeout(timeoutID)
    timeoutID = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

// 16进制颜色转RGB的辅助函数
function hexToRgb(hex) {
  if (!hex || typeof hex !== 'string') return '255, 255, 255'
  
  let hexValue = hex.replace('#', '')
  
  if (hexValue.length === 3) {
    hexValue = hexValue.split('').map(char => char + char).join('')
  }
  
  if (hexValue.length === 6) {
    const r = parseInt(hexValue.substring(0, 2), 16)
    const g = parseInt(hexValue.substring(2, 4), 16)
    const b = parseInt(hexValue.substring(4, 6), 16)
    return `${r}, ${g}, ${b}`
  }
  
  // 对于 'rgba(r, g, b, a)' 格式，尝试提取 rgb 部分
  const rgbaMatch = hex.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/)
  if (rgbaMatch) {
    return `${rgbaMatch[1]}, ${rgbaMatch[2]}, ${rgbaMatch[3]}`
  }

  return '255, 255, 255'; // 默认返回白色RGB
}

// 辅助函数：将RGB或RGBA字符串转换为RGB字符串
function toRgbString(color) {
  if (!color) return '255, 255, 255';
  
  // 如果已经是RGB或RGBA格式，提取RGB部分
  const rgbMatch = color.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (rgbMatch) {
    return `${rgbMatch[1]}, ${rgbMatch[2]}, ${rgbMatch[3]}`;
  }
  
  // 如果是hex颜色，转换为RGB
  if (color.startsWith('#')) {
    return hexToRgb(color);
  }
  
  // 如果是CSS颜色名称，可以考虑添加映射，但目前先返回默认
  return '255, 255, 255';
}

export const useThemeStore = defineStore('theme', () => {
  // --- 从 ConfigView.vue 迁移的预设数据 ---
  const backgroundPresets = ref([
    // 基础主题 (10个)
    { name: '默认渐变', value: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)', isDark: false, category: 'basic' },
    { name: '浅灰色', value: '#f8f9fa', isDark: false, category: 'basic' },
    { name: '深灰色', value: '#343a40', isDark: true, category: 'basic' },
    { name: '暗夜模式', value: 'linear-gradient(135deg, #2c3e50 0%, #34495e 100%)', isDark: true, category: 'basic' },
    { name: '纯白色', value: '#ffffff', isDark: false, category: 'basic' },
    { name: '象牙白', value: '#f5f5dc', isDark: false, category: 'basic' },
    { name: '午夜黑', value: '#1a1a2e', isDark: true, category: 'basic' },
    { name: '石墨灰', value: 'linear-gradient(135deg, #3d4e81 0%, #5a6a9a 100%)', isDark: true, category: 'basic' },
    { name: '暖灰调', value: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)', isDark: false, category: 'basic' },
    { name: '冷灰调', value: 'linear-gradient(135deg, #909497 0%, #212f3d 100%)', isDark: true, category: 'basic' },
    
    // 自然风光 (10个)
    { name: '天空蓝', value: 'linear-gradient(135deg, #9eceff 0%, #53a9eb 100%)', isDark: false, category: 'nature' },
    { name: '薄荷绿', value: 'linear-gradient(135deg, #55efc4 0%, #00b894 100%)', isDark: false, category: 'nature' },
    { name: '森林绿', value: 'linear-gradient(135deg, #54bf90 0%, #77cfb3 100%)', isDark: false, category: 'nature' },
    { name: '海洋蓝', value: 'linear-gradient(135deg, #49a6be 0%, #87ddf0 100%)', isDark: false, category: 'nature' },
    { name: '翡翠绿', value: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)', isDark: false, category: 'nature' },
    { name: '青草地', value: 'linear-gradient(to right, #83a4d4, #b6fbff)', isDark: false, category: 'nature' },
    { name: '森林晨雾', value: 'linear-gradient(135deg, #c1dfc4 0%, #deecdd 100%)', isDark: false, category: 'nature' },
    { name: '云雾山涧', value: 'linear-gradient(180deg, #E0EAFC 0%, #CFDEF3 100%)', isDark: false, category: 'nature' },
    { name: '春日花园', value: 'linear-gradient(135deg, #96fbc4 0%, #f9f586 100%)', isDark: false, category: 'nature' },
    { name: '秋日暖阳', value: 'linear-gradient(135deg, #fddb92 0%, #d1fdff 100%)', isDark: false, category: 'nature' },
    
    // 缤纷色彩 (10个)
    { name: '紫罗兰', value: 'linear-gradient(135deg, #706baf 0%, #4b3f9f 100%)', isDark: true, category: 'colorful' },
    { name: '珊瑚橙', value: 'linear-gradient(135deg, #fea9c7 0%, #f087ba 100%)', isDark: false, category: 'colorful' },
    { name: '日落橙', value: 'linear-gradient(135deg, #ff9a9e 0%, #fecfef 50%, #fecfef 100%)', isDark: false, category: 'colorful' },
    { name: '极光紫', value: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', isDark: false, category: 'colorful' },
    { name: '樱花粉', value: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)', isDark: false, category: 'colorful' },
    { name: '薰衣草', value: 'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)', isDark: false, category: 'colorful' },
    { name: '玫瑰金', value: 'linear-gradient(135deg, #89f7fe 0%, #66a6ff 100%)', isDark: false, category: 'colorful' },
    { name: '晨曦黄', value: 'linear-gradient(135deg, #fdbb2d 0%, #22c1c3 100%)', isDark: false, category: 'colorful' },
    { name: '梦幻紫', value: 'linear-gradient(135deg, #8e2de2 0%, #4a00e0 100%)', isDark: true, category: 'colorful' },
    { name: '夏日橙', value: 'linear-gradient(135deg, #ffb089 0%, #ff8386 100%)', isDark: false, category: 'colorful' },
    
    // 游戏主题 (10个)
    { name: '王者荣耀', value: 'linear-gradient(135deg, #1e3c72 0%, #2a5298 50%, #f1c40f 100%)', isDark: true, category: 'game' },
    { name: '穿越火线', value: 'linear-gradient(135deg, #2c3e50 0%, #c0392b 100%)', isDark: true, category: 'game' },
    { name: '星露谷', value: 'linear-gradient(135deg, #78e08f 0%, #38ada9 100%)', isDark: false, category: 'game' },
    { name: '原神', value: 'linear-gradient(135deg, #dceefb 0%, #a2d2ff 100%)', isDark: false, category: 'game' },
    { name: '赛博朋克', value: 'linear-gradient(135deg, #fce38a 0%, #f38181 100%)', isDark: false, category: 'game' },
    { name: '我的世界', value: 'linear-gradient(135deg, #6ab04c 0%, #badc58 100%)', isDark: false, category: 'game' },
    { name: '英雄联盟', value: 'linear-gradient(135deg, #53a9eb 0%, #9eceff 100%)', isDark: false, category: 'game' },
    { name: '绝地求生', value: 'linear-gradient(135deg, #f67567 0%, #f9cc6c 100%)', isDark: false, category: 'game' },
    { name: '守望先锋', value: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)', isDark: false, category: 'game' },
    { name: '黑暗之魂', value: 'linear-gradient(135deg, #232526 0%, #414345 100%)', isDark: true, category: 'game' },
    
    // 国风雅韵 (10个)
    { name: '柔和丁香', value: 'linear-gradient(135deg, #d299c2 0%, #fef9d7 100%)', isDark: false, category: 'chinese' },
    { name: '胭脂粉', value: 'linear-gradient(135deg, #ff91a3 0%, #ff98c2 100%)', isDark: false, category: 'chinese' },
    { name: '淡雅青瓷', value: 'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)', isDark: false, category: 'chinese' },
    { name: '松风水月', value: 'linear-gradient(180deg, #E8F3E8 0%, #A4D7E1 100%)', isDark: false, category: 'chinese' },
    { name: '湖光山色', value: 'linear-gradient(180deg, #c1dfc4 0%, #deecdd 100%)', isDark: false, category: 'chinese' },
    { name: '烟雨江南', value: 'linear-gradient(to top, #cfd9df 0%, #e2ebf0 100%)', isDark: false, category: 'chinese' },
    { name: '紫气东来', value: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)', isDark: false, category: 'chinese' },
    { name: '水墨丹青', value: 'linear-gradient(180deg, #97938f 0%, #222f39 100%)', isDark: true, category: 'chinese' },
    { name: '沧海桑田', value: 'linear-gradient(135deg, #2c3e50 0%, #4ca1af 100%)', isDark: true, category: 'chinese' },
    { name: '荷塘月色', value: 'linear-gradient(to top, #0089ad 0%, #003fa1 100%)', isDark: true, category: 'chinese' },
    
    // 其他风格 (10个)
    { name: '深海蓝', value: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', isDark: true, category: 'other' },
    { name: '宁静蓝', value: 'linear-gradient(to right, #a4b3fd, #fd9cb0)', isDark: false, category: 'other' },
    { name: '清新绿', value: 'linear-gradient(to right, #43e97b, #38f9d7)', isDark: false, category: 'other' },
    { name: '活力橙', value: 'linear-gradient(to right, #fa643b, #fade56)', isDark: false, category: 'other' },
    { name: '优雅紫', value: 'linear-gradient(to right, #8092c2, #302172)', isDark: true, category: 'other' },
    { name: '甜美粉', value: 'linear-gradient(to right, #ff7e5f, #feb47b)', isDark: false, category: 'other' },
    { name: '深空灰', value: 'linear-gradient(to right, #434343, #000000)', isDark: true, category: 'other' },
    { name: '日出之光', value: 'linear-gradient(to right, #ff876f, #f5b860)', isDark: false, category: 'other' },
    { name: '护眼豆沙', value: 'linear-gradient(135deg, #f5e6d3 0%, #d4a574 100%)', isDark: false, category: 'other' },
    { name: '静谧之蓝', value: 'linear-gradient(135deg, #5265bb 0%, #3d4fa8 100%)', isDark: true, category: 'other' }
  ])

  const panelBackgroundPresets = ref([
    { name: '明亮玻璃', value: 'rgba(255, 255, 255, 0.5)' },
    { name: '深邃玻璃', value: 'rgba(0, 0, 0, 0.3)' },
    { name: '清透', value: 'rgba(255, 255, 255, 0.2)' },
    { name: '朦胧', value: 'rgba(255, 255, 255, 0.7)' },
    { name: '深沉', value: 'rgba(0, 0, 0, 0.5)' },
  ])

  const chatBackgroundPresets = ref([
    { name: '纯净白', value: 'rgba(255, 255, 255, 0.95)' },
    { name: '温暖米', value: 'rgba(254, 252, 232, 0.95)' },
    { name: '清新绿', value: 'rgba(240, 253, 244, 0.95)' },
    { name: '宁静蓝', value: 'rgba(239, 246, 255, 0.95)' },
    { name: '优雅紫', value: 'rgba(250, 245, 255, 0.95)' },
    { name: '柔和粉', value: 'rgba(255, 242, 242, 0.95)' },
    { name: '深色模式', value: 'rgba(30, 41, 59, 0.95)' },
    { name: '透明玻璃', value: 'rgba(255, 255, 255, 0.1)' },
    { name: '晨曦渐变', value: 'linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(250, 250, 250, 0.85) 100%)' },
    { name: '海洋渐变', value: 'linear-gradient(135deg, rgba(239, 246, 255, 0.9) 0%, rgba(220, 230, 250, 0.85) 100%)' },
    { name: '晚霞渐变', value: 'linear-gradient(135deg, rgba(255, 242, 242, 0.9) 0%, rgba(255, 230, 230, 0.85) 100%)' },
    { name: '春意渐变', value: 'linear-gradient(135deg, rgba(240, 253, 244, 0.9) 0%, rgba(220, 250, 230, 0.85) 100%)' },
    { name: '紫霞渐变', value: 'linear-gradient(135deg, rgba(250, 245, 255, 0.9) 0%, rgba(235, 225, 255, 0.85) 100%)' },
    { name: '极光渐变', value: 'linear-gradient(135deg, rgba(240, 245, 250, 0.9) 0%, rgba(220, 240, 255, 0.85) 100%)' },
    { name: '月光渐变', value: 'linear-gradient(135deg, rgba(250, 250, 255, 0.9) 0%, rgba(240, 240, 250, 0.85) 100%)' },
    { name: '云朵渐变', value: 'linear-gradient(135deg, rgba(252, 252, 252, 0.9) 0%, rgba(245, 245, 245, 0.85) 100%)' }
  ])

  const inlineCodeBackgroundPresets = ref([
    { name: '浅灰色', value: '#f1f5f9' },
    { name: '淡黄色', value: '#fffbeb' },
    { name: '淡蓝色', value: '#eff6ff' },
    { name: '淡紫色', value: '#faf5ff' },
    { name: '淡绿色', value: '#f0fdf4' },
    { name: '淡橙色', value: '#fff7ed' },
    { name: '淡粉色', value: '#fdf2f8' },
    { name: '淡青色', value: '#ecfeff' }
  ])

  // --- 聊天皮肤预设（/chat 换肤） ---
  // 各皮肤通过同一组 --chat-* 变量驱动，切换时不改变组件结构与行为。
  const skinPresets = ref([
    {
      name: 'retro',
      label: '复古 Win95',
      isDark: false,
      pageBackground: '#c0c0c0',
      vars: {
        '--chat-wallpaper': '#008c8c',
        '--chat-wallpaper-grid': 'rgba(0, 0, 0, 0.03)',
        '--chat-backdrop': 'rgba(0, 0, 0, 0.45)',
        '--chat-panel': '#c0c0c0',
        '--chat-panel-hover': '#d4d4d4',
        '--chat-sunken': '#d4d0c8',
        '--chat-bevel-light': '#ffffff',
        '--chat-bevel-shadow': '#808080',
        '--chat-bevel-frame-light': '#dfdfdf',
        '--chat-bevel-frame-dark': '#404040',
        '--chat-inset-shadow': '#808080',
        '--chat-inset-light': '#ffffff',
        '--chat-titlebar-start': '#000080',
        '--chat-titlebar-end': '#1084d0',
        '--chat-accent': '#000080',
        '--chat-accent-hover': '#0000cc',
        '--chat-text': '#000000',
        '--chat-text-muted': '#4d4d4d',
        '--chat-text-on-accent': '#ffffff',
        '--chat-active-title': '#ffffff',
        '--chat-active-muted': '#d6e7ff',
        '--chat-titlebar-text': '#ffffff',
        '--chat-titlebar-separator': 'rgba(255, 255, 255, 0.3)',
        '--chat-titlebar-hairline': 'transparent',
        '--chat-titlebar-text-shadow': '1px 1px 0 #000000',
        '--chat-titlebar-text-muted': '#c0c0c0',
        '--chat-danger': '#dc2626',
        '--chat-danger-text': '#800000',
        '--chat-danger-tint': '#ffcccc',
        '--chat-success': '#008000',
        '--chat-success-tint': '#ccffcc',
        '--chat-favorite': '#808000',
        '--chat-favorite-on': '#ffff00',
        '--chat-favorite-tint': '#ffffcc',
        '--chat-rag': '#800080',
        '--chat-tooltip': '#ffffe1',
        '--chat-scrollbar-track': 'repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px',
        '--chat-scrollbar-thumb': '#c0c0c0',
        '--chat-scrollbar-border': '#808080',
        '--chat-scrollbar-size': '17px',
        '--chat-radius': '0px',
        '--chat-font-family': "'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif",
        '--chat-shadow': 'none',
        '--chat-shadow-color': 'rgba(0, 0, 0, 0.4)',
        '--chat-popover-shadow': '2px 2px 0 rgba(0, 0, 0, 0.25)',
        '--chat-input-bg': 'linear-gradient(180deg, rgba(214, 210, 200, 0.92), #c0c0c0)',
        '--chat-surface': '#ffffff',
        '--chat-surface-text': '#111827',
        '--chat-user-bubble-bg': 'linear-gradient(180deg, #1084d0, #000080)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(180deg, #2096df, #0000a0)',
        '--chat-user-bubble-text': '#ffffff',
        '--chat-user-bubble-border': '#000080',
        '--chat-user-bubble-border-hover': '#0000cc',
        '--chat-user-bubble-shadow': '2px 2px 0 rgba(0, 0, 0, 0.18)',
        '--chat-ai-bubble-bg': '#ffffff',
        '--chat-ai-bubble-text': '#202020',
        '--chat-ai-bubble-shadow': '1px 1px 0 rgba(0, 0, 0, 0.12)',
        '--chat-ai-bubble-border': '#808080',
        '--chat-markdown-text': '#202020',
        '--chat-markdown-heading': '#000080',
        '--chat-markdown-marker': '#008080',
        '--chat-inline-code-bg': '#eeeeee',
        '--chat-inline-code-border': '#808080',
        '--chat-blockquote-bg': '#ffffe1',
        '--chat-blockquote-text': '#404040',
        '--chat-table-bg': '#ffffff',
        '--chat-table-border': '#e5e7eb',
        '--chat-table-head-text': '#334155',
        '--chat-link': '#1d4ed8',
        '--chat-blockquote-border': '#008080',
        '--chat-code-text': '#e2e8f0',
        '--chat-inline-code-text': '#b42318',
        '--chat-table-head': '#f8fafc',
        '--chat-table-stripe': '#fbfdff',
        '--chat-image-bg': '#f8fafc',
        '--chat-send-bg': 'linear-gradient(180deg, #1084d0, #000080)',
        '--chat-send-bg-hover': 'linear-gradient(180deg, #1d9be8, #0000a8)'
      }
    },
    {
      name: 'modern-light',
      label: '明亮现代',
      isDark: false,
      pageBackground: '#f5f8f7',
      followPageBackground: true,
      vars: {
        '--chat-wallpaper': '#edf3f2',
        '--chat-wallpaper-grid': 'transparent',
        '--chat-backdrop': 'rgba(15, 23, 42, 0.4)',
        '--chat-panel': '#ffffff',
        '--chat-panel-hover': '#eef4f3',
        '--chat-sunken': 'transparent',
        '--chat-bevel-light': '#e2e8f0',
        '--chat-bevel-shadow': '#cbd5e1',
        '--chat-bevel-frame-light': 'transparent',
        '--chat-bevel-frame-dark': 'transparent',
        '--chat-inset-shadow': 'rgba(15, 23, 42, 0.06)',
        '--chat-inset-light': 'transparent',
        '--chat-titlebar-start': '#f8fafc',
        '--chat-titlebar-end': '#eaf4f2',
        '--chat-titlebar-text': '#263b3a',
        '--chat-titlebar-separator': '#e2e8f0',
        '--chat-titlebar-hairline': '#e2e8f0',
        '--chat-titlebar-text-shadow': 'none',
        '--chat-titlebar-text-muted': '#64748b',
        '--chat-accent': '#2563eb',
        '--chat-accent-hover': '#1d4ed8',
        '--chat-text': '#25343b',
        '--chat-text-muted': '#65747a',
        '--chat-text-on-accent': '#ffffff',
        '--chat-active-title': '#ffffff',
        '--chat-active-muted': 'rgba(255, 255, 255, 0.82)',
        '--chat-danger': '#ef4444',
        '--chat-danger-text': '#b91c1c',
        '--chat-danger-tint': '#fee2e2',
        '--chat-success': '#10b981',
        '--chat-success-tint': '#d1fae5',
        '--chat-favorite': '#d97706',
        '--chat-favorite-on': '#f59e0b',
        '--chat-favorite-tint': '#fef3c7',
        '--chat-rag': '#7c3aed',
        '--chat-tooltip': '#ffffff',
        '--chat-scrollbar-track': '#f1f5f9',
        '--chat-scrollbar-thumb': '#cbd5e1',
        '--chat-scrollbar-border': '#e2e8f0',
        '--chat-scrollbar-size': '8px',
        '--chat-radius': '8px',
        '--chat-font-family': "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
        '--chat-shadow': '0 1px 3px rgba(15, 23, 42, 0.1)',
        '--chat-shadow-color': '#0f172a',
        '--chat-popover-shadow': '0 8px 24px rgba(15, 23, 42, 0.14)',
        '--chat-input-bg': '#ffffff',
        '--chat-surface': '#ffffff',
        '--chat-surface-text': '#1f2937',
        '--chat-user-bubble-bg': 'linear-gradient(135deg, #2563eb, #4f46e5)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(135deg, #1d4ed8, #4338ca)',
        '--chat-user-bubble-text': '#ffffff',
        '--chat-user-bubble-border': 'transparent',
        '--chat-user-bubble-border-hover': 'transparent',
        '--chat-user-bubble-shadow': '0 3px 10px rgba(67, 56, 202, 0.2)',
        '--chat-ai-bubble-bg': '#ffffff',
        '--chat-ai-bubble-text': '#1f2937',
        '--chat-ai-bubble-shadow': '0 1px 2px rgba(15, 23, 42, 0.06)',
        '--chat-ai-bubble-border': '#e2e8f0',
        '--chat-markdown-text': '#374151',
        '--chat-markdown-heading': '#111827',
        '--chat-markdown-marker': '#0f766e',
        '--chat-inline-code-bg': '#f1f5f9',
        '--chat-inline-code-border': 'rgba(203, 213, 225, 0.9)',
        '--chat-blockquote-bg': 'linear-gradient(to right, rgba(248, 250, 252, 0.98), rgba(255, 255, 255, 0.78))',
        '--chat-blockquote-text': '#475569',
        '--chat-table-bg': '#ffffff',
        '--chat-table-border': '#e5e7eb',
        '--chat-table-head-text': '#334155',
        '--chat-link': '#0f766e',
        '--chat-blockquote-border': '#5aa59c',
        '--chat-code-text': '#e2e8f0',
        '--chat-inline-code-text': '#be185d',
        '--chat-table-head': '#f8fafc',
        '--chat-table-stripe': '#fbfdff',
        '--chat-image-bg': '#f1f5f9',
        '--chat-send-bg': 'linear-gradient(135deg, #2563eb, #4f46e5)',
        '--chat-send-bg-hover': 'linear-gradient(135deg, #3b82f6, #6366f1)'
      }
    },
    {
      name: 'modern-dark',
      label: '暗色现代',
      isDark: true,
      pageBackground: '#101717',
      vars: {
        '--chat-wallpaper': '#0d1517',
        '--chat-wallpaper-grid': 'transparent',
        '--chat-backdrop': 'rgba(0, 0, 0, 0.6)',
        '--chat-panel': '#1a2527',
        '--chat-panel-hover': '#29383b',
        '--chat-sunken': '#0c1416',
        '--chat-bevel-light': '#405257',
        '--chat-bevel-shadow': '#253337',
        '--chat-bevel-frame-light': 'transparent',
        '--chat-bevel-frame-dark': 'transparent',
        '--chat-inset-shadow': 'rgba(0, 0, 0, 0.5)',
        '--chat-inset-light': 'transparent',
        '--chat-titlebar-start': '#172124',
        '--chat-titlebar-end': '#253e40',
        '--chat-titlebar-text': '#d4e6e4',
        '--chat-titlebar-separator': '#324649',
        '--chat-titlebar-hairline': '#324649',
        '--chat-titlebar-text-shadow': 'none',
        '--chat-titlebar-text-muted': '#94a3b8',
        '--chat-accent': '#2563eb',
        '--chat-accent-hover': '#3b82f6',
        '--chat-text': '#dce8e7',
        '--chat-text-muted': '#91a5a7',
        '--chat-text-on-accent': '#ffffff',
        '--chat-active-title': '#ffffff',
        '--chat-active-muted': 'rgba(255, 255, 255, 0.82)',
        '--chat-danger': '#f87171',
        '--chat-danger-text': '#fca5a5',
        '--chat-danger-tint': '#7f1d1d',
        '--chat-success': '#34d399',
        '--chat-success-tint': '#064e3b',
        '--chat-favorite': '#fbbf24',
        '--chat-favorite-on': '#fcd34d',
        '--chat-favorite-tint': '#78350f',
        '--chat-rag': '#a78bfa',
        '--chat-tooltip': '#29383b',
        '--chat-scrollbar-track': '#162124',
        '--chat-scrollbar-thumb': '#40575a',
        '--chat-scrollbar-border': '#293c40',
        '--chat-scrollbar-size': '8px',
        '--chat-radius': '8px',
        '--chat-font-family': "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
        '--chat-shadow': '0 1px 3px rgba(0, 0, 0, 0.3)',
        '--chat-shadow-color': '#000000',
        '--chat-popover-shadow': '0 8px 24px rgba(0, 0, 0, 0.5)',
        '--chat-input-bg': '#0d1517',
        '--chat-surface': '#27373a',
        '--chat-surface-text': '#dce8e7',
        '--chat-user-bubble-bg': 'linear-gradient(135deg, #2563eb, #4f46e5)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(135deg, #3b82f6, #6366f1)',
        '--chat-user-bubble-text': '#ffffff',
        '--chat-user-bubble-border': 'transparent',
        '--chat-user-bubble-border-hover': 'transparent',
        '--chat-user-bubble-shadow': '0 1px 2px rgba(0, 0, 0, 0.3)',
        '--chat-ai-bubble-bg': '#27373a',
        '--chat-ai-bubble-text': '#dce8e7',
        '--chat-ai-bubble-shadow': '0 1px 2px rgba(0, 0, 0, 0.3)',
        '--chat-ai-bubble-border': '#405257',
        '--chat-markdown-text': '#c8d7d6',
        '--chat-markdown-heading': '#eff7f6',
        '--chat-markdown-marker': '#5eead4',
        '--chat-inline-code-bg': '#0d1517',
        '--chat-inline-code-border': '#405257',
        '--chat-blockquote-bg': 'linear-gradient(to right, rgba(51, 65, 85, 0.55), rgba(30, 41, 59, 0.4))',
        '--chat-blockquote-text': '#cbd5e1',
        '--chat-table-bg': '#1a2527',
        '--chat-table-border': '#405257',
        '--chat-table-head-text': '#e2e8f0',
        '--chat-link': '#5eead4',
        '--chat-blockquote-border': '#2dd4bf',
        '--chat-code-text': '#e2e8f0',
        '--chat-inline-code-text': '#f472b6',
        '--chat-table-head': '#253337',
        '--chat-table-stripe': '#111b1d',
        '--chat-image-bg': '#1a2527',
        '--chat-send-bg': 'linear-gradient(135deg, #2563eb, #4f46e5)',
        '--chat-send-bg-hover': 'linear-gradient(135deg, #3b82f6, #6366f1)'
      }
    },
    {
      name: 'paper',
      label: '纸墨阅读',
      isDark: false,
      pageBackground: '#f4efe4',
      vars: {
        '--chat-wallpaper': '#eae3d4',
        '--chat-wallpaper-grid': 'transparent',
        '--chat-backdrop': 'rgba(88, 70, 48, 0.35)',
        '--chat-panel': '#fbf7ee',
        '--chat-panel-hover': '#f4edde',
        '--chat-sunken': '#f6f1e5',
        '--chat-bevel-light': '#e6dcc7',
        '--chat-bevel-shadow': '#d3c6ac',
        '--chat-bevel-frame-light': 'transparent',
        '--chat-bevel-frame-dark': 'transparent',
        '--chat-inset-shadow': 'rgba(120, 95, 60, 0.08)',
        '--chat-inset-light': 'transparent',
        '--chat-titlebar-start': '#4d5b43',
        '--chat-titlebar-end': '#77654a',
        '--chat-accent': '#6b5f35',
        '--chat-accent-hover': '#51472a',
        '--chat-text': '#3d3629',
        '--chat-text-muted': '#776b56',
        '--chat-text-on-accent': '#fdf6e9',
        '--chat-active-title': '#fdf6e9',
        '--chat-active-muted': 'rgba(253, 246, 233, 0.82)',
        '--chat-titlebar-text': '#fdf6e9',
        '--chat-titlebar-separator': 'rgba(255, 251, 240, 0.28)',
        '--chat-titlebar-hairline': 'rgba(0, 0, 0, 0.08)',
        '--chat-titlebar-text-shadow': 'none',
        '--chat-titlebar-text-muted': 'rgba(253, 246, 233, 0.82)',
        '--chat-danger': '#b04a3c',
        '--chat-danger-text': '#9a3328',
        '--chat-danger-tint': '#f5e0dc',
        '--chat-success': '#47735a',
        '--chat-success-tint': '#e6efd5',
        '--chat-favorite': '#b7791f',
        '--chat-favorite-on': '#d69e2e',
        '--chat-favorite-tint': '#fdf3d7',
        '--chat-rag': '#8b5f6d',
        '--chat-tooltip': '#fefaf0',
        '--chat-scrollbar-track': '#efe7d6',
        '--chat-scrollbar-thumb': '#c9bca1',
        '--chat-scrollbar-border': '#ddd2bb',
        '--chat-scrollbar-size': '8px',
        '--chat-radius': '6px',
        '--chat-font-family': "'Georgia', 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif",
        '--chat-shadow': '0 1px 3px rgba(120, 95, 60, 0.12)',
        '--chat-shadow-color': '#4a3826',
        '--chat-popover-shadow': '0 8px 24px rgba(120, 95, 60, 0.18)',
        '--chat-input-bg': '#fdf9f0',
        '--chat-surface': '#fffdf6',
        '--chat-surface-text': '#3d3629',
        '--chat-user-bubble-bg': 'linear-gradient(180deg, #9a5b43, #7d4938)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(180deg, #aa674d, #8d5140)',
        '--chat-user-bubble-text': '#fdf6e9',
        '--chat-user-bubble-border': 'transparent',
        '--chat-user-bubble-border-hover': 'transparent',
        '--chat-user-bubble-shadow': '0 1px 2px rgba(120, 95, 60, 0.22)',
        '--chat-ai-bubble-bg': '#fffdf6',
        '--chat-ai-bubble-text': '#3d3629',
        '--chat-ai-bubble-shadow': '0 1px 2px rgba(120, 95, 60, 0.1)',
        '--chat-ai-bubble-border': '#e6dcc7',
        '--chat-markdown-text': '#3d3629',
        '--chat-markdown-heading': '#2c271e',
        '--chat-markdown-marker': '#5c6f52',
        '--chat-inline-code-bg': '#f1e9d8',
        '--chat-inline-code-border': 'rgba(197, 180, 150, 0.65)',
        '--chat-blockquote-bg': 'linear-gradient(to right, rgba(242, 233, 215, 0.92), rgba(253, 250, 243, 0.7))',
        '--chat-blockquote-text': '#6b5a3e',
        '--chat-table-bg': '#fffdf6',
        '--chat-table-border': '#ddd2bb',
        '--chat-table-head-text': '#4a4033',
        '--chat-link': '#426a58',
        '--chat-blockquote-border': '#9a5b43',
        '--chat-code-text': '#f0ead9',
        '--chat-inline-code-text': '#9a4b2d',
        '--chat-table-head': '#f4edde',
        '--chat-table-stripe': '#f9f4e8',
        '--chat-image-bg': '#f4edde',
        '--chat-send-bg': 'linear-gradient(180deg, #9a5b43, #7d4938)',
        '--chat-send-bg-hover': 'linear-gradient(180deg, #aa674d, #8d5140)'
      }
    },
    {
      name: 'emerald',
      label: '翡翠绿',
      isDark: false,
      pageBackground: '#f0f5f1',
      vars: {
        '--chat-wallpaper': '#e5eee8',
        '--chat-wallpaper-grid': 'transparent',
        '--chat-backdrop': 'rgba(34, 66, 46, 0.35)',
        '--chat-panel': '#f6fbf4',
        '--chat-panel-hover': '#edf6ea',
        '--chat-sunken': '#f0f7ee',
        '--chat-bevel-light': '#dce8d8',
        '--chat-bevel-shadow': '#c6d8c2',
        '--chat-bevel-frame-light': 'transparent',
        '--chat-bevel-frame-dark': 'transparent',
        '--chat-inset-shadow': 'rgba(34, 66, 46, 0.08)',
        '--chat-inset-light': 'transparent',
        '--chat-titlebar-start': '#286356',
        '--chat-titlebar-end': '#4b8f72',
        '--chat-accent': '#147d64',
        '--chat-accent-hover': '#0f6652',
        '--chat-text': '#263a35',
        '--chat-text-muted': '#5d736d',
        '--chat-text-on-accent': '#ffffff',
        '--chat-active-title': '#ffffff',
        '--chat-active-muted': 'rgba(255, 255, 255, 0.82)',
        '--chat-titlebar-text': '#f3fbf5',
        '--chat-titlebar-separator': 'rgba(243, 251, 245, 0.28)',
        '--chat-titlebar-hairline': 'rgba(0, 0, 0, 0.06)',
        '--chat-titlebar-text-shadow': 'none',
        '--chat-titlebar-text-muted': 'rgba(243, 251, 245, 0.82)',
        '--chat-danger': '#b91c1c',
        '--chat-danger-text': '#991b1b',
        '--chat-danger-tint': '#fbe3e3',
        '--chat-success': '#16805d',
        '--chat-success-tint': '#ddf1e3',
        '--chat-favorite': '#b45309',
        '--chat-favorite-on': '#d97706',
        '--chat-favorite-tint': '#fdf0df',
        '--chat-rag': '#7c3aed',
        '--chat-tooltip': '#f2faf1',
        '--chat-scrollbar-track': '#e6efe2',
        '--chat-scrollbar-thumb': '#b9cbb4',
        '--chat-scrollbar-border': '#d5e0d2',
        '--chat-scrollbar-size': '8px',
        '--chat-radius': '12px',
        '--chat-font-family': "system-ui, -apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', Roboto, sans-serif",
        '--chat-shadow': '0 2px 8px rgba(34, 66, 46, 0.08)',
        '--chat-shadow-color': '#1f3d2a',
        '--chat-popover-shadow': '0 10px 28px rgba(34, 66, 46, 0.14)',
        '--chat-input-bg': '#f6fbf4',
        '--chat-surface': '#ffffff',
        '--chat-surface-text': '#233a2a',
        '--chat-user-bubble-bg': 'linear-gradient(135deg, #17866a, #0f766e)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(135deg, #20977a, #0d665f)',
        '--chat-user-bubble-text': '#ffffff',
        '--chat-user-bubble-border': 'transparent',
        '--chat-user-bubble-border-hover': 'transparent',
        '--chat-user-bubble-shadow': '0 3px 10px rgba(15, 118, 110, 0.2)',
        '--chat-ai-bubble-bg': '#ffffff',
        '--chat-ai-bubble-text': '#2b3a2c',
        '--chat-ai-bubble-shadow': '0 1px 3px rgba(34, 66, 46, 0.1)',
        '--chat-ai-bubble-border': '#dce8d8',
        '--chat-markdown-text': '#2b3a2c',
        '--chat-markdown-heading': '#173c34',
        '--chat-markdown-marker': '#147d64',
        '--chat-inline-code-bg': '#e6f0e2',
        '--chat-inline-code-border': 'rgba(184, 205, 175, 0.65)',
        '--chat-blockquote-bg': 'linear-gradient(to right, rgba(226, 240, 222, 0.92), rgba(255, 255, 255, 0.7))',
        '--chat-blockquote-text': '#4d6252',
        '--chat-table-bg': '#ffffff',
        '--chat-table-border': '#dce8d8',
        '--chat-table-head-text': '#334a3a',
        '--chat-link': '#1d4ed8',
        '--chat-blockquote-border': '#c08a3e',
        '--chat-code-text': '#e2e8f0',
        '--chat-inline-code-text': '#0f766e',
        '--chat-table-head': '#edf6ea',
        '--chat-table-stripe': '#f8fcf6',
        '--chat-image-bg': '#eef5ee',
        '--chat-send-bg': 'linear-gradient(135deg, #17866a, #0f766e)',
        '--chat-send-bg-hover': 'linear-gradient(135deg, #20977a, #0d665f)'
      }
    },
    {
      name: 'terminal',
      label: '终端绿',
      isDark: true,
      pageBackground: '#07110d',
      vars: {
        '--chat-wallpaper': '#07110d',
        '--chat-wallpaper-grid': 'rgba(45, 212, 191, 0.025)',
        '--chat-backdrop': 'rgba(0, 0, 0, 0.65)',
        '--chat-panel': '#0d1b16',
        '--chat-panel-hover': '#142820',
        '--chat-sunken': '#050c09',
        '--chat-bevel-light': '#244438',
        '--chat-bevel-shadow': '#07130e',
        '--chat-bevel-frame-light': 'transparent',
        '--chat-bevel-frame-dark': 'transparent',
        '--chat-inset-shadow': 'rgba(0, 0, 0, 0.6)',
        '--chat-inset-light': 'transparent',
        '--chat-titlebar-start': '#0a1712',
        '--chat-titlebar-end': '#123d2d',
        '--chat-accent': '#34d399',
        '--chat-accent-hover': '#6ee7b7',
        '--chat-text': '#c5f4dc',
        '--chat-text-muted': '#72a98f',
        '--chat-text-on-accent': '#032019',
        '--chat-active-title': '#032019',
        '--chat-active-muted': 'rgba(4, 17, 10, 0.75)',
        '--chat-titlebar-text': '#6ee7b7',
        '--chat-titlebar-separator': 'rgba(110, 231, 183, 0.24)',
        '--chat-titlebar-hairline': '#23543f',
        '--chat-titlebar-text-shadow': 'none',
        '--chat-titlebar-text-muted': 'rgba(74, 222, 128, 0.75)',
        '--chat-danger': '#f87171',
        '--chat-danger-text': '#fca5a5',
        '--chat-danger-tint': '#3f1d1d',
        '--chat-success': '#4ade80',
        '--chat-success-tint': '#0f2f1c',
        '--chat-favorite': '#facc15',
        '--chat-favorite-on': '#fde047',
        '--chat-favorite-tint': '#3a2f0d',
        '--chat-rag': '#22d3ee',
        '--chat-tooltip': '#10231c',
        '--chat-scrollbar-track': '#091610',
        '--chat-scrollbar-thumb': '#285b45',
        '--chat-scrollbar-border': '#18382b',
        '--chat-scrollbar-size': '8px',
        '--chat-radius': '0px',
        '--chat-font-family': "'Cascadia Code', 'JetBrains Mono', Consolas, 'Courier New', monospace",
        '--chat-shadow': '0 2px 8px rgba(0, 0, 0, 0.28)',
        '--chat-shadow-color': '#000000',
        '--chat-popover-shadow': '0 8px 24px rgba(0, 0, 0, 0.6)',
        '--chat-input-bg': '#091610',
        '--chat-surface': '#10231c',
        '--chat-surface-text': '#c5f4dc',
        '--chat-user-bubble-bg': 'linear-gradient(180deg, #34d399, #10b981)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(180deg, #6ee7b7, #34d399)',
        '--chat-user-bubble-text': '#032019',
        '--chat-user-bubble-border': 'transparent',
        '--chat-user-bubble-border-hover': 'transparent',
        '--chat-user-bubble-shadow': '0 2px 8px rgba(16, 185, 129, 0.18)',
        '--chat-ai-bubble-bg': '#10231c',
        '--chat-ai-bubble-text': '#c5f4dc',
        '--chat-ai-bubble-shadow': '0 2px 8px rgba(0, 0, 0, 0.22)',
        '--chat-ai-bubble-border': '#244438',
        '--chat-markdown-text': '#bdebd4',
        '--chat-markdown-heading': '#a7f3d0',
        '--chat-markdown-marker': '#34d399',
        '--chat-inline-code-bg': '#07130e',
        '--chat-inline-code-border': '#285b45',
        '--chat-blockquote-bg': 'linear-gradient(to right, rgba(18, 32, 22, 0.9), rgba(13, 19, 14, 0.6))',
        '--chat-blockquote-text': '#91c9ad',
        '--chat-table-bg': '#0d1b16',
        '--chat-table-border': '#244438',
        '--chat-table-head-text': '#a7f3d0',
        '--chat-link': '#67e8f9',
        '--chat-blockquote-border': '#34d399',
        '--chat-code-text': '#a7f3d0',
        '--chat-inline-code-text': '#a7f3d0',
        '--chat-table-head': '#142820',
        '--chat-table-stripe': '#091610',
        '--chat-image-bg': '#0d1b16',
        '--chat-send-bg': 'linear-gradient(180deg, #34d399, #10b981)',
        '--chat-send-bg-hover': 'linear-gradient(180deg, #6ee7b7, #34d399)'
      }
    },
    {
      name: 'coral',
      label: '珊瑚晚霞',
      isDark: false,
      pageBackground: '#fff8f5',
      vars: {
        '--chat-wallpaper': '#fff0eb',
        '--chat-wallpaper-grid': 'transparent',
        '--chat-backdrop': 'rgba(154, 52, 18, 0.35)',
        '--chat-panel': '#fffdfc',
        '--chat-panel-hover': '#fff1ed',
        '--chat-sunken': '#fff6f2',
        '--chat-bevel-light': '#f8d9d1',
        '--chat-bevel-shadow': '#e9bdb3',
        '--chat-bevel-frame-light': 'transparent',
        '--chat-bevel-frame-dark': 'transparent',
        '--chat-inset-shadow': 'rgba(154, 52, 18, 0.08)',
        '--chat-inset-light': 'transparent',
        '--chat-titlebar-start': '#c04358',
        '--chat-titlebar-end': '#c64f3c',
        '--chat-titlebar-text': '#ffffff',
        '--chat-titlebar-separator': 'rgba(255, 247, 237, 0.28)',
        '--chat-titlebar-hairline': 'rgba(0, 0, 0, 0.06)',
        '--chat-titlebar-text-shadow': 'none',
        '--chat-titlebar-text-muted': 'rgba(255, 247, 237, 0.82)',
        '--chat-accent': '#c94061',
        '--chat-accent-hover': '#b83550',
        '--chat-text': '#49333a',
        '--chat-text-muted': '#8c636f',
        '--chat-text-on-accent': '#ffffff',
        '--chat-active-title': '#ffffff',
        '--chat-active-muted': 'rgba(255, 255, 255, 0.82)',
        '--chat-danger': '#dc2626',
        '--chat-danger-text': '#991b1b',
        '--chat-danger-tint': '#fde3e3',
        '--chat-success': '#0f8b78',
        '--chat-success-tint': '#dff4ef',
        '--chat-favorite': '#d97706',
        '--chat-favorite-on': '#f59e0b',
        '--chat-favorite-tint': '#fdf0df',
        '--chat-rag': '#7c5cc4',
        '--chat-tooltip': '#fff8f5',
        '--chat-scrollbar-track': '#fce9e5',
        '--chat-scrollbar-thumb': '#dfa9ad',
        '--chat-scrollbar-border': '#f1cbd0',
        '--chat-scrollbar-size': '8px',
        '--chat-radius': '12px',
        '--chat-font-family': "system-ui, -apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', Roboto, sans-serif",
        '--chat-shadow': '0 2px 10px rgba(148, 75, 91, 0.1)',
        '--chat-shadow-color': '#7d4050',
        '--chat-popover-shadow': '0 12px 30px rgba(148, 75, 91, 0.16)',
        '--chat-input-bg': '#fff9f7',
        '--chat-surface': '#ffffff',
        '--chat-surface-text': '#49333a',
        '--chat-user-bubble-bg': 'linear-gradient(135deg, #d6455e, #bc3a4c)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(135deg, #de5870, #c94757)',
        '--chat-user-bubble-text': '#ffffff',
        '--chat-user-bubble-border': 'transparent',
        '--chat-user-bubble-border-hover': 'transparent',
        '--chat-user-bubble-shadow': '0 3px 10px rgba(217, 79, 112, 0.2)',
        '--chat-ai-bubble-bg': '#ffffff',
        '--chat-ai-bubble-text': '#49333a',
        '--chat-ai-bubble-shadow': '0 2px 8px rgba(148, 75, 91, 0.08)',
        '--chat-ai-bubble-border': '#f3d8d4',
        '--chat-markdown-text': '#584149',
        '--chat-markdown-heading': '#713b4b',
        '--chat-markdown-marker': '#d94f70',
        '--chat-inline-code-bg': '#fff0ed',
        '--chat-inline-code-border': 'rgba(224, 169, 174, 0.7)',
        '--chat-blockquote-bg': 'linear-gradient(to right, rgba(254, 235, 220, 0.92), rgba(255, 255, 255, 0.7))',
        '--chat-blockquote-text': '#85616b',
        '--chat-table-bg': '#ffffff',
        '--chat-table-border': '#f3d8d4',
        '--chat-table-head-text': '#713b4b',
        '--chat-link': '#0f766e',
        '--chat-blockquote-border': '#e86572',
        '--chat-code-text': '#e2e8f0',
        '--chat-inline-code-text': '#be123c',
        '--chat-table-head': '#fff1ed',
        '--chat-table-stripe': '#fffbfa',
        '--chat-image-bg': '#fff6f2',
        '--chat-send-bg': 'linear-gradient(135deg, #d6455e, #bc3a4c)',
        '--chat-send-bg-hover': 'linear-gradient(135deg, #de5870, #c94757)'
      }
    },
    {
      name: 'ink',
      label: '国风水墨',
      isDark: true,
      pageBackground: '#171918',
      vars: {
        '--chat-wallpaper': '#1c201e',
        '--chat-wallpaper-grid': 'rgba(226, 218, 199, 0.018)',
        '--chat-backdrop': 'rgba(0, 0, 0, 0.6)',
        '--chat-panel': '#252824',
        '--chat-panel-hover': '#30342f',
        '--chat-sunken': '#131614',
        '--chat-bevel-light': '#3d423b',
        '--chat-bevel-shadow': '#111310',
        '--chat-bevel-frame-light': 'transparent',
        '--chat-bevel-frame-dark': 'transparent',
        '--chat-inset-shadow': 'rgba(0, 0, 0, 0.55)',
        '--chat-inset-light': 'transparent',
        '--chat-titlebar-start': '#242824',
        '--chat-titlebar-end': '#3f4941',
        '--chat-titlebar-text': '#eee7d8',
        '--chat-titlebar-separator': 'rgba(232, 221, 204, 0.22)',
        '--chat-titlebar-hairline': 'rgba(0, 0, 0, 0.2)',
        '--chat-titlebar-text-shadow': 'none',
        '--chat-titlebar-text-muted': 'rgba(232, 221, 204, 0.7)',
        '--chat-accent': '#b94a3b',
        '--chat-accent-hover': '#d05a49',
        '--chat-text': '#e5dfd2',
        '--chat-text-muted': '#a19d90',
        '--chat-text-on-accent': '#fdf6ef',
        '--chat-active-title': '#fdf6ef',
        '--chat-active-muted': 'rgba(253, 246, 239, 0.82)',
        '--chat-danger': '#e74c3c',
        '--chat-danger-text': '#f5b7b1',
        '--chat-danger-tint': '#4a1d1d',
        '--chat-success': '#6fa58c',
        '--chat-success-tint': '#20352c',
        '--chat-favorite': '#d4a017',
        '--chat-favorite-on': '#e6b93b',
        '--chat-favorite-tint': '#3a2f0d',
        '--chat-rag': '#8ca99a',
        '--chat-tooltip': '#30342f',
        '--chat-scrollbar-track': '#1c201e',
        '--chat-scrollbar-thumb': '#515a52',
        '--chat-scrollbar-border': '#363c37',
        '--chat-scrollbar-size': '8px',
        '--chat-radius': '4px',
        '--chat-font-family': "'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', Georgia, serif",
        '--chat-shadow': '0 2px 9px rgba(0, 0, 0, 0.32)',
        '--chat-shadow-color': '#000000',
        '--chat-popover-shadow': '0 8px 24px rgba(0, 0, 0, 0.55)',
        '--chat-input-bg': '#171a18',
        '--chat-surface': '#2b2f2b',
        '--chat-surface-text': '#e5dfd2',
        '--chat-user-bubble-bg': 'linear-gradient(180deg, #a94436, #87372d)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(180deg, #bd5141, #9c4033)',
        '--chat-user-bubble-text': '#fdf6ef',
        '--chat-user-bubble-border': 'transparent',
        '--chat-user-bubble-border-hover': 'transparent',
        '--chat-user-bubble-shadow': '0 1px 2px rgba(0, 0, 0, 0.35)',
        '--chat-ai-bubble-bg': 'rgba(43, 47, 43, 0.94)',
        '--chat-ai-bubble-text': '#e5dfd2',
        '--chat-ai-bubble-shadow': '0 2px 8px rgba(0, 0, 0, 0.22)',
        '--chat-ai-bubble-border': 'rgba(180, 181, 164, 0.24)',
        '--chat-markdown-text': '#ddd8cc',
        '--chat-markdown-heading': '#f2ecdf',
        '--chat-markdown-marker': '#b94a3b',
        '--chat-inline-code-bg': '#171a18',
        '--chat-inline-code-border': '#414741',
        '--chat-blockquote-bg': 'linear-gradient(to right, rgba(58, 50, 40, 0.9), rgba(38, 34, 29, 0.6))',
        '--chat-blockquote-text': '#b9b4a8',
        '--chat-table-bg': '#252824',
        '--chat-table-border': '#414741',
        '--chat-table-head-text': '#e5dfd2',
        '--chat-link': '#8fc2aa',
        '--chat-blockquote-border': '#b94a3b',
        '--chat-code-text': '#f0ead9',
        '--chat-inline-code-text': '#e8b4a0',
        '--chat-table-head': '#30342f',
        '--chat-table-stripe': '#1c201e',
        '--chat-image-bg': '#252824',
        '--chat-send-bg': 'linear-gradient(180deg, #a94436, #87372d)',
        '--chat-send-bg-hover': 'linear-gradient(180deg, #bd5141, #9c4033)'
      }
    },
    {
      name: 'cloud-immortal',
      label: '云海仙门',
      isDark: false,
      pageBackground: '#dce8e6',
      vars: {
        '--chat-wallpaper': '#b8cfce',
        '--chat-wallpaper-grid': 'rgba(247, 244, 226, 0.08)',
        '--chat-backdrop': 'rgba(18, 51, 59, 0.38)',
        '--chat-panel': 'rgba(235, 243, 238, 0.92)',
        '--chat-panel-hover': '#f3f7ef',
        '--chat-sunken': 'rgba(218, 233, 229, 0.88)',
        '--chat-bevel-light': '#f8f4df',
        '--chat-bevel-shadow': '#8eaca8',
        '--chat-bevel-frame-light': '#dfece4',
        '--chat-bevel-frame-dark': '#426b70',
        '--chat-inset-shadow': 'rgba(35, 79, 82, 0.2)',
        '--chat-inset-light': 'rgba(255, 255, 255, 0.62)',
        '--chat-titlebar-start': '#315b66',
        '--chat-titlebar-end': '#547f82',
        '--chat-accent': '#2f7d78',
        '--chat-accent-hover': '#23635f',
        '--chat-text': '#183d46',
        '--chat-text-muted': '#547174',
        '--chat-text-on-accent': '#fffdf1',
        '--chat-active-title': '#fffdf1',
        '--chat-active-muted': 'rgba(255, 253, 241, 0.84)',
        '--chat-titlebar-text': '#fffdf1',
        '--chat-titlebar-separator': 'rgba(255, 253, 241, 0.28)',
        '--chat-titlebar-hairline': 'rgba(25, 67, 72, 0.18)',
        '--chat-titlebar-text-shadow': '0 1px 0 rgba(14, 43, 50, 0.35)',
        '--chat-titlebar-text-muted': 'rgba(236, 244, 232, 0.78)',
        '--chat-danger': '#b94b42',
        '--chat-danger-text': '#9c3934',
        '--chat-danger-tint': '#f8e1d8',
        '--chat-success': '#287b68',
        '--chat-success-tint': '#dcefe3',
        '--chat-favorite': '#b38a3d',
        '--chat-favorite-on': '#d2ad5b',
        '--chat-favorite-tint': '#f7efd2',
        '--chat-rag': '#6872a1',
        '--chat-tooltip': '#fffdf1',
        '--chat-scrollbar-track': 'rgba(180, 207, 204, 0.58)',
        '--chat-scrollbar-thumb': '#759b9a',
        '--chat-scrollbar-border': '#aac5c0',
        '--chat-scrollbar-size': '9px',
        '--chat-radius': '7px',
        '--chat-font-family': "'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', Georgia, serif",
        '--chat-shadow': '0 8px 24px rgba(30, 74, 77, 0.13)',
        '--chat-shadow-color': '#234f52',
        '--chat-popover-shadow': '0 12px 30px rgba(30, 74, 77, 0.2)',
        '--chat-input-bg': 'rgba(231, 240, 234, 0.96)',
        '--chat-surface': '#fffdf3',
        '--chat-surface-text': '#183d46',
        '--chat-user-bubble-bg': 'linear-gradient(145deg, #d7ede3, #b8d9d1)',
        '--chat-user-bubble-bg-hover': 'linear-gradient(145deg, #e1f1e8, #c4e2da)',
        '--chat-user-bubble-text': '#17474a',
        '--chat-user-bubble-border': '#70a9a0',
        '--chat-user-bubble-border-hover': '#2f7d78',
        '--chat-user-bubble-shadow': 'rgba(45, 103, 99, 0.18)',
        '--chat-ai-bubble-bg': 'rgba(255, 253, 243, 0.94)',
        '--chat-ai-bubble-text': '#24444b',
        '--chat-ai-bubble-shadow': 'rgba(52, 83, 78, 0.1)',
        '--chat-ai-bubble-border': '#d2dfd5',
        '--chat-markdown-text': '#24444b',
        '--chat-markdown-heading': '#1c4e58',
        '--chat-markdown-marker': '#b38a3d',
        '--chat-inline-code-bg': '#e5eee6',
        '--chat-inline-code-border': '#b5cdc3',
        '--chat-blockquote-bg': 'linear-gradient(90deg, #e1eee9, rgba(255, 253, 243, 0.72))',
        '--chat-blockquote-text': '#507075',
        '--chat-table-bg': 'rgba(255, 253, 243, 0.9)',
        '--chat-table-border': '#c8d9d2',
        '--chat-table-head-text': '#315b66',
        '--chat-link': '#236f75',
        '--chat-blockquote-border': '#b38a3d',
        '--chat-code-text': '#e5f0e9',
        '--chat-inline-code-text': '#8d4238',
        '--chat-table-head': '#e5eee6',
        '--chat-table-stripe': '#f2f5e9',
        '--chat-image-bg': '#e8f0e8',
        '--chat-send-bg': 'linear-gradient(145deg, #4b9a8e, #23635f)',
        '--chat-send-bg-hover': 'linear-gradient(145deg, #61aca0, #2f7d78)'
      }
    }
  ])

  // --- 从 configService.js 合并的默认配置 ---
  const defaultConfig = {
    // API 配置
    openaiKey: '',
    openaiBaseUrl: 'https://api.openai.com/v1',
    claudeKey: '',
    geminiKey: '',
    
    // 页面外观
    pageBackground: '#f8fafc',
    customBackground: '#f8fafc',
    panelBackground: 'rgba(255, 255, 255, 0.9)',
    fontFamily: 'system',
    fontSize: 14,
    isThemeDark: false,

    // 聊天皮肤（/chat 换肤）
    chatSkin: 'retro', // 'retro' | 'modern-light' | 'modern-dark' | 'paper' | 'emerald' | 'terminal' | 'coral' | 'ink' | 'cloud-immortal'
    
    // 聊天样式
    chatBackgroundType: 'theme', // 'theme' 跟随主题, 'color' 纯色, 'image' 图片
    chatBackground: 'rgba(255, 255, 255, 0.95)',
    chatBackgroundImage: '',
    chatBackgroundImageOpacity: 0.5,
    
    // 消息样式
    messageSpacing: 12,
    messagePadding: 12,
    messageBorderRadius: 12,
    messageMaxWidth: 80,
    messageUserBackground: 'rgba(59, 130, 246, 0.9)',
    messageAiBackground: 'rgba(255, 255, 255, 0.85)',
    
    // 代码样式
    inlineCodeBackground: '#f1f5f9',
    codeBlockBackground: '#1e293b',
    codeFontSize: 14,
    codeFont: 'system',
    
    // 聊天行为
    autoScroll: true,
    showTimestamp: false,
    showMessageCount: false,
    enableMessageActions: true,
    autoCopyUserMessage: false,
    enableHoverEffects: false, // 关闭悬停效果，提高性能
    parseMdAfterStream: false, // 流式结束后再解析 Markdown：流式期间仅显示纯文本，减少长消息解析卡顿
    
    // 滚动条样式
    scrollbarThumbColor: '#cbd5e1', // 默认滚动条滑块颜色
    scrollbarTrackColor: '#f1f5f9', // 默认滚动条轨道颜色

    // 新增：话题追踪器配置
    topicTrackerViewMode: 'normal', // normal, fullscreen
    topicTrackerDensity: 'high', // 默认使用紧凑视图
    topicTrackerContrast: 'high', // normal, high

    // 头像配置（基于 DiceBear 生成）
    userAvatar: {
      enabled: true,               // 是否应用为用户头像
      style: 'avataaars',          // DiceBear 风格
      seed: '小江子',               // 头像种子
      transparent: true,           // 背景是否透明
      backgroundColor: 'b6e3f4'    // 非透明时的背景色（不含 #）
    },
    aiAvatar: {
      enabled: true,               // 是否应用为 AI 头像
      style: 'bottts',             // 用户所给示例即为 bottts 风格
      seed: '09d9cb57a3864cdbb4fe9fc49856efa1',
      transparent: true,
      backgroundColor: 'b6e3f4'
    }
  }

  // --- 响应式状态 ---
  const config = ref(JSON.parse(JSON.stringify(defaultConfig)))

  // --- 方法 ---

  // 应用样式到页面
  const applyStyles = () => {
    const root = document.documentElement
    const conf = config.value

    const fontFamily = conf.fontFamily === 'system' 
      ? 'system-ui, -apple-system, sans-serif' 
      : conf.fontFamily

    const codeFont = conf.codeFont === 'system'
      ? 'Consolas, Monaco, monospace'
      : `'${conf.codeFont}', Consolas, Monaco, monospace`

    // 根据对比度设置调整颜色 - 使用简洁的灰色主题
    const textPrimary = conf.topicTrackerContrast === 'high'
      ? (conf.isThemeDark ? '#f8f9fa' : '#374151')
      : (conf.isThemeDark ? '#f8f9fa' : '#4b5563');
      
    const textSecondary = conf.topicTrackerContrast === 'high'
      ? (conf.isThemeDark ? '#adb5bd' : '#6b7280')
      : (conf.isThemeDark ? '#adb5bd' : '#9ca3af');
      
    const borderColor = conf.topicTrackerContrast === 'high'
      ? (conf.isThemeDark ? 'rgba(255, 255, 255, 0.3)' : 'rgba(0, 0, 0, 0.1)')
      : (conf.isThemeDark ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.06)');

    const cssVars = {
      // 基础变量
      '--app-background': conf.pageBackground,
      '--app-font-family': fontFamily,
      '--app-font-size': conf.fontSize + 'px',
      '--app-panel-background': conf.panelBackground,
      '--app-panel-background-rgb': toRgbString(conf.panelBackground),
      
      // 动态颜色变量
      '--app-text-primary': textPrimary,
      '--app-text-secondary': textSecondary,
      '--app-border-color': borderColor,
      '--app-component-bg': conf.isThemeDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(248, 250, 252, 0.5)',
      '--app-hover-bg': conf.isThemeDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(59, 130, 246, 0.1)',
      '--app-active-bg': conf.isThemeDark ? 'rgba(59, 130, 246, 0.2)' : '#3b82f6',
      '--app-active-text': conf.isThemeDark ? '#ffffff' : '#ffffff',
      '--app-active-text-hover': conf.isThemeDark ? '#3b82f6' : '#3b82f6',

      // 聊天界面变量
      '--app-chat-background-type': conf.chatBackgroundType,
      '--app-chat-background': conf.chatBackgroundType === 'theme' ? conf.pageBackground : (conf.chatBackgroundType === 'image' ? 'transparent' : conf.chatBackground),
      '--app-chat-background-image': conf.chatBackgroundType === 'image' && conf.chatBackgroundImage ? conf.chatBackgroundImage : '',
      '--app-chat-background-opacity': conf.chatBackgroundType === 'image' ? String(conf.chatBackgroundImageOpacity ?? 0.5) : '1',
      
      // 消息样式变量
      '--message-spacing': conf.messageSpacing + 'px',
      '--message-padding': conf.messagePadding + 'px',
      '--message-border-radius': conf.messageBorderRadius + 'px',
      '--message-max-width': conf.messageMaxWidth + '%',
      
      // 代码样式变量
      '--code-block-background': conf.codeBlockBackground,
      '--code-font-family': codeFont,
      '--code-font-size': conf.codeFontSize + 'px',
      
      // 交互效果变量
      '--enable-hover-effects': conf.enableHoverEffects ? '1' : '0',
      
      // 话题追踪器变量
      '--topic-tracker-view-mode': conf.topicTrackerViewMode,
      '--topic-tracker-density': conf.topicTrackerDensity,
      '--topic-tracker-contrast': conf.topicTrackerContrast,

      // 滚动条变量
      '--app-scrollbar-thumb': conf.scrollbarThumbColor,
      '--app-scrollbar-track': conf.scrollbarTrackColor,
    }

    Object.entries(cssVars).forEach(([property, value]) => {
      root.style.setProperty(property, value)
    })

    // 聊天皮肤变量（/chat 换肤）
    const skin = skinPresets.value.find(s => s.name === conf.chatSkin) || skinPresets.value[0]
    Object.entries(skin.vars).forEach(([property, value]) => {
      root.style.setProperty(property, value)
    })
    // 支持「跟随页面背景」的皮肤：聊天桌面壁纸使用用户自定义的页面背景，并去掉壁纸网格线
    if (skin.followPageBackground) {
      root.style.setProperty('--chat-wallpaper', conf.pageBackground)
      root.style.setProperty('--chat-wallpaper-grid', 'transparent')
    }
    root.style.setProperty('--chat-inline-code-bg', conf.inlineCodeBackground)
  }

  // 切换聊天皮肤：写入配置并立即应用样式
  const setChatSkin = (name) => {
    const skin = skinPresets.value.find(s => s.name === name)
    if (!skin) return
    config.value.chatSkin = skin.name
    config.value.isThemeDark = skin.isDark
    // 跟随页面背景的皮肤（如明亮现代）不覆盖用户自定义的页面背景
    if (skin.pageBackground && !skin.followPageBackground) config.value.pageBackground = skin.pageBackground
    applyStyles()
  }

  // 保存配置到 localStorage (防抖)
  const saveConfig = debounce(() => {
    try {
      localStorage.setItem('app-config', JSON.stringify(config.value))
    } catch (error) {
      console.error('保存配置失败:', error)
    }
  }, 500)

  // 从 localStorage 加载配置
  const loadConfig = () => {
    try {
      const saved = localStorage.getItem('app-config')
      if (saved) {
        const parsedConfig = JSON.parse(saved)
        // CRITICAL FIX: Ensure parsedConfig is a non-null object before merging
        if (parsedConfig && typeof parsedConfig === 'object') {
          delete parsedConfig.enableAnimations
          // 合并保存的配置和默认配置，以防有新增的配置项
          config.value = { ...defaultConfig, ...parsedConfig }
        } else {
          // If localStorage is corrupted or not an object, reset to default
          config.value = JSON.parse(JSON.stringify(defaultConfig))
        }
      }
    } catch (error) {
      console.error('加载配置失败:', error)
      config.value = JSON.parse(JSON.stringify(defaultConfig))
    }
  }

  // 重置配置
  const resetConfig = () => {
    config.value = JSON.parse(JSON.stringify(defaultConfig))
  }

  // 防抖应用样式，避免频繁更新导致滚动等问题
  const debouncedApplyStyles = debounce(() => {
    applyStyles()
  }, 100)

  // 初始化
  const init = () => {
    loadConfig()
    applyStyles()
    // 监听配置变化，自动应用样式并保存
    watch(config, () => {
      debouncedApplyStyles()
      saveConfig()
    }, { deep: true })
  }

  return {
    // 状态
    config,
    backgroundPresets,
    panelBackgroundPresets,
    chatBackgroundPresets,
    inlineCodeBackgroundPresets,
    skinPresets,
    
    // 方法
    init,
    resetConfig,
    setChatSkin,
    // 提供一个直接修改config的方法，Vue组件中可以直接使用 v-model="store.config.fontSize"
  }
})
