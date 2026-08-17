const test = require('node:test')
const assert = require('node:assert/strict')

function loadEnvironment(values) {
  global.wx = {
    getStorageSync: key => values.get(key),
    setStorageSync: (key, value) => values.set(key, value),
    removeStorageSync: key => values.delete(key)
  }
  delete require.cache[require.resolve('../config/env')]
  return require('../config/env')
}

test.afterEach(() => {
  delete global.wx
  delete require.cache[require.resolve('../config/env')]
})

test('production environment ignores a stale local API override', () => {
  const values = new Map([
    ['apiBaseURL', 'http://localhost:8080/api']
  ])
  const environment = loadEnvironment(values)

  assert.equal(environment.activeEnv, 'prod')
  assert.equal(environment.getBaseURL(), 'https://wuye.mingda.com.cn/api')
})

test('switching runtime environment clears the previous server session once', () => {
  const values = new Map([
    ['apiRuntimeEnvironment', 'dev|http://localhost:8080/api'],
    ['apiBaseURL', 'http://localhost:8080/api'],
    ['token', 'local-token'],
    ['userInfo', { userType: 'STAFF', name: 'local-user' }],
    ['identity', 'STAFF'],
    ['manualLogout', true]
  ])
  const environment = loadEnvironment(values)

  const changed = environment.synchronizeRuntimeEnvironment()

  assert.equal(changed, true)
  assert.equal(values.has('apiBaseURL'), false)
  assert.equal(values.has('token'), false)
  assert.equal(values.has('userInfo'), false)
  assert.equal(values.has('identity'), false)
  assert.equal(values.get('manualLogout'), true)
  assert.equal(
    values.get('apiRuntimeEnvironment'),
    'prod|https://wuye.mingda.com.cn/api'
  )
  assert.equal(environment.synchronizeRuntimeEnvironment(), false)
})
