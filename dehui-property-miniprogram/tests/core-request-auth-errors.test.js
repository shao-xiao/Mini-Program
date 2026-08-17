const assert = require('node:assert/strict')
const test = require('node:test')

function clearModules() {
  for (const path of ['../utils/auth', '../utils/request', '../config/env']) {
    delete require.cache[require.resolve(path)]
  }
}

function createRuntime(handler) {
  const values = new Map([
    ['token', 'token-1'],
    ['identity', 'TENANT'],
    ['userInfo', { userType: 'TENANT' }],
    ['legalConsent', { acceptedAt: '2026-07-20T08:00:00.000Z' }]
  ])
  global.wx = {
    env: { USER_DATA_PATH: '/tmp' },
    getStorageSync(key) {
      return values.get(key) || ''
    },
    setStorageSync(key, value) {
      values.set(key, value)
    },
    removeStorageSync(key) {
      values.delete(key)
    },
    showToast() {},
    request: handler.request || (() => {}),
    downloadFile: handler.downloadFile || (() => {}),
    uploadFile: handler.uploadFile || (() => {})
  }
  return values
}

test('standard business 401 clears only session storage and returns AUTH_REQUIRED', async () => {
  clearModules()
  const values = createRuntime({
    request(options) {
      options.success({
        statusCode: 200,
        data: { code: 401, message: '登录已过期', data: null }
      })
    }
  })
  const api = require('../utils/request')

  await assert.rejects(api.get('/mobile/me'), (error) => {
    assert.equal(error.kind, 'AUTH_REQUIRED')
    assert.equal(error.code, 401)
    assert.equal(error.message, '登录已过期')
    return true
  })
  assert.equal(values.has('token'), false)
  assert.equal(values.has('userInfo'), false)
  assert.equal(values.has('identity'), false)
  assert.equal(values.has('legalConsent'), true)
})

test('HTTP 401 is classified even without a standard response body', async () => {
  clearModules()
  const values = createRuntime({
    request(options) {
      options.success({ statusCode: 401, data: 'unauthorized' })
    }
  })
  const api = require('../utils/request')

  await assert.rejects(api.get('/mobile/me'), (error) => {
    assert.equal(error.kind, 'AUTH_REQUIRED')
    assert.equal(error.statusCode, 401)
    return true
  })
  assert.equal(values.has('token'), false)
})

test('a late 401 from an old token cannot clear a newer session', async () => {
  clearModules()
  let pendingRequest
  const values = createRuntime({
    request(options) {
      pendingRequest = options
    }
  })
  const api = require('../utils/request')

  const oldRequest = api.get('/mobile/me')
  values.set('token', 'token-2')
  values.set('identity', 'TENANT')
  values.set('userInfo', { userType: 'TENANT', contactName: '新会话' })
  pendingRequest.success({
    statusCode: 401,
    data: { code: 401, message: '旧会话已过期' }
  })

  await assert.rejects(oldRequest, error => error.kind === 'AUTH_REQUIRED')
  assert.equal(values.get('token'), 'token-2')
  assert.equal(values.get('userInfo').contactName, '新会话')
})

test('403 remains a signed-in session and is classified as FORBIDDEN', async () => {
  clearModules()
  const values = createRuntime({
    request(options) {
      options.success({
        statusCode: 403,
        data: { code: 403, message: '请先绑定租户账号', data: null }
      })
    }
  })
  const api = require('../utils/request')

  await assert.rejects(api.get('/mobile/bills'), (error) => {
    assert.equal(error.kind, 'FORBIDDEN')
    assert.equal(error.code, 403)
    return true
  })
  assert.equal(values.get('token'), 'token-1')
  assert.equal(values.get('identity'), 'TENANT')
})

test('download 401 clears a session while upload 403 preserves it', async () => {
  clearModules()
  const downloadValues = createRuntime({
    downloadFile(options) {
      options.success({ statusCode: 401 })
    }
  })
  let api = require('../utils/request')
  await assert.rejects(api.download('/mobile/bills/1/invoice/download', 'invoice.pdf'), (error) => {
    assert.equal(error.kind, 'AUTH_REQUIRED')
    return true
  })
  assert.equal(downloadValues.has('token'), false)

  clearModules()
  const uploadValues = createRuntime({
    uploadFile(options) {
      options.success({
        statusCode: 403,
        data: JSON.stringify({ code: 403, message: '禁止上传' })
      })
    }
  })
  api = require('../utils/request')
  await assert.rejects(api.upload('/mobile/workorders/images', '/tmp/a.jpg'), (error) => {
    assert.equal(error.kind, 'FORBIDDEN')
    return true
  })
  assert.equal(uploadValues.get('token'), 'token-1')
})
