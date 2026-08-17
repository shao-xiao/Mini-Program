const assert = require('node:assert/strict')
const test = require('node:test')

const pagePath = require.resolve('../pages/mine/index')
const requestPath = require.resolve('../utils/request')
const authPath = require.resolve('../utils/auth')
const capabilitiesPath = require.resolve('../utils/capabilities')

function flush() {
  return new Promise(resolve => setImmediate(resolve))
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function loadMine({ profile, download }) {
  delete require.cache[pagePath]
  delete require.cache[requestPath]
  delete require.cache[authPath]
  delete require.cache[capabilitiesPath]

  require.cache[requestPath] = {
    id: requestPath,
    filename: requestPath,
    loaded: true,
    exports: { download }
  }
  require.cache[authPath] = {
    id: authPath,
    filename: authPath,
    loaded: true,
    exports: {
      ensureWechatSession: async () => ({ token: 'session-token' }),
      refreshCurrentProfile: async () => profile
    }
  }
  require.cache[capabilitiesPath] = {
    id: capabilitiesPath,
    filename: capabilitiesPath,
    loaded: true,
    exports: {
      getUserType: value => String((value && value.userType) || 'PUBLIC').toUpperCase(),
      isTenantProfile: value => String((value && value.userType) || '').toUpperCase() === 'TENANT',
      getVisibleMineFeatures: () => [],
      getFeatureAccessState: () => ({ allowed: true })
    }
  }

  let definition
  global.Page = value => {
    definition = value
  }
  global.wx = {}
  require(pagePath)

  const instance = {
    ...definition,
    data: { ...definition.data },
    setData(payload) {
      Object.assign(this.data, payload)
    }
  }
  instance.onLoad()
  return instance
}

test('tenant mine profile downloads the server-owned company logo as a temporary protected file', async () => {
  const calls = []
  const instance = loadMine({
    profile: {
      userType: 'TENANT',
      tenantInfo: {
        name: '测试企业',
        logoUrl: '/api/mobile/files/31'
      },
      profile: { name: '测试联系人', maskedPhone: '138****1234' },
      capabilities: []
    },
    download: async (...args) => {
      calls.push(args)
      return 'wxfile://tmp/tenant-logo'
    }
  })

  await instance.refresh()
  await flush()

  assert.deepEqual(calls, [[
    '/api/mobile/files/31',
    'tenant-logo.png',
    { silent: true, temporary: true }
  ]])
  assert.equal(instance.data.isTenant, true)
  assert.equal(instance.data.tenantLogoUrl, '/api/mobile/files/31')
  assert.equal(instance.data.tenantLogoPath, 'wxfile://tmp/tenant-logo')
})

test('tenant without a configured logo stays empty while staff and public keep their non-tenant presentation', async () => {
  let downloadCount = 0
  const tenant = loadMine({
    profile: {
      userType: 'TENANT',
      tenantInfo: { name: '无 Logo 企业', logoUrl: '' },
      profile: { name: '租户联系人' },
      capabilities: []
    },
    download: async () => {
      downloadCount += 1
      return 'unexpected'
    }
  })
  await tenant.refresh()
  await flush()
  assert.equal(downloadCount, 0)
  assert.equal(tenant.data.isTenant, true)
  assert.equal(tenant.data.tenantLogoPath, '')

  const staff = loadMine({
    profile: {
      userType: 'STAFF',
      tenantInfo: { logoUrl: '/api/mobile/files/should-not-load' },
      profile: { name: '物业员工' },
      capabilities: []
    },
    download: async () => {
      downloadCount += 1
      return 'unexpected'
    }
  })
  await staff.refresh()
  await flush()
  assert.equal(downloadCount, 0)
  assert.equal(staff.data.isTenant, false)
  assert.equal(staff.data.tenantLogoPath, '')
})

test('logo download failure does not fail the identity card', async () => {
  const instance = loadMine({
    profile: {
      userType: 'TENANT',
      tenantInfo: { name: '测试企业', logoUrl: '/api/mobile/files/32' },
      profile: { name: '测试联系人' },
      capabilities: []
    },
    download: async () => {
      throw new Error('logo unavailable')
    }
  })

  await instance.refresh()
  await flush()

  assert.equal(instance.data.pageState, 'success')
  assert.equal(instance.data.isTenant, true)
  assert.equal(instance.data.tenantLogoPath, '')
})

test('onHide invalidates a late tenant logo download response', async () => {
  const pending = deferred()
  const instance = loadMine({
    profile: {
      userType: 'TENANT',
      tenantInfo: { name: '旧企业', logoUrl: '/api/mobile/files/33' },
      profile: { name: '旧联系人' },
      capabilities: []
    },
    download: () => pending.promise
  })

  await instance.refresh()
  instance.onHide()
  pending.resolve('wxfile://tmp/stale-logo')
  await flush()

  assert.equal(instance.data.tenantLogoPath, '')
})

test('tenant logo markup never falls back to the DHIC logo while non-tenant identities retain it', () => {
  const fs = require('node:fs')
  const path = require('node:path')
  const markup = fs.readFileSync(path.resolve(__dirname, '../pages/mine/index.wxml'), 'utf8')
  const style = fs.readFileSync(path.resolve(__dirname, '../pages/mine/index.wxss'), 'utf8')

  assert.match(markup, /avatar-wrap \{\{isTenant \? 'avatar-wrap-square' : ''\}\}/)
  assert.match(markup, /wx:if="{{isTenant && tenantLogoPath}}"[^>]+src="{{tenantLogoPath}}"/)
  assert.match(markup, /wx:elif="{{!isTenant}}"[^>]+src="\/assets\/dehui-logo\.png"/)
  assert.doesNotMatch(markup, /<image class="avatar" src="\/assets\/dehui-logo\.png"/)
  assert.match(style, /\.avatar-wrap-square\s*\{[^}]*border-radius:\s*var\(--dhic-radius-xs\);/s)
  assert.match(style, /\.avatar-wrap\s*\{[^}]*border-radius:\s*50%;/s)
})
