const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('phone-login entry surfaces use neutral review-safe copy', () => {
  const entryFiles = [
    'pages/index/index.js',
    'pages/mine/index.js',
    'pages/mine/index.wxml',
    'pages/me/index.js',
    'pages/me/index.wxml',
    'pages/meeting/index.js',
    'pages/meeting/index.wxml',
    'pages/investment/index.js',
    'pages/investment/index.wxml',
    'pages/investment/detail/index.js',
    'pages/investment/detail/index.wxml',
    'utils/auth.js'
  ]

  for (const relativePath of entryFiles) {
    const source = read(relativePath)
    assert.doesNotMatch(source, /微信|官方/, `${relativePath} must not present platform-branded login copy`)
    assert.doesNotMatch(
      source,
      /<image\b[^>]*(?:wechat|weixin|微信|official)[^>]*>/i,
      `${relativePath} must not render platform-branded login artwork`
    )
  }

  assert.match(read('pages/me/index.wxml'), />手机号快捷登录<\/button>/)
  assert.match(read('pages/meeting/index.wxml'), />手机号快捷登录<\/button>/)
  assert.match(read('pages/investment/index.wxml'), />验证手机号后预约<\/button>/)
  assert.match(read('pages/investment/detail/index.wxml'), />验证手机号<\/button>/)
})

test('review-safe copy keeps every native phone authorization binding intact', () => {
  const markup = [
    read('pages/me/index.wxml'),
    read('pages/meeting/index.wxml'),
    read('pages/investment/index.wxml'),
    read('pages/investment/detail/index.wxml')
  ].join('\n')

  assert.equal((markup.match(/open-type="getPhoneNumber"/g) || []).length, 5)
  assert.equal((markup.match(/bindgetphonenumber="loginWithPhoneNumber"/g) || []).length, 1)
  assert.equal((markup.match(/bindgetphonenumber="authorizeGuestPhone"/g) || []).length, 2)
  assert.equal((markup.match(/bindgetphonenumber="authorizePhone"/g) || []).length, 2)
  assert.match(read('utils/auth.js'), /url:\s*'\/mobile\/auth\/wechat-phone-login'/)
  assert.match(read('utils/auth.js'), /data:\s*\{\s*phoneCode\s*\}/)
})
