const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('privacy helper explains workorder image usage before invoking wx APIs', () => {
  const privacyPath = path.join(root, 'utils/privacy.js')
  assert.equal(fs.existsSync(privacyPath), true)

  const privacy = read('utils/privacy.js')
  assert.match(privacy, /getPrivacySetting/)
  assert.match(privacy, /requirePrivacyAuthorize/)
  assert.match(privacy, /showModal/)
  assert.match(privacy, /用户隐私保护指引/)
  assert.match(privacy, /现场照片/)
  assert.doesNotMatch(privacy, /文件下载/)
  assert.doesNotMatch(privacy, /文件上传/)
})

test('workorder photo selection requests privacy authorization before chooseImage', () => {
  const workorder = read('pages/workorders/index.js')
  const authorizationIndex = workorder.indexOf('ensurePrivacyAuthorization')
  const chooseImageIndex = workorder.indexOf('wx.chooseImage')

  assert.notEqual(authorizationIndex, -1)
  assert.notEqual(chooseImageIndex, -1)
  assert.ok(authorizationIndex < chooseImageIndex)
  assert.match(workorder, /await ensurePrivacyAuthorization\('workorderImage'/)
})

test('anonymous visitor submission explains contact-data usage before request', () => {
  const privacy = read('utils/privacy.js')
  const visitors = read('pages/visitors/index.js')
  const authorizationIndex = visitors.indexOf("ensurePrivacyAuthorization('publicVisitor')")
  const requestIndex = visitors.indexOf("'/mobile/public/visitors'")

  assert.match(privacy, /publicVisitor/)
  assert.match(privacy, /姓名、手机号、来访时间和来访事由/)
  assert.notEqual(authorizationIndex, -1)
  assert.notEqual(requestIndex, -1)
  assert.ok(authorizationIndex < requestIndex)
})

test('file transfer wrappers do not declare extra privacy scenarios', () => {
  const request = read('utils/request.js')

  assert.doesNotMatch(request, /ensurePrivacyAuthorization/)
  assert.doesNotMatch(request, /fileDownload/)
  assert.doesNotMatch(request, /fileUpload/)
  assert.match(request, /wx\.downloadFile/)
  assert.match(request, /wx\.uploadFile/)
})

test('app config still does not declare unused location or camera private infos', () => {
  const appConfig = JSON.parse(read('app.json'))

  assert.equal(appConfig.requiredPrivateInfos, undefined)
  assert.equal(appConfig.permission, undefined)
})
