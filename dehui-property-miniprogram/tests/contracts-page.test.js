const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('mini-program registers a tenant electronic contracts page', () => {
  const appJson = JSON.parse(read('app.json'))
  assert.ok(appJson.pages.includes('pages/contracts/index'))
})

test('mine page exposes electronic contracts through capability-filtered business cards', () => {
  const source = read('pages/mine/index.js')

  assert.match(source, /getVisibleMineFeatures/)
  assert.match(source, /key:\s*'contract'/)
  assert.match(source, /title:\s*'我的合同'/)
  assert.match(source, /url:\s*'\/pages\/contracts\/index'/)
})

test('contracts page guards tenant access and preserves unified file download', () => {
  const source = read('pages/contracts/index.js')
  const wxml = read('pages/contracts/index.wxml')

  assert.match(source, /FEATURE_KEY = 'contract'/)
  assert.ok(
    source.indexOf('canAccessFeature(FEATURE_KEY, profile)') < source.indexOf("api.get('/mobile/contracts'"),
    'contract capability guard must run before the contract request'
  )
  assert.match(source, /contractDownloadUrl/)
  assert.match(source, /item\.attachments/)
  assert.match(source, /SUPPLEMENTARY_AGREEMENT/)
  assert.match(source, /api\.download/)
  assert.match(source, /wx\.openDocument/)
  assert.match(source, /roomSummary/)
  assert.doesNotMatch(source, /api\.(?:post|patch|delete)\(['`]\/mobile\/contracts/)
  assert.match(wxml, /pageState === 'login-required'/)
  assert.match(wxml, /pageState === 'unauthorized'/)
  assert.match(wxml, /当前租户暂无电子合同/)
  assert.match(wxml, /attachment\.downloadUrl/)
})
