const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

test('app config removes legacy home, binding, and internal checkin pages', () => {
  const appConfig = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  const removedPages = [
    'pages/home/index',
    'pages/checkin/index',
    'pages/mine/tenant-bind/index',
    'pages/mine/staff-bind/index'
  ]

  for (const page of removedPages) {
    assert.equal(appConfig.pages.includes(page), false, `legacy page remains registered: ${page}`)
  }

  assert.equal(fs.existsSync(path.join(root, 'pages/home/index.js')), false)
  assert.equal(fs.existsSync(path.join(root, 'pages/checkin/index.js')), false)
  assert.equal(fs.existsSync(path.join(root, 'pages/mine/tenant-bind/index.js')), false)
  assert.equal(fs.existsSync(path.join(root, 'pages/mine/staff-bind/index.js')), false)
})

test('registered pages are unique, exist, and cover both tab bar routes', () => {
  const appConfig = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  assert.equal(new Set(appConfig.pages).size, appConfig.pages.length)

  for (const page of appConfig.pages) {
    assert.equal(fs.existsSync(path.join(root, `${page}.js`)), true, `missing page script: ${page}`)
  }

  for (const tab of appConfig.tabBar.list) {
    assert.ok(appConfig.pages.includes(tab.pagePath), `unregistered tab page: ${tab.pagePath}`)
  }
})

test('app config does not declare unused location or camera private infos', () => {
  const appConfig = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))

  assert.equal(appConfig.requiredPrivateInfos, undefined)
  assert.equal(appConfig.permission, undefined)
})
