const assert = require('node:assert/strict')
const test = require('node:test')

function clearModules() {
  for (const path of ['../app', '../utils/auth', '../utils/request', '../config/env']) {
    delete require.cache[require.resolve(path)]
  }
}

function runtime(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    values,
    getStorageSync: key => values.get(key) || '',
    setStorageSync: (key, value) => values.set(key, value),
    removeStorageSync: key => values.delete(key),
    showToast() {}
  }
}

test('app launch creates one silent PUBLIC session and publishes sessionReady', async (t) => {
  clearModules()
  const storage = runtime()
  let app
  let loginCount = 0
  let wechatLoginCount = 0
  let profileCount = 0
  global.App = definition => { app = definition }
  global.getApp = () => app
  global.wx = {
    ...storage,
    login(options) {
      loginCount += 1
      options.success({ code: 'launch-code' })
    },
    request(options) {
      if (options.url.endsWith('/mobile/auth/wechat-login')) {
        wechatLoginCount += 1
        options.success({
          statusCode: 200,
          data: { code: 200, data: { token: 'launch-token', userType: 'PUBLIC', capabilities: [] } }
        })
        return
      }
      profileCount += 1
      options.success({
        statusCode: 200,
        data: {
          code: 200,
          data: {
            sessionState: 'AUTHENTICATED',
            userType: 'PUBLIC',
            bindingState: 'UNVERIFIED',
            capabilities: [],
            dataScope: { type: 'PUBLIC' }
          }
        }
      })
    }
  }
  t.after(() => {
    delete global.App
    delete global.getApp
    delete global.wx
  })

  require('../app')
  app.onLaunch.call(app)
  assert.equal(typeof app.globalData.sessionReady.then, 'function')
  const me = await app.globalData.sessionReady

  assert.equal(loginCount, 1)
  assert.equal(wechatLoginCount, 1)
  assert.equal(profileCount, 1)
  assert.equal(me.userType, 'PUBLIC')
  assert.deepEqual(me.capabilities, [])
  assert.equal(app.globalData.token, 'launch-token')
  assert.equal(app.globalData.identity, 'PUBLIC')
  assert.equal(app.globalData.userInfo.userType, 'PUBLIC')
})

test('app launch after explicit logout remains local PUBLIC without WeChat calls', async (t) => {
  clearModules()
  const storage = runtime({ manualLogout: true })
  let app
  let loginCount = 0
  let requestCount = 0
  global.App = definition => { app = definition }
  global.getApp = () => app
  global.wx = {
    ...storage,
    login() { loginCount += 1 },
    request() { requestCount += 1 }
  }
  t.after(() => {
    delete global.App
    delete global.getApp
    delete global.wx
  })

  require('../app')
  app.onLaunch.call(app)
  const me = await app.globalData.sessionReady

  assert.equal(loginCount, 0)
  assert.equal(requestCount, 0)
  assert.equal(me.sessionState, 'ANONYMOUS')
  assert.equal(me.userType, 'PUBLIC')
  assert.deepEqual(me.capabilities, [])
  assert.equal(app.globalData.identity, 'PUBLIC')
  assert.equal(storage.values.get('manualLogout'), true)
})
