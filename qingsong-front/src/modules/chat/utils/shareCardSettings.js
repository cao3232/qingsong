export const SHARE_CARD_SETTINGS_KEY = 'chat-share-card-settings'

export const DEFAULT_SHARE_CARD_SETTINGS = {
  showHeader: true,
  showFooter: true,
  showPrevious: false
}

const SETTING_KEYS = ['showHeader', 'showFooter', 'showPrevious']

export const parseShareCardSettings = raw => {
  let parsed = null
  if (raw) {
    try {
      parsed = JSON.parse(raw)
    } catch {
      parsed = null
    }
  }
  if (!parsed || typeof parsed !== 'object') {
    return { ...DEFAULT_SHARE_CARD_SETTINGS }
  }
  const result = {}
  for (const key of SETTING_KEYS) {
    result[key] = typeof parsed[key] === 'boolean'
      ? parsed[key]
      : DEFAULT_SHARE_CARD_SETTINGS[key]
  }
  return result
}

export const readShareCardSettings = (storage = window.localStorage) => {
  try {
    return parseShareCardSettings(storage.getItem(SHARE_CARD_SETTINGS_KEY))
  } catch {
    return { ...DEFAULT_SHARE_CARD_SETTINGS }
  }
}

export const writeShareCardSettings = (settings, storage = window.localStorage) => {
  const safe = parseShareCardSettings(JSON.stringify(settings))
  try {
    storage.setItem(SHARE_CARD_SETTINGS_KEY, JSON.stringify(safe))
  } catch {
    // 存储不可用（隐私模式/配额）时静默忽略，不影响主流程
  }
}