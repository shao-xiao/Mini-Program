const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function source(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('staff visitor list exposes the pending review queue with localized statuses', () => {
  const script = source('subPackages/staff/pages/visitors/index.js')
  const template = source('subPackages/staff/pages/visitors/index.wxml')

  assert.match(script, /PENDING:\s*\{\s*text:\s*'待审核'/)
  assert.match(script, /REGISTERED:\s*\{\s*text:\s*'待核验'/)
  assert.match(template, /data-status="PENDING"[^>]*>待审核</)
  assert.match(template, /审核、二维码与到访核验均由服务端统一记录/)
})

test('staff visitor detail closes review, QR and verification actions', () => {
  const script = source('subPackages/staff/pages/visitors/detail.js')
  const template = source('subPackages/staff/pages/visitors/detail.wxml')

  assert.match(script, /\/mobile\/staff\/visitors\/\$\{this\.data\.id\}\/\$\{action\}/)
  assert.match(script, /\/mobile\/staff\/visitors\/\$\{this\.data\.id\}\/qr-code/)
  assert.match(script, /uploadInternalImages\(1\)/)
  assert.match(script, /previewProtected/)
  assert.match(template, /detail\.status === 'PENDING'/)
  assert.match(template, />审核通过</)
  assert.match(template, />驳回</)
  assert.match(template, /'上传二维码'/)
  assert.match(template, />核验到访</)
})

test('reject requires a reason before the write request', () => {
  const script = source('subPackages/staff/pages/visitors/detail.js')

  assert.match(script, /if \(!reviewRemark\)/)
  assert.match(script, /请填写驳回原因/)
})
