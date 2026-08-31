import assert from 'node:assert/strict'
import test from 'node:test'
import {
  DEFAULT_SHARE_CARD_SETTINGS,
  parseShareCardSettings,
  readShareCardSettings,
  writeShareCardSettings
} from './shareCardSettings.js'

const createFakeStorage = initial => {
  const store = { ...initial }
  return {
    getItem: key => (key in store ? store[key] : null),
    setItem: (key, value) => {
      store[key] = String(value)
    },
    _store: store
  }
}

test('默认设置：头部/底部开、上一条关', () => {
  assert.deepEqual(DEFAULT_SHARE_CARD_SETTINGS, {
    showHeader: true,
    showFooter: true,
    showPrevious: false
  })
})

test('解析合法的设置 JSON', () => {
  assert.deepEqual(
    parseShareCardSettings('{"showHeader":false,"showFooter":true,"showPrevious":true}'),
    { showHeader: false, showFooter: true, showPrevious: true }
  )
})

test('空值 / 非法 JSON 回退默认值', () => {
  assert.deepEqual(parseShareCardSettings(null), DEFAULT_SHARE_CARD_SETTINGS)
  assert.deepEqual(parseShareCardSettings(''), DEFAULT_SHARE_CARD_SETTINGS)
  assert.deepEqual(parseShareCardSettings('{bad json'), DEFAULT_SHARE_CARD_SETTINGS)
})

test('解析时缺失或类型错误的字段逐个回退默认值', () => {
  assert.deepEqual(parseShareCardSettings('{"showHeader":false}'), {
    showHeader: false,
    showFooter: true,
    showPrevious: false
  })
  assert.deepEqual(parseShareCardSettings('{"showHeader":"yes"}'), DEFAULT_SHARE_CARD_SETTINGS)
})

test('write 会丢弃未知字段并可被 read 回读', () => {
  const storage = createFakeStorage({})
  writeShareCardSettings(
    { showHeader: false, showFooter: true, showPrevious: true, extra: 1 },
    storage
  )
  assert.deepEqual(readShareCardSettings(storage), {
    showHeader: false,
    showFooter: true,
    showPrevious: true
  })
})

test('storage 读取抛错时安全回退默认值', () => {
  const throwingStorage = {
    getItem: () => {
      throw new Error('read blocked')
    },
    setItem: () => {}
  }
  assert.deepEqual(readShareCardSettings(throwingStorage), DEFAULT_SHARE_CARD_SETTINGS)
})