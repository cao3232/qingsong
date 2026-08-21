import test from 'node:test'
import assert from 'node:assert/strict'
import { EMOJI_PROVIDERS, replaceEmojis } from './emoji.js'

test('registers both trend providers', () => {
  const keys = EMOJI_PROVIDERS.map(provider => provider.key)
  assert.ok(keys.includes('blob'))
  assert.ok(keys.includes('doodle'))
})

test('renders provider-specific assets', () => {
  assert.match(replaceEmojis('😄', 'blob'), /emoji-blob\/1F604\.svg/)
  assert.match(replaceEmojis('😄', 'doodle'), /emoji-doodle\/1F604\.svg/)
})

test('keeps uncovered emoji as native text', () => {
  const rare = '🦄'
  assert.equal(replaceEmojis(rare, 'blob'), rare)
  assert.equal(replaceEmojis(rare, 'doodle'), rare)
})

test('continues protecting existing image tags', () => {
  const html = '<img src="x.svg" alt="😄"> 😄'
  const result = replaceEmojis(html, 'blob')
  assert.match(result, /^<img src="x\.svg" alt="😄">/)
  assert.equal((result.match(/emoji-img/g) || []).length, 1)
})
