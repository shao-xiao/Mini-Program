const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

test('tenant visitor page uses the compact schema form without duplicating server fields', () => {
  const page = read('pages/visitors/index.wxml')
  const componentScript = read('components/dynamic-form/index.js')
  const componentMarkup = read('components/dynamic-form/index.wxml')
  const componentStyle = read('components/dynamic-form/index.wxss')

  assert.match(page, /<dynamic-form[\s\S]*?module-key="visitors"[\s\S]*?compact="\{\{true\}\}"/)
  assert.match(componentScript, /compact:\s*Boolean/)
  assert.match(componentMarkup, /dynamic-form--compact/)
  assert.match(componentMarkup, /field-\{\{item\.dataType\}\}/)
  assert.match(componentStyle, /\.dynamic-form--compact\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/)
  assert.match(componentStyle, /\.dynamic-form--compact \.field-TEXTAREA,[\s\S]*?grid-column:\s*1\s*\/\s*-1/)
})

test('tenant history is a compact summary panel and hides empty pagination noise', () => {
  const page = read('pages/visitors/index.wxml')
  const style = read('pages/visitors/index.wxss')

  assert.match(page, /tenant-history-panel/)
  assert.match(page, /审核及门禁状态/)
  assert.match(page, /提交后可在此查看审核结果与门禁二维码/)
  assert.match(page, /编号 \{\{item\.code\}\}/)
  assert.match(page, /单位 \{\{item\.visitCompany \|\| item\.companyName\}\}/)
  assert.match(page, /wx:if="\{\{visitors\.length > 0 && \(loadingMore \|\| !noMore\)\}\}"/)
  assert.doesNotMatch(page, /没有更多数据/)
  assert.match(style, /\.tenant-history-empty\s*\{[\s\S]*?display:\s*flex/)
  assert.match(style, /\.tenant-history-item\s*\{[\s\S]*?box-shadow:\s*none/)
})

test('tenant page removes the redundant title card and retains red primary actions', () => {
  const script = read('pages/visitors/index.js')
  const page = read('pages/visitors/index.wxml')
  const style = read('pages/visitors/index.wxss')
  const tenantBranch = page.slice(page.indexOf('<block wx:else>'), page.indexOf('<view wx:if="{{qrPopupVisible}}"'))

  assert.doesNotMatch(tenantBranch, /<view class="title">访客预约<\/view>/)
  assert.match(script, /profile\.tenantInfo\?\.name/)
  assert.match(page, />提交访客预约<\/button>/)
  assert.match(page, />门禁二维码<\/button>/)
  assert.match(style, /\.tenant-submit-button/)
  assert.match(style, /\.qr-button\s*\{[\s\S]*?background:\s*#ed2f2a/)
})
