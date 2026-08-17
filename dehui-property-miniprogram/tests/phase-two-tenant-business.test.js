const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const capabilities = require(path.join(root, 'utils/capabilities'))

test('PUBLIC sees only implemented public services including sanitized meeting rooms', () => {
  const keys = capabilities.getVisibleHomeFeatures({ userType: 'PUBLIC', capabilities: [] }).map(item => item.key)
  assert.deepEqual(keys, ['announcement', 'investment', 'meetingPublic', 'visitorGuide'])
})

test('employee home never gains company modules without server grants', () => {
  const keys = capabilities.getVisibleHomeFeatures({
    userType: 'TENANT',
    capabilities: ['my-business.view', 'workorder.view', 'meeting.book', 'visitor.create', 'parking.mine.view']
  }).map(item => item.key)
  for (const denied of ['bill', 'contract', 'rooms', 'energy', 'parkingCompany', 'parkingMine']) {
    assert.equal(keys.includes(denied), false)
  }
})

test('my business page consumes one backend summary instead of client aggregation', () => {
  const source = read('pages/my-business/index.js')
  assert.match(source, /\/mobile\/my-business\/summary/)
  assert.doesNotMatch(source, /Promise\.all/)
})

test('room list is paginated and opens a scoped backend detail', () => {
  const source = read('pages/rooms/index.js')
  assert.match(source, /pageSize:\s*20/)
  assert.match(source, /onReachBottom/)
  assert.match(source, /type=room/)
})

test('generic detail maps every tenant business to a real endpoint', () => {
  const source = read('pages/business-detail/index.js')
  for (const endpoint of [
    '/mobile/bills/', '/mobile/contracts/', '/mobile/my-business/rooms/',
    '/mobile/energy/details/', '/mobile/parking/', '/mobile/meetings/bookings/',
    '/mobile/workorders/', '/mobile/visitors/'
  ]) assert.ok(source.includes(endpoint), endpoint)
})

test('meeting detail presents the property review result with tenant-facing labels', () => {
  const source = read('pages/business-detail/index.js')
  const template = read('pages/business-detail/index.wxml')
  const styles = read('pages/business-detail/index.wxss')
  assert.match(source, /reviewReply:\s*'物业回复'/)
  assert.match(source, /reviewedAt:\s*'审核时间'/)
  assert.match(source, /REJECTED:\s*'已驳回'/)
  assert.match(source, /CONFIRMED:\s*'审核通过'/)
  assert.match(source, /meetingReview:\s*null/)
  assert.match(source, /meetingReview:\s*this\.type === 'meeting' \? buildMeetingReview\(data\) : null/)
  assert.match(source, /cancellable:\s*\['PENDING',\s*'BOOKED',\s*'CONFIRMED'\]\.includes\(status\)/)
  assert.match(source, /\/mobile\/meetings\/bookings\/\$\{this\.id\}\/cancel/)
  assert.match(source, /new Set\(\['reviewReply',\s*'reviewedAt'\]\)/)
  assert.match(template, /meeting-review-panel/)
  assert.match(template, /物业审核回复/)
  assert.match(template, /\{\{meetingReview\.reply\}\}/)
  assert.match(template, /wx:if="{{meetingReview\.cancellable}}"[\s\S]*?bindtap="cancelMeetingBooking"/)
  assert.match(styles, /\.meeting-review-copy[\s\S]*white-space:\s*pre-wrap/)
  assert.match(styles, /\.meeting-cancel-button/)
})

test('public meeting view uses its separated sanitized endpoint', () => {
  const source = read('pages/meeting/index.js')
  const template = read('pages/meeting/index.wxml')
  assert.match(source, /\/mobile\/public\/meeting-rooms/)
  assert.match(source, /\/mobile\/public\/meeting-bookings/)
  assert.match(source, /authorizeWechatPhone/)
  assert.match(source, /meeting\.guest\.book/)
  assert.match(template, /open-type="getPhoneNumber"/)
  assert.match(template, /手机号快捷登录/)
  assert.match(template, /!isPublicMode && item\.hasRates/)
  assert.match(template, /!isPublicMode && selectedRoom\.hasRates/)
  assert.match(template, /isBookingMode && selectedRoom/)
  assert.match(template, /item\.coverImageUrl/)
  assert.match(template, /room-detail-fallback/)
  assert.match(source, /roomName:\s*roomDisplayName\(room\)/)
  assert.doesNotMatch(source, /contactPhone:\s*this\.data\.form|tenantId:\s*this\.data\.form|mobileUserId:\s*this\.data\.form/)
})

