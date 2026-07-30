const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('workorder form defaults from profile and stores editable display snapshots', () => {
  const js = read('pages/workorders/index.js')
  const wxml = read('pages/workorders/index.wxml')

  assert.match(js, /applyProfileDefaults\(effectiveProfile,\s*this\.data\.form\)/)
  assert.match(js, /displayProfile\.name/)
  assert.match(js, /tenantInfo\.name/)
  const requestBody = js.match(/api\.post\('\/mobile\/workorders',\s*\{([\s\S]*?)\n\s*\}\)/)[1]
  assert.match(requestBody, /reporterName:\s*form\.reporterName\.trim\(\)/)
  assert.match(requestBody, /companyName:\s*form\.companyName\.trim\(\)/)
  assert.match(requestBody, /reportedAt:\s*form\.reportedAt/)
  assert.match(requestBody, /problemCategory:\s*form\.problemCategory/)
  assert.match(requestBody, /description:\s*form\.description\.trim\(\)/)
  assert.match(requestBody, /remark:\s*form\.remark\.trim\(\)/)
  assert.match(requestBody, /fileId:\s*item\.fileId/)
  assert.doesNotMatch(requestBody, /tenantId|mobileUserId|contactPhone|reporterPhone/)
  assert.match(wxml, /data-field="reporterName"/)
  assert.match(wxml, /data-field="companyName"/)
  assert.match(wxml, /报修时间/)
  assert.match(wxml, /问题类型/)
  assert.match(wxml, /问题描述/)
  assert.match(wxml, /现场照片/)
  assert.ok(wxml.indexOf('报修历史记录') < wxml.indexOf('提交报修'))
})

test('workorder page exposes only tenant create, pre-acceptance cancel, progress, and evaluation', () => {
  const js = read('pages/workorders/index.js')
  const wxml = read('pages/workorders/index.wxml')

  assert.match(js, /FEATURE_KEY = 'workorder'/)
  assert.match(js, /item\.status === 'CREATED' \|\| item\.status === 'SUBMITTED'/)
  assert.match(js, /\/mobile\/workorders\/\$\{id\}\/cancel/)
  assert.match(js, /\/mobile\/workorders\/\$\{form\.workOrderId\}\/evaluation/)
  assert.match(wxml, /catchtap="cancelWorkOrder"/)
  assert.match(wxml, /catchtap="openEvaluation"/)
  assert.match(wxml, /bindtap="openDetail"/)
  assert.match(js, /type=workorder&id=/)
  assert.match(wxml, /customFieldSchema/)
  assert.doesNotMatch(wxml, /schema="\{\{fieldSchema\}\}"/)

  for (const forbidden of [
    'notifications',
    'unreadNotificationCount',
    'openNotification',
    'startWorkOrder',
    'completeWorkOrder',
    'Completed in mini program',
    "userType === 'INTERNAL'"
  ]) {
    assert.ok(!js.includes(forbidden), `workorder JS must not contain ${forbidden}`)
  }
  assert.doesNotMatch(wxml, /派单提醒|开始处理|完成工单|openNotification/)
})
