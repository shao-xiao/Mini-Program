const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

test('tenant visitor page waits for approval and downloads protected QR images', () => {
  const script = read('pages/visitors/index.js')
  const markup = read('pages/visitors/index.wxml')
  const style = read('pages/visitors/index.wxss')

  assert.match(script, /status === 'PENDING'\) return '待审核'/)
  assert.match(script, /status === 'REJECTED'\) return '已驳回'/)
  assert.match(script, /api\.download\(visitor\.qrCodeUrl/)
  assert.match(script, /visitDate:\s*dynamicPayload\.visitDate \|\| form\.visitDate/)
  assert.match(script, /visitTime:\s*dynamicPayload\.visitTime \|\| form\.visitClock/)
  assert.match(markup, /出示门禁二维码/)
  assert.match(markup, /show-menu-by-longpress/)
  assert.match(style, /\.qr-image[\s\S]*width:\s*320rpx[\s\S]*height:\s*320rpx/)
})

test('staff verification is offered only after desktop review approval', () => {
  const list = read('subPackages/staff/pages/visitors/index.wxml')
  const detail = read('subPackages/staff/pages/visitors/detail.wxml')

  assert.match(list, /data-status="REGISTERED"[^>]*>待核验/)
  assert.match(detail, /detail\.status === 'REGISTERED'/)
  assert.doesNotMatch(detail, /detail\.status === 'PENDING'.*核验到访/)
})

test('shared authenticated downloader preserves image file extensions', () => {
  const request = read('utils/request.js')
  assert.match(request, /'jpg', 'jpeg', 'png', 'webp'/)
})
