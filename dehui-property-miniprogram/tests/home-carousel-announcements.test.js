const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('home page requests only real announcements selected for the carousel', () => {
  const script = read('pages/index/index.js')
  const markup = read('pages/index/index.wxml')

  assert.match(script, /\/mobile\/announcements\/carousel/)
  assert.match(script, /announcementState/)
  assert.match(markup, /暂无重点公告/)
  assert.match(markup, /公告暂时无法加载/)
  assert.doesNotMatch(script, /defaultBanners/)
  assert.doesNotMatch(script, /banner-park-welcome/)
})

test('announcement list page still requests all published announcements', () => {
  const source = read('pages/announcements/index.js')

  assert.match(source, /\/mobile\/announcements['"]/)
  assert.doesNotMatch(source, /\/carousel/)
  assert.match(source, /_loadSequence/)
  assert.match(source, /isCurrentRequest\(requestId\)/)
})

test('home page exposes capability-backed asset services and removes legacy staff entry points', () => {
  const script = read('pages/index/index.js')
  const markup = read('pages/index/index.wxml')

  for (const forbidden of [
    '员工签到',
    '内部工作台',
    '/pages/checkin/index',
    '/pages/mine/staff-bind/index',
    'staffOrTenant'
  ]) {
    assert.equal(script.includes(forbidden) || markup.includes(forbidden), false, `unexpected home entry: ${forbidden}`)
  }
  assert.match(script, /物业员工/)
  assert.match(script, /\/pages\/energy\/index/)
  assert.match(script, /\/pages\/parking\/index\?scope=company/)
  assert.doesNotMatch(script, /\/pages\/parking\/index\?scope=mine/)
  assert.match(script, /getUserType\(profile\) === 'PUBLIC'/)
})

test('home page delegates PUBLIC and TENANT feature visibility to capability utility', () => {
  const script = read('pages/index/index.js')

  assert.match(script, /require\('\.\.\/\.\.\/utils\/capabilities'\)/)
  assert.match(script, /getVisibleHomeFeatures\(profile\)/)
  assert.match(script, /getFeatureAccessState\(item\.key, this\.data\.profile\)/)
  assert.doesNotMatch(script, /allowedKeys/)
  assert.match(script, /title:\s*'招商中心'/)
  assert.match(script, /meetingPublic:\s*\{[\s\S]*?url:\s*'\/pages\/meeting\/index\?mode=public'/)
  assert.match(script, /visitorGuide:\s*\{[\s\S]*?url:\s*'\/pages\/visitors\/index\?mode=public'/)
  assert.match(script, /key:\s*'tenantLogin'/)
  assert.match(script, /title:\s*'租户登录'/)
  assert.match(script, /title:\s*'访客预约'/)
  assert.match(script, /title:\s*'访客预约'/)
  assert.doesNotMatch(script, /title:\s*'我的合同'/)
  assert.doesNotMatch(script, /title:\s*'我的车位'/)
  assert.match(script, /identityState:\s*code === 401 \? 'login-required' : \(code === 403 \? 'unauthorized'/)
  assert.match(script, /onUnload\(\)/)
  assert.match(script, /_identitySequence/)
  assert.match(script, /_homeSequence/)
  assert.match(script, /_pageSequence/)
})

test('home page keeps full-width cards and uses a WeChat-compatible three-column module layout', () => {
  const markup = read('pages/index/index.wxml')
  const styles = read('pages/index/index.wxss')

  assert.match(markup, /class="card intro-card"/)
  assert.match(markup, /class="card banner-card"/)
  assert.match(markup, /class="module-grid"/)
  assert.match(markup, /<view[\s\S]*?class="module-item"[\s\S]*?role="button"/)
  assert.doesNotMatch(markup, /<button[^>]*class="module-item"/)
  assert.match(styles, /\.module-grid\s*{[^}]*display:\s*flex;[^}]*flex-wrap:\s*wrap;/s)
  assert.match(styles, /\.module-item\s*{[^}]*flex:\s*0\s+0\s+calc\(\(100%\s*-\s*28rpx\)\s*\/\s*3\);/s)
  assert.match(styles, /\.module-item:nth-child\(3n\)\s*{[^}]*margin-right:\s*0;/s)
  assert.doesNotMatch(styles, /grid-template-columns:\s*repeat\(3/)
  assert.doesNotMatch(markup, /class="service-list"/)
  assert.doesNotMatch(markup, /class="section-block/)
})
