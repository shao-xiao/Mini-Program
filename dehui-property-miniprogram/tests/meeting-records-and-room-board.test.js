const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('mine meeting entry opens meeting page in records-only mode', () => {
  const mineJs = read('pages/mine/index.js')

  assert.match(mineJs, /key:\s*'meeting'/)
  assert.match(mineJs, /target:\s*'\/pages\/meeting\/index\?mode=records'/)
})

test('meeting page keeps records mode separate and expands meeting rooms by default', () => {
  const meetingJs = read('pages/meeting/index.js')
  const meetingWxml = read('pages/meeting/index.wxml')

  assert.match(meetingJs, /onLoad\(options = {}\)/)
  assert.match(meetingJs, /isRecordMode/)
  assert.match(meetingJs, /isBookingMode/)
  assert.match(meetingJs, /const shouldShowRooms = this\.data\.isBookingMode/)
  assert.match(meetingWxml, /isRecordMode \? bookings : recentBookings/)
  assert.match(meetingWxml, /wx:if="{{isBookingMode && !selectedRoom}}" class="section room-list-section"/)
  assert.match(meetingWxml, /class="room-enter-action">选择时间/)
  assert.doesNotMatch(meetingJs, /roomsQueried/)
  assert.doesNotMatch(meetingJs, /queryRooms\(\)/)
  assert.doesNotMatch(meetingWxml, /bindtap="queryRooms"/)
})

test('meeting defaults stay inside normal booking hours and full refresh clears stale selection', () => {
  const meetingJs = read('pages/meeting/index.js')

  assert.match(meetingJs, /start\.getHours\(\) < 8/)
  assert.match(meetingJs, /start\.getHours\(\) >= 22/)
  assert.doesNotMatch(meetingJs, /start\.getHours\(\) === 23/)
  const loadHome = meetingJs.match(/async loadMeetingHome\(options = \{\}\) \{([\s\S]*?)\r?\n\s*\},\r?\n\r?\n\s*retryLoad/)
  assert.ok(loadHome, 'loadMeetingHome method must exist')
  assert.match(loadHome[1], /if \(!append\)[\s\S]*?_availabilitySequence/)
  assert.match(loadHome[1], /'form\.meetingRoomId':\s*null/)
  assert.match(loadHome[1], /selectedRoom:\s*null/)
})

test('meeting records are above rooms and booking mode only previews recent records', () => {
  const meetingJs = read('pages/meeting/index.js')
  const meetingWxml = read('pages/meeting/index.wxml')
  const meetingWxss = read('pages/meeting/index.wxss')

  assert.match(meetingJs, /recentBookings/)
  assert.match(meetingJs, /hasMoreBookings/)
  assert.match(meetingJs, /recentBookings:\s*bookings\.slice\(0,\s*1\)/)
  assert.match(meetingJs, /hasMoreBookings:\s*bookings\.length > 1 \|\| total > 1/)
  assert.match(meetingJs, /openAllBookingRecords\(\)/)
  assert.match(meetingWxml, /class="section records-section records-section-top"/)
  assert.match(meetingWxml, /会议预约记录/)
  assert.match(meetingWxml, /wx:for="{{isRecordMode \? bookings : recentBookings}}"/)
  assert.match(meetingWxml, /class="booking-stack \{\{isBookingMode && hasMoreBookings \? 'has-stack' : ''\}\}"/)
  assert.match(meetingWxml, /wx:if="{{isBookingMode && hasMoreBookings}}"[\s\S]*?bindtap="openAllBookingRecords"/)
  assert.match(meetingWxss, /\.booking-stack\.has-stack::before/)
  assert.match(meetingWxss, /\.booking-stack\.has-stack::after/)
  assert.doesNotMatch(meetingWxml, /cancel-button|cancelBooking/)
  assert.ok(
    meetingWxml.indexOf('records-section-top') < meetingWxml.indexOf('room-list-section'),
    'meeting records should appear before meeting rooms'
  )
})