test('visitor reservation has an anonymous create mode while tenant history remains guarded', () => {
  const source = read('pages/visitors/index.js')
  const template = read('pages/visitors/index.wxml')
  assert.match(source, /isPublicMode/)
  assert.match(source, /\/mobile\/public\/visitors/)
  assert.match(source, /submissionToken/)
  const onLoad = source.match(/onLoad\(options = \{\}\) \{([\s\S]*?)\r?\n\s*\},\r?\n\r?\n\s*onShow/)
  assert.ok(onLoad, 'visitor page must initialize each page session in onLoad')
  assert.match(onLoad[1], /submissionToken:\s*createSubmissionToken\(\)/)
  assert.match(onLoad[1], /form:\s*initialForm\(\)/)
  assert.match(source, /submissionToken:\s*''/)
  const submitVisitor = source.match(/async submitVisitor\(\) \{([\s\S]*?)\r?\n\s*\},\r?\n\r?\n\s*onSchemaChange/)
  assert.ok(submitVisitor, 'visitor submission method must exist')
  const failedSubmission = submitVisitor[1].match(/catch \(error\) \{([\s\S]*?)\r?\n\s*\} finally \{/)
  assert.ok(failedSubmission, 'visitor submission failure branch must exist')
  assert.doesNotMatch(failedSubmission[1], /createSubmissionToken/)
  assert.match(source, /ensurePrivacyAuthorization\('publicVisitor'\)/)
  assert.match(source, /canAccessFeature\(FEATURE_KEY, profile\)/)
  assert.match(template, /无需登录即可预约/)
  assert.match(template, /提交访客预约/)
  assert.doesNotMatch(source, /tenantId\s*:|mobileUserId\s*:|applicantMobileUserId\s*:/)
})

test('workorder submission sends editable display snapshots but never relationship or phone identity fields', () => {
  const source = read('pages/workorders/index.js')
  const body = source.match(/api\.post\('\/mobile\/workorders',\s*\{([\s\S]*?)\n\s*\}\)/)[1]
  assert.doesNotMatch(body, /tenantId|mobileUserId|contactPhone|reporterPhone/)
  assert.match(body, /reporterName/)
  assert.match(body, /companyName/)
  assert.match(body, /reportedAt/)
  assert.match(body, /problemCategory/)
  assert.match(body, /description/)
  assert.match(body, /remark/)
  assert.match(body, /images/)
})

test('energy page consumes server settlement data and reading details', () => {
  const source = read('pages/energy/index.js')
  assert.match(source, /\/mobile\/energy\/summary/)
  assert.match(source, /\/mobile\/energy\/history/)
  assert.match(source, /\/mobile\/energy\/readings/)
  assert.match(source, /type=energy/)
})

test('parking chooses company or mine solely from capability and opens scoped detail', () => {
  const source = read('pages/parking/index.js')
  const markup = read('pages/parking/index.wxml')
  assert.match(source, /canAccessFeature\(feature, profile\)/)
  assert.match(source, /\/mobile\/parking\/\$\{this\.data\.scope\}/)
  assert.match(source, /normalizeSummary\(data\.summary\)/)
  assert.match(source, /type=parking/)
  assert.doesNotMatch(source, /tenantContactId|mobileUserId/)
  assert.match(markup, /公司车位总数/)
  assert.match(markup, /固定车位数量/)
  assert.match(markup, /空余活动车位/)
})

test('all tenant record lists refresh and load additional server pages', () => {
  for (const page of ['bills', 'contracts', 'energy', 'parking', 'meeting', 'workorders', 'visitors', 'rooms']) {
    const source = read(`pages/${page}/index.js`)
    assert.match(source, /onPullDownRefresh/, page)
    assert.match(source, /onReachBottom/, page)
    assert.match(source, /pageSize:\s*20/, page)
    assert.doesNotMatch(source, /pageSize:\s*100/, page)
  }
})
