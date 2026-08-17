const assert = require('node:assert/strict')
const test = require('node:test')

function clearModules() {
  for (const path of ['../utils/auth', '../utils/request', '../config/env']) {
    delete require.cache[require.resolve(path)]
  }
}

function storage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    values,
    getStorageSync: key => values.get(key) || '',
    setStorageSync: (key, value) => values.set(key, value),
    removeStorageSync: key => values.delete(key),
    showToast() {}
  }
}

function success(options, data) {
  options.success({ statusCode: 200, data: { code: 200, data } })
}

test('concurrent silent session requests share one wx.login and backend login', async () => {
  clearModules()
  const runtime = storage()
  let logins = 0
  let requests = 0
  global.wx = {
    ...runtime,
    login(options) {
      logins += 1
      setImmediate(() => options.success({ code: 'wx-code' }))
    },
    request(options) {
      requests += 1
      setImmediate(() => success(options, {
        token: 'public-token',
        userType: 'PUBLIC',
        capabilities: []
      }))
    }
  }
  const auth = require('../utils/auth')
  const [first, second] = await Promise.all([
    auth.ensureWechatSession(),
    auth.ensureWechatSession()
  ])
  assert.equal(logins, 1)
  assert.equal(requests, 1)
  assert.equal(first.token, 'public-token')
  assert.equal(second.token, 'public-token')
  assert.equal(auth.getStoredProfile().userType, 'PUBLIC')
  assert.deepEqual(auth.getStoredProfile().capabilities, [])
})

test('forced session refresh is reused before stored-token shortcut', async () => {
  clearModules()
  const runtime = storage({
    token: 'old-token',
    identity: 'TENANT',
    userInfo: { userType: 'TENANT', capabilities: ['bill.view'] }
  })
  let finishLogin
  let requests = 0
  global.wx = {
    ...runtime,
    login(options) {
      finishLogin = () => options.success({ code: 'fresh-code' })
    },
    request(options) {
      requests += 1
      success(options, { token: 'fresh-token', userType: 'PUBLIC', capabilities: [] })
    }
  }
  const auth = require('../utils/auth')
  const forced = auth.ensureWechatSession({ force: true })
  const concurrent = auth.ensureWechatSession()
  assert.strictEqual(concurrent, forced)
  finishLogin()
  await Promise.all([forced, concurrent])
  assert.equal(requests, 1)
  assert.equal(runtime.values.get('token'), 'fresh-token')
})

test('identity-only PUBLIC session clears stale TENANT authority', () => {
  clearModules()
  const runtime = storage({
    token: 'tenant-token',
    identity: 'TENANT',
    userInfo: { userType: 'TENANT', capabilities: ['bill.view'], tenantId: 99 }
  })
  global.wx = runtime
  const auth = require('../utils/auth')
  auth.setSession({ token: 'public-token', userType: 'PUBLIC' })
  const profile = auth.getStoredProfile()
  assert.equal(profile.userType, 'PUBLIC')
  assert.deepEqual(profile.capabilities, [])
  assert.equal(Object.hasOwn(profile, 'tenantId'), false)
})

test('late profile response cannot overwrite a newer session identity', async () => {
  clearModules()
  const runtime = storage({
    token: 'token-a',
    identity: 'TENANT',
    userInfo: { userType: 'TENANT', capabilities: ['bill.view'] }
  })
  let pending
  global.wx = { ...runtime, request(options) { pending = options } }
  const auth = require('../utils/auth')
  const oldRefresh = auth.refreshCurrentProfile({ ensureSession: false })
  auth.setSession({ token: 'token-b', userType: 'PUBLIC', capabilities: [] })
  success(pending, { userType: 'TENANT', capabilities: ['bill.view'] })
  await assert.rejects(oldRefresh, error => error.kind === 'STALE_SESSION')
  assert.equal(runtime.values.get('token'), 'token-b')
  assert.equal(auth.getStoredProfile().userType, 'PUBLIC')
  assert.deepEqual(auth.getStoredProfile().capabilities, [])
})

test('concurrent profile readers share one backend request', async () => {
  clearModules()
  const runtime = storage({
    token: 'tenant-token',
    identity: 'TENANT',
    userInfo: { userType: 'TENANT', capabilities: [] }
  })
  let pending
  let requests = 0
  global.wx = {
    ...runtime,
    request(options) {
      requests += 1
      pending = options
    }
  }
  const auth = require('../utils/auth')
  const first = auth.refreshCurrentProfile({ ensureSession: false })
  const second = auth.refreshCurrentProfile({ ensureSession: false })
  assert.equal(requests, 1)
  success(pending, {
    userType: 'TENANT',
    profile: { name: '张三', maskedPhone: '138****1234' },
    capabilities: ['workorder.view']
  })
  const [a, b] = await Promise.all([first, second])
  assert.deepEqual(a, b)
  assert.equal(auth.getStoredProfile().profile.name, '张三')
})