test('room-first flow checks the selected interval and blocks occupied submissions', () => {
  const meetingJs = read('pages/meeting/index.js')
  const meetingWxml = read('pages/meeting/index.wxml')
  const meetingWxss = read('pages/meeting/index.wxss')

  assert.match(meetingJs, /availabilityState:\s*'idle'/)
  assert.match(meetingJs, /availabilityMessage/)
  assert.match(meetingJs, /availabilityCheckedKey/)
  assert.match(meetingJs, /_availabilitySequence/)
  assert.match(meetingJs, /checkSelectedRoomAvailability\(\)/)
  assert.match(meetingJs, /\/mobile\/public\/meeting-rooms\/\$\{roomId\}\/availability/)
  assert.match(meetingJs, /availabilityState:\s*available \? 'available' : 'occupied'/)
  assert.match(meetingJs, /this\.data\.availabilityState !== 'available'/)
  assert.match(meetingJs, /selectedRoom\.openTime/)
  assert.match(meetingJs, /selectedRoom\.closeTime/)
  assert.match(meetingWxml, /class="booking-time-section"/)
  assert.match(meetingWxml, /滑动选择开始和结束时间，确认后自动检查/)
  assert.match(meetingWxml, /class="time-availability {{availabilityState}}"/)
  assert.match(meetingWxml, /availabilityMessage/)
  assert.match(meetingWxml, /该时间段空闲，可以预约/)
  assert.match(meetingWxml, /该时间段已被占用，请调整时间/)
  assert.match(meetingWxml, /bindtap="retryAvailabilityCheck"/)
  assert.match(meetingWxml, /disabled="{{submitting \|\| availabilityState !== 'available'/)
  assert.ok(
    meetingWxml.indexOf('booking-selection-strip') < meetingWxml.indexOf('booking-time-section'),
    'selected room should appear before its time controls'
  )
  assert.match(meetingWxss, /\.time-availability\.available/)
  assert.match(meetingWxss, /\.time-availability\.occupied/)
  assert.match(meetingWxss, /\.time-availability\.checking/)
  assert.doesNotMatch(meetingJs, /buildRoomAvailabilityBoard/)
  assert.doesNotMatch(meetingJs, /availabilityBoard/)
  assert.doesNotMatch(meetingWxml, /availability-summary/)

  const selectRoom = meetingJs.match(/selectRoom\(event\) \{([\s\S]*?)\r?\n\s*\},\r?\n\r?\n\s*changeRoom/)
  assert.ok(selectRoom, 'selectRoom method must exist')
  assert.doesNotMatch(selectRoom[1], /!room\.available|unavailableReason/)
  assert.doesNotMatch(meetingWxml, /item\.available \? '' : 'disabled'/)

  const timeHandlers = meetingJs.match(/onDateChange\(event\) \{([\s\S]*?)\r?\n\s*\},\r?\n\r?\n\s*onInput/)
  assert.ok(timeHandlers, 'date and time handlers must exist')
  assert.doesNotMatch(timeHandlers[1], /'form\.meetingRoomId':\s*null|selectedRoom:\s*null|rooms:\s*\[\]/)
  assert.match(timeHandlers[1], /checkSelectedRoomAvailability/)
})

test('meeting room cards normalize public names and keep media or details visible through selection', () => {
  const meetingJs = read('pages/meeting/index.js')
  const meetingWxml = read('pages/meeting/index.wxml')
  const meetingWxss = read('pages/meeting/index.wxss')

  assert.match(meetingJs, /function normalizeMeetingRoom/)
  assert.match(meetingJs, /roomName:\s*roomDisplayName\(room\)/)
  assert.match(meetingJs, /normalizeRoomImages\(room\)/)
  assert.match(meetingJs, /coverImageUrl:\s*imageUrls\[0\]\s*\|\|\s*''/)
  assert.match(meetingJs, /descriptionText:\s*description\s*\|\|\s*'暂无房间说明'/)
  assert.match(meetingJs, /openHoursText:\s*buildOpenHoursText\(room\)/)
  assert.match(meetingWxml, /class="room-card-body"/)
  assert.match(meetingWxml, /wx:if="{{item\.hasCoverImage}}"[\s\S]*?class="room-cover-image"/)
  assert.match(meetingWxml, /wx:else class="room-detail-fallback"/)
  assert.match(meetingWxml, /item\.descriptionText/)
  assert.match(meetingWxml, /item\.openHoursText/)
  assert.match(meetingWxml, /item\.facilitiesText/)
  assert.match(meetingWxml, /selectedRoom\.descriptionText/)
  assert.match(meetingWxml, /selectedRoom\.openHoursText/)
  assert.match(meetingWxml, /selectedRoom\.facilitiesText/)
  assert.match(meetingWxml, /wx:if="{{!isPublicMode && selectedRoom\.hasRates}}"/)
  assert.match(meetingWxss, /\.room-card-body/)
  assert.match(meetingWxss, /\.room-cover-image/)
  assert.match(meetingWxss, /\.room-detail-fallback/)
  assert.match(meetingWxss, /\.booking-selection-cover/)
})

test('meeting page is tenant guarded and renders a complete server-backed booking form', () => {
  const meetingJs = read('pages/meeting/index.js')
  const meetingWxml = read('pages/meeting/index.wxml')
  const meetingJson = read('pages/meeting/index.json')

  assert.match(meetingJs, /FEATURE_KEY = 'meeting'/)
  assert.ok(
    meetingJs.indexOf('canAccessFeature(FEATURE_KEY, profile)') < meetingJs.indexOf("'/mobile/meetings'"),
    'meeting capability guard must run before the meeting request'
  )
  assert.match(meetingJs, /hasPositiveAmount\(item\.calculatedAmount\)/)
  assert.match(meetingWxml, /wx:if="{{item\.hasAmount}}" class="booking-amount"/)
  assert.match(meetingWxml, /wx:if="{{!isPublicMode && item\.hasRates}}" class="room-rates"/)
  assert.match(meetingJs, /loadModuleFieldSchema\('meeting-bookings'\)/)
  assert.match(meetingJson, /dynamic-form/)
  assert.match(meetingWxml, /所属企业/)
  assert.match(meetingWxml, /申请人/)
  assert.match(meetingWxml, /联系电话/)
  assert.match(meetingWxml, /data-field="participantCount"/)
  assert.match(meetingWxml, /data-field="remark"/)
  assert.match(meetingWxml, /dynamic-form/)
  assert.match(meetingWxml, /class="identity-summary"/)
  assert.match(meetingWxml, /class="booking-selection-strip"[\s\S]*?bindtap="changeRoom">更换/)
  assert.match(meetingWxml, /bindtap="toggleExtraFields"/)
  assert.match(meetingWxml, /hidden="{{!showExtraFields}}"/)
  assert.match(meetingWxml, /class="booking-action-bar"/)
  assert.doesNotMatch(meetingWxml, /class="identity-grid"/)
  assert.doesNotMatch(meetingWxml, /<textarea[\s\S]*?data-field="purpose"/)
  assert.match(meetingJs, /showExtraFields:\s*false/)
  assert.match(meetingJs, /hasRequiredCustomFields/)
  assert.match(meetingJs, /toggleExtraFields\(\)/)
  assert.match(meetingJs, /schemaValidation[\s\S]*showExtraFields:\s*true/)
  assert.match(meetingJs, /wx\.pageScrollTo/)
  assert.match(meetingJs, /participantCount:\s*Number\(this\.data\.form\.participantCount\)/)
  assert.match(meetingJs, /remark:\s*this\.data\.form\.remark/)
  assert.match(meetingJs, /contactName:\s*applicantName/)
  assert.doesNotMatch(meetingWxml, /data-field="departmentName"/)
  assert.doesNotMatch(meetingWxml, />部门</)
  assert.doesNotMatch(meetingJs, /departmentName/)
  assert.match(meetingJs, /contactPhone:\s*contactPhone/)
  assert.doesNotMatch(meetingJs, /tenantId:\s*this\.data\.form/)
  assert.doesNotMatch(meetingJs, /Number\(value \|\| 0\)/)
  assert.match(meetingJs, /if \(status === 'BOOKED'\) return '待审核'/)
  assert.match(meetingJs, /if \(status === 'REJECTED'\) return '已驳回'/)
  assert.match(meetingJs, /if \(status === 'CONFIRMED'\) return '审核通过'/)
  assert.match(meetingJs, /reviewReply:\s*String\(item\.reviewReply/)
  assert.match(meetingWxml, /物业回复：[\s\S]*?{{item\.reviewReply}}/)
  assert.match(meetingJs, /等待物业审核/)
})

test('public meeting booking separates editable contact phone from verified WeChat identity', () => {
  const meetingJs = read('pages/meeting/index.js')
  const meetingWxml = read('pages/meeting/index.wxml')

  assert.match(meetingJs, /authorizeWechatPhone/)
  assert.match(meetingJs, /meeting\.guest\.book/)
  assert.match(meetingJs, /profile\.bindingState === 'UNMATCHED'/)
  assert.match(meetingJs, /\/mobile\/public\/meeting-bookings/)
  assert.match(meetingWxml, /open-type="getPhoneNumber"/)
  assert.match(meetingWxml, /浏览会议室无需登录/)
  assert.match(meetingWxml, /手填号码不用于匹配租户身份/)
  assert.match(meetingWxml, /data-field="applicantName"/)
  assert.match(meetingWxml, /data-field="contactPhone"/)
  assert.match(
    meetingWxml,
    /class="booking-action-bar"[\s\S]*?wx:if="{{isPublicMode && !guestPhoneReady && agreementsAccepted}}"[\s\S]*?open-type="getPhoneNumber"/
  )
  assert.doesNotMatch(
    meetingWxml,
    /disabled="{{submitting \|\| availabilityState !== 'available' \|\| \(isPublicMode && !guestPhoneReady\)}}"/
  )
  assert.match(meetingJs, /CONTACT_PHONE_REG/)
  assert.match(meetingJs, /contactPhone:\s*contactPhone/)
  assert.doesNotMatch(meetingJs, /tenantId:\s*this\.data\.form/)
  assert.doesNotMatch(meetingJs, /mobileUserId:\s*this\.data\.form/)
})

test('investment page shows listing cards before viewing appointment form', () => {
  const investmentJs = read('pages/investment/index.js')
  const investmentWxml = read('pages/investment/index.wxml')

  assert.match(investmentJs, /\/mobile\/investment\/listings/)
  assert.match(investmentJs, /filteredRooms/)
  assert.match(investmentJs, /floorFiltersFromRooms/)
  assert.match(investmentWxml, /room-list/)
  assert.match(investmentWxml, /room-card/)
  assert.match(investmentWxml, /filteredRooms/)
  assert.match(investmentWxml, /bindtap="openLeadPopup"/)
  assert.ok(
    investmentWxml.indexOf('room-list') < investmentWxml.indexOf('popup-panel'),
    'listing cards should appear before the appointment popup'
  )
})
