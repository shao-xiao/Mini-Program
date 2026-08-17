const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('mine page keeps one account entry and no legacy binding routes', () => {
  const script = read('pages/mine/index.js')
  const markup = read('pages/mine/index.wxml')

  assert.match(script, /wx\.navigateTo\(\{ url: '\/pages\/me\/index' \}\)/)
  assert.match(script, /accountActionText:\s*'账号管理'/)
  assert.match(script, /accountActionText:\s*'手机号快捷登录'/)
  assert.match(markup, /accountActionText/)
  assert.doesNotMatch(script, /\/pages\/mine\/tenant-bind\/index/)
  assert.doesNotMatch(script, /\/pages\/mine\/staff-bind\/index/)
})

test('mine page only presents identity, personal tenant business, and legal entries', () => {
  const script = read('pages/mine/index.js')
  const markup = read('pages/mine/index.wxml')

  assert.match(markup, /我的身份/)
  assert.match(markup, /所属企业/)
  assert.match(markup, /我的业务/)
  assert.match(markup, /用户服务协议/)
  assert.match(markup, /用户隐私保护指引/)
  assert.match(script, /getVisibleMineFeatures\(profile\)/)
  assert.match(script, /title:\s*'我的账单'/)
  assert.match(script, /title:\s*'我的合同'/)
  assert.match(script, /title:\s*'我的报修'/)
  assert.match(script, /title:\s*'我的会议'/)
  assert.match(script, /title:\s*'我的访客预约'/)

  for (const forbidden of ['园区概览', '公告总数', '可租房源', 'investmentLeadCount', 'availableRoomCount']) {
    assert.equal(script.includes(forbidden) || markup.includes(forbidden), false, `unexpected mine content: ${forbidden}`)
  }
})

test('mine page keeps the original compact card and list-row visual hierarchy', () => {
  const markup = read('pages/mine/index.wxml')
  const styles = read('pages/mine/index.wxss')

  assert.match(markup, /class="card profile-card"/)
  assert.match(markup, /<view class="section-title">身份管理<\/view>/)
  assert.match(markup, /class="card list-section" wx:if="{{isTenant}}"/)
  assert.match(markup, /<view class="item-icon">账<\/view>/)
  assert.match(markup, /class="item-value {{isTenant \? 'bound' : 'not-bound'}}"/)
  assert.match(styles, /\.avatar-wrap\s*{[^}]*border-radius:\s*50%/s)
  assert.match(styles, /\.item-icon\s*{[^}]*width:\s*40rpx;[^}]*height:\s*40rpx;/s)
  assert.match(styles, /\.item-value\s*{[^}]*font-size:\s*26rpx;/s)
  assert.doesNotMatch(styles, /\.profile-card::before/)
  assert.doesNotMatch(markup, /class="account-button"/)
})

test('account page removes registration, password login, manual binding, and diagnostics', () => {
  const script = read('pages/me/index.js')
  const markup = read('pages/me/index.wxml')

  assert.match(markup, /手机号快捷登录/)
  assert.match(script, /authorizeWechatPhone/)

  for (const forbidden of [
    'registerPhoneAccount',
    '/mobile/auth/register-phone',
    '/mobile/auth/register-phone-code',
    '/mobile/auth/bind-tenant',
    '/mobile/auth/bind-internal',
    'openPasswordLogin',
    '账号密码登录',
    '手机号注册',
    '接口设置',
    '/mobile/dev/fixtures',
    'checkBackend'
  ]) {
    assert.equal(script.includes(forbidden) || markup.includes(forbidden), false, `unexpected account flow: ${forbidden}`)
  }
})

test('mine meeting record entry remains records-only and internal workbench entries stay absent', () => {
  const script = read('pages/mine/index.js')
  const markup = read('pages/mine/index.wxml')

  assert.match(script, /target:\s*'\/pages\/meeting\/index\?mode=records'/)
  assert.doesNotMatch(script, /staffOrTenant/)
  assert.doesNotMatch(script, /\/pages\/checkin\/index/)
  assert.doesNotMatch(markup, /员工签到/)
  assert.doesNotMatch(markup, /内部员工绑定/)
})

test('investment page still loads the current WeChat sessions viewing records', () => {
  const script = read('pages/investment/index.js')
  const markup = read('pages/investment/index.wxml')

  assert.match(script, /\/mobile\/investment\/appointments\/mine/)
  assert.match(script, /myLeadRecords/)
  assert.match(markup, /myLeadRecords/)
})