test('server profile replaces raw phone and relationship ids', () => {
  clearModules()
  const runtime = storage({
    userInfo: {
      userType: 'TENANT',
      phone: '13800001234',
      tenantId: 99,
      capabilities: ['bill.view']
    }
  })
  global.wx = runtime
  const auth = require('../utils/auth')
  const saved = auth.saveProfile({
    userType: 'TENANT',
    profile: { name: '张三', maskedPhone: '138****1234' },
    tenantInfo: { name: '测试企业' },
    capabilities: []
  })
  assert.equal(Object.hasOwn(saved, 'phone'), false)
  assert.equal(Object.hasOwn(saved, 'tenantId'), false)
  assert.equal(saved.profile.maskedPhone, '138****1234')
  assert.deepEqual(saved.capabilities, [])
})

test('/mobile/me 401 removes token and fails closed', async () => {
  clearModules()
  const runtime = storage({
    token: 'expired-token',
    identity: 'TENANT',
    userInfo: { userType: 'TENANT', capabilities: ['bill.view'] }
  })
  global.wx = {
    ...runtime,
    request(options) {
      options.success({
        statusCode: 401,
        data: { code: 401, message: 'expired' }
      })
    }
  }
  const auth = require('../utils/auth')
  await assert.rejects(
    auth.refreshCurrentProfile({ ensureSession: false }),
    error => error.kind === 'AUTH_REQUIRED'
  )
  assert.equal(runtime.values.has('token'), false)
  assert.equal(auth.getStoredProfile().userType, 'PUBLIC')
  assert.deepEqual(auth.getStoredProfile().capabilities, [])
})

test('logout revokes backend token, clears auth, and keeps legal consent', async () => {
  clearModules()
  const consent = { acceptedAt: '2026-07-20T08:00:00.000Z' }
  const runtime = storage({
    token: 'tenant-token',
    identity: 'TENANT',
    userInfo: { userType: 'TENANT' },
    legalConsent: consent
  })
  let logoutRequest
  global.wx = {
    ...runtime,
    request(options) {
      logoutRequest = options
      success(options, null)
    }
  }
  const auth = require('../utils/auth')
  assert.equal(await auth.logout(), true)
  assert.ok(logoutRequest.url.endsWith('/mobile/auth/logout'))
  assert.equal(logoutRequest.header.Authorization, 'tenant-token')
  assert.equal(runtime.values.has('token'), false)
  assert.equal(runtime.values.get('manualLogout'), true)
  assert.deepEqual(runtime.values.get('legalConsent'), consent)
})

test('server cannot restore TENANT when a public-only session is requested', async () => {
  clearModules()
  const runtime = storage({ manualLogout: true })
  global.wx = {
    ...runtime,
    login(options) { options.success({ code: 'public-only-code' }) },
    request(options) {
      success(options, {
        token: 'unexpected-token',
        userType: 'TENANT',
        capabilities: ['bill.view']
      })
    }
  }
  const auth = require('../utils/auth')
  await assert.rejects(
    auth.ensureWechatSession({ userInitiated: true }),
    error => error.kind === 'INVALID_SESSION_SCOPE'
  )
  assert.equal(runtime.values.has('token'), false)
  assert.equal(runtime.values.get('manualLogout'), true)
})

test('verified phone authorization retries once after an expired session', async () => {
  clearModules()
  const runtime = storage({
    token: 'expired-token',
    identity: 'PUBLIC',
    userInfo: { userType: 'PUBLIC', capabilities: [] }
  })
  let phoneCalls = 0
  let wxLogins = 0
  global.wx = {
    ...runtime,
    login(options) {
      wxLogins += 1
      options.success({ code: 'fresh-code' })
    },
    request(options) {
      if (options.url.endsWith('/mobile/auth/wechat-phone-login')) {
        phoneCalls += 1
        if (phoneCalls === 1) {
          options.success({ statusCode: 401, data: { code: 401, message: 'expired' } })
        } else {
          success(options, {
            token: 'tenant-token',
            userType: 'TENANT',
            capabilities: ['workorder.view']
          })
        }
        return
      }
      success(options, { token: 'fresh-public-token', userType: 'PUBLIC', capabilities: [] })
    }
  }
  const auth = require('../utils/auth')
  const profile = await auth.authorizeWechatPhone('official-phone-code')
  assert.equal(phoneCalls, 2)
  assert.equal(wxLogins, 1)
  assert.equal(profile.userType, 'TENANT')
  assert.equal(runtime.values.get('token'), 'tenant-token')
})
