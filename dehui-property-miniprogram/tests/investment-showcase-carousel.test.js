const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('investment center renders backend showcase sections as a dedicated carousel', () => {
  const script = read('pages/investment/index.js')
  const markup = read('pages/investment/index.wxml')

  assert.match(script, /\/mobile\/investment\/showcase/)
  assert.match(script, /SHOWCASE_BANNER_SECTIONS/)
  assert.match(script, /introduction/)
  assert.match(script, /highlight/)
  assert.match(script, /policy/)
  assert.match(script, /buildInvestmentBanners\(overview\)/)
  assert.match(markup, /class="investment-swiper"/)
  assert.match(markup, /autoplay="\{\{investmentBanners\.length > 1\}\}"/)
  assert.match(markup, />招商公告</)
  assert.match(markup, />招商展示</)
  assert.match(script, /openInvestmentRooms\(\)/)
  assert.match(script, /selector: '#investment-room-section'/)
  assert.match(markup, /class="investment-actions"/)
  assert.match(markup, /bindtap="openInvestmentRooms">选择房源/)
  assert.match(markup, /bindtap="callConsultant">电话咨询/)
  assert.doesNotMatch(markup, /hero-card|hero-title|hero-subtitle|hero-meta/)
})

test('investment showcase carousel remains separate from park announcements', () => {
  const script = read('pages/investment/index.js')
  const markup = read('pages/investment/index.wxml')

  assert.doesNotMatch(script, /\/mobile\/announcements/)
  assert.doesNotMatch(markup, /\/pages\/announcements/)
  assert.match(markup, /后台暂未发布招商展示内容/)
})
