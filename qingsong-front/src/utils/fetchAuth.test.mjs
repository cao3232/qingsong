import assert from 'node:assert/strict'
import test from 'node:test'

const AUTH_STORAGE_KEY = 'mt-ai-auth-session'

const createStorage = (values = {}) => ({
  getItem(key) {
    return values[key] ?? null
  },
  removeItem(key) {
    delete values[key]
  }
})

test('fetchAuth sends the stored token in the satoken header without cookies', async () => {
  globalThis.window = {
    localStorage: createStorage({ [AUTH_STORAGE_KEY]: JSON.stringify('token-123') }),
    sessionStorage: createStorage()
  }

  let request
  globalThis.fetch = async (url, options) => {
    request = { url, options }
    return { ok: true }
  }

  const { fetchAuth } = await import(`./fetchAuth.js?test=${Date.now()}`)
  await fetchAuth('/api/test', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  })

  assert.equal(request.url, '/api/test')
  assert.equal(request.options.credentials, undefined)
  assert.equal(request.options.headers.get('Content-Type'), 'application/json')
  assert.equal(request.options.headers.get('satoken'), 'token-123')
})

test('fetchAuth preserves an explicit satoken header when no session exists', async () => {
  globalThis.window = {
    localStorage: createStorage(),
    sessionStorage: createStorage()
  }

  let request
  globalThis.fetch = async (url, options) => {
    request = { url, options }
    return { ok: true }
  }

  const { fetchAuth } = await import(`./fetchAuth.js?test=${Date.now()}`)
  await fetchAuth('/api/test', { headers: { satoken: 'explicit-token' } })

  assert.equal(request.options.headers.get('satoken'), 'explicit-token')
})

test('validateAuthSession rejects and clears a stale local token', async () => {
  const localValues = { [AUTH_STORAGE_KEY]: JSON.stringify('stale-token') }
  globalThis.window = {
    localStorage: createStorage(localValues),
    sessionStorage: createStorage()
  }

  const authSession = await import(`../services/authSession.js?test=${Date.now()}`)

  assert.equal(typeof authSession.validateAuthSession, 'function')
  assert.equal(await authSession.validateAuthSession(async () => false), false)
  assert.equal(localValues[AUTH_STORAGE_KEY], undefined)
})

test('validateAuthSession keeps the token when validation fails because of a network error', async () => {
  const localValues = { [AUTH_STORAGE_KEY]: JSON.stringify('valid-token') }
  globalThis.window = {
    localStorage: createStorage(localValues),
    sessionStorage: createStorage()
  }

  const authSession = await import(`../services/authSession.js?network-test=${Date.now()}`)
  await assert.rejects(
    authSession.validateAuthSession(async () => {
      throw new Error('Network Error')
    }),
    /Network Error/
  )
  assert.equal(localValues[AUTH_STORAGE_KEY], JSON.stringify('valid-token'))
})

test('validateNavigationSession returns unavailable marker on transient validation errors', async () => {
  const localValues = { [AUTH_STORAGE_KEY]: JSON.stringify('valid-token') }
  globalThis.window = {
    localStorage: createStorage(localValues),
    sessionStorage: createStorage()
  }

  const authSession = await import(`../services/authSession.js?navigation-test=${Date.now()}`)
  const isAvailable = await authSession.validateNavigationSession(async () => {
    throw new Error('Server Error')
  })

  // 网络失败/5xx 时返回 'unavailable'（三态），路由守卫据此放行但不跳转
  assert.equal(isAvailable, 'unavailable')
  assert.equal(localValues[AUTH_STORAGE_KEY], JSON.stringify('valid-token'))
})

test('fetchAuth rejects an HTTP 200 legacy authentication failure body', async () => {
  const localValues = { [AUTH_STORAGE_KEY]: JSON.stringify('stale-token') }
  globalThis.window = {
    localStorage: createStorage(localValues),
    sessionStorage: createStorage(),
    location: { pathname: '/chat', search: '', hash: '', href: '' }
  }
  globalThis.fetch = async () => new Response(
    JSON.stringify({ ok: 0, msg: '未登录，请重新登录' }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )

  const { fetchAuth } = await import(`./fetchAuth.js?legacy-body-test=${Date.now()}`)

  await assert.rejects(fetchAuth('/api/test'), /登录态已失效/)
  assert.equal(localValues[AUTH_STORAGE_KEY], undefined)
})

test('clearLegacyAuthCookie expires old satoken cookies on known paths', async () => {
  const assignments = []
  globalThis.document = {
    set cookie(value) {
      assignments.push(value)
    }
  }

  const authSession = await import(`../services/authSession.js?cookie-test=${Date.now()}`)

  assert.equal(typeof authSession.clearLegacyAuthCookie, 'function')
  authSession.clearLegacyAuthCookie()
  assert.deepEqual(assignments, [
    'satoken=; Max-Age=0; Path=/; SameSite=Lax',
    'satoken=; Max-Age=0; Path=/user-config; SameSite=Lax'
  ])
})
