const test = require('node:test')
const assert = require('node:assert/strict')
const {
  CRITICAL_HEX,
  CATEGORY_QUOTAS,
  buildTrendEmojiCatalog,
  normalizeHex,
  toBlobFileName
} = require('./trend-emoji-catalog.cjs')

test('normalizes codepoints to runtime file naming', () => {
  assert.equal(normalizeHex('2764-FE0F'), '2764')
  assert.equal(normalizeHex('1f44d-1f3fb'), '1F44D-1F3FB')
})

test('builds one unique 200-item catalog with fixed quotas', () => {
  const catalog = buildTrendEmojiCatalog()
  assert.equal(catalog.length, 200)
  assert.equal(new Set(catalog.map(item => item.hex)).size, 200)

  for (const [group, expected] of Object.entries(CATEGORY_QUOTAS)) {
    assert.equal(catalog.filter(item => item.group === group).length, expected)
  }

  for (const hex of CRITICAL_HEX) {
    assert.ok(catalog.some(item => item.hex === hex), `missing critical emoji ${hex}`)
  }
})

test('maps the blob provider file name deterministically', () => {
  const joy = buildTrendEmojiCatalog().find(item => item.hex === '1F602')
  assert.equal(toBlobFileName(joy), 'emoji_u1f602.svg')
})
