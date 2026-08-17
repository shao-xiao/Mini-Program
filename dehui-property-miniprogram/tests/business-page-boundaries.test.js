const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('bill center uses server-backed filters and conditionally renders real energy fields', () => {
  const js = read('pages/bills/index.js')
  const wxml = read('pages/bills/index.wxml')

  assert.match(js, /onLoad\(options = {}\)/)
  assert.match(js, /normalizeInitialType\(options\.type\)/)
  assert.match(js, /\['energy', 'rent', 'parking'\]/)
  assert.match(js, /function billCategory\(item\)/)
  assert.match(js, /query\.status = this\.data\.activeStatus/)
  assert.match(js, /query\.category = this\.data\.activeType/)
  assert.doesNotMatch(js, /applyFilters/)
  assert.match(js, /previousReading/)
  assert.match(js, /currentReading/)
  assert.match(js, /meterReadingDate/)
  assert.match(js, /paidAtText/)
  assert.match(js, /profile\.tenantInfo\.name/)
  assert.match(wxml, /当前没有待缴账单/)
  assert.match(wxml, /已结清/)
  assert.match(wxml, /wx:if="{{item\.hasEnergyDetail}}"/)
  assert.match(wxml, /wx:if="{{item\.hasPreviousReading}}"/)
  assert.match(wxml, /wx:if="{{item\.hasCurrentReading}}"/)
  assert.doesNotMatch(js, /wx\.requestPayment|requestPayment/)
})

test('visitor reservation and anonymous reservation submit only visitor-owned business fields', () => {
  const js = read('pages/visitors/index.js')
  const wxml = read('pages/visitors/index.wxml')
  const json = JSON.parse(read('pages/visitors/index.json'))
  const submitMatch = js.match(/async submitVisitor\(\) \{([\s\S]*?)\n\s*\},\n\n\s*onSchemaChange/)

  assert.ok(submitMatch, 'visitor submission method must exist')
  const requestBody = submitMatch[1]
  for (const field of [
    'visitorName', 'visitorPhone', 'visitTime', 'carPlateNo',
    'visitCompany', 'visitReason', 'remark'
  ]) assert.match(requestBody, new RegExp(`${field}:`))
  assert.match(requestBody, /\/mobile\/public\/visitors/)
  assert.match(requestBody, /\/mobile\/visitors/)
  assert.match(requestBody, /submissionToken/)
  assert.match(requestBody, /visitTime:\s*toApiDateTime\(form\.visitDate,\s*form\.visitClock\)/)
  assert.match(requestBody, /this\.data\.isPublicMode[\s\S]*?\? \{[\s\S]*?submissionToken[\s\S]*?: \{[\s\S]*?visitDate:/)
  assert.doesNotMatch(requestBody, /tenantId|visitedPerson|mobileUserId|applicantMobileUserId/)
  assert.doesNotMatch(js, /tenantId|visitedPerson/)
  assert.match(js, /item\.visitReason \|\| item\.remark/)
  assert.match(js, /created\.invitationCode \|\| created\.visitCode \|\| created\.code/)
  assert.doesNotMatch(wxml, /租户ID|tenantId|被访人|visitedPerson|mobileUserId/)
  assert.match(wxml, /访客预约/)
  assert.match(wxml, /无需登录即可预约/)
  assert.match(wxml, /个人预约历史记录/)
  assert.match(wxml, /public-field-grid/)
  assert.match(wxml, /public-reason-textarea/)
  assert.match(wxml, /public-submit-row/)
  assert.equal(json.navigationBarTitleText, '访客预约')
})

test('all business pages distinguish access and request failures and protect unload', () => {
  for (const page of ['bills', 'contracts', 'workorders', 'meeting', 'visitors']) {
    const js = read(`pages/${page}/index.js`)
    const wxml = read(`pages/${page}/index.wxml`)

    assert.match(js, /code === 401/)
    assert.match(js, /code === 403/)
    assert.match(js, /error\.code \|\| error\.statusCode \|\| error\.status/)
    assert.match(js, /pageState:\s*'error'/)
    assert.match(js, /onUnload\(\)/)
    assert.match(js, /this\._active = false/)
    assert.match(js, /safeSetData\(/)
    assert.match(wxml, /pageState === 'login-required'/)
    assert.match(wxml, /pageState === 'unauthorized'/)
    assert.match(wxml, /pageState === 'error'/)
    assert.match(wxml, /pageState === 'loading'/)
  }
})
