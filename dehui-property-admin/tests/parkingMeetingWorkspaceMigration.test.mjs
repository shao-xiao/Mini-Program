import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

const pages = {
  parkingSpace: read('src/views/parking/ParkingSpaceList.vue'),
  parkingBill: read('src/views/parking/ParkingBillList.vue'),
  meetingRoom: read('src/views/meeting/MeetingRoomList.vue'),
  meetingBooking: read('src/views/meeting/MeetingBookingList.vue')
}
const meetingWorkspaceConfig = read('src/workspaceConfigs/parkingMeetingWorkspaceConfig.js')

test('active parking and meeting list pages use the shared workspace stack', () => {
  for (const [name, source] of Object.entries(pages)) {
    assert.match(source, /<DataWorkspace\b/, `${name} must render DataWorkspace`)
    assert.match(source, /<DhicDataToolbar\b/, `${name} must render DhicDataToolbar`)
    assert.match(source, /<WorkspaceDataGrid\b/, `${name} must render WorkspaceDataGrid`)
    assert.doesNotMatch(source, /<el-table(?:\s|>)/, `${name} must not own the primary table`)
  }
})

test('parking space keeps server paging and real business mutations', () => {
  const source = pages.parkingSpace
  assert.match(source, /\.\.\.serverQueryParams\.value/)
  assert.match(source, /\.\.\.pageParams\(pagination\)/)
  assert.match(source, /request\.get\('\/parking\/spaces\/summary'/)
  assert.match(source, /bindParkingSpace/)
  assert.match(source, /releaseParkingSpace/)
  assert.match(source, /updateParkingAssignmentMonthlyFee/)
  assert.match(source, /activeCustomFields/)
  assert.match(source, /saveCustomFieldRecord/)
  assert.doesNotMatch(source, /createCustomFieldDefinition/)
})

test('parking activity-space binding does not require a plate number', () => {
  const source = pages.parkingSpace
  const bindRules = source.match(/const bindRules = \{([\s\S]*?)\n\}/)?.[1] || ''

  assert.doesNotMatch(bindRules, /\bplateNo\s*:/)
  assert.match(source, /placeholder="可选，活动车位可不绑定车牌"/)
})

test('meeting pages retain their real CRUD and state transition endpoints', () => {
  assert.match(pages.meetingRoom, /request\.get\('\/meeting-rooms'/)
  assert.match(pages.meetingRoom, /request\.post\('\/meeting-rooms'/)
  assert.match(pages.meetingRoom, /request\.put\(`\/meeting-rooms\/\$\{form\.id\}`/)
  assert.match(pages.meetingRoom, /request\.delete\(`\/meeting-rooms\/\$\{row\.id\}`/)

  assert.match(pages.meetingBooking, /request\.get\('\/meeting-bookings'/)
  assert.match(pages.meetingBooking, /request\.post\('\/meeting-bookings'/)
  assert.match(pages.meetingBooking, /`\/meeting-bookings\/\$\{reviewBooking\.value\.id\}\/confirm`/)
  assert.match(pages.meetingBooking, /`\/meeting-bookings\/\$\{reviewBooking\.value\.id\}\/reject`/)
  assert.match(pages.meetingBooking, /`\/meeting-bookings\/\$\{row\.id\}\/cancel`/)
  assert.match(pages.meetingBooking, /`\/meeting-bookings\/\$\{row\.id\}\/complete`/)
})

test('meeting booking review exposes submission order and cancelled records', () => {
  assert.match(meetingWorkspaceConfig, /key:\s*'createdAt',\s*label:\s*'提交时间'/)
  assert.match(meetingWorkspaceConfig, /sorts:\s*\[\{\s*key:\s*'createdAt',\s*order:\s*'descending'\s*\}\]/)
  assert.match(meetingWorkspaceConfig, /id:\s*'cancelled-meeting-bookings'[\s\S]*?value:\s*'CANCELLED'/)
  assert.match(pages.meetingBooking, /CANCELLED/)
})

test('meeting booking review requires an explicit decision and returns a reply to the applicant', () => {
  const source = pages.meetingBooking
  assert.match(source, /审核预约/)
  assert.match(source, /v-model="reviewForm\.decision"/)
  assert.match(source, /v-model\.trim="reviewForm\.reviewReply"/)
  assert.match(source, /reviewForm\.decision === 'REJECT'/)
  assert.match(source, /reviewReply:\s*reviewForm\.reviewReply/)
  assert.match(source, /row\.status === 'PENDING' \|\| row\.status === 'BOOKED'/)
  assert.match(source, /物业回复/)
})

test('parking and meeting actions remain permission-code based', () => {
  const source = Object.values(pages).join('\n')
  assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
  assert.match(pages.parkingSpace, /hasPermission\('parking:create'\)/)
  assert.match(pages.parkingSpace, /hasPermission\('parking:update'\)/)
  assert.match(pages.parkingSpace, /hasPermission\('parking:delete'\)/)
  assert.match(pages.meetingRoom, /hasPermission\('meeting-room:create'\)/)
  assert.match(pages.meetingRoom, /hasPermission\('meeting-room:update'\)/)
  assert.match(pages.meetingRoom, /hasPermission\('meeting-room:delete'\)/)
  assert.match(pages.meetingBooking, /hasPermission\('meeting-booking:create'\)/)
  assert.match(pages.meetingBooking, /hasPermission\('meeting-booking:confirm'\)/)
  assert.match(pages.meetingBooking, /hasPermission\('meeting-booking:cancel'\)/)
  assert.match(pages.meetingBooking, /hasPermission\('meeting-booking:complete'\)/)
})

test('meeting booking still keeps internal employee calculations local and free', () => {
  assert.match(pages.meetingBooking, /const applyInternalFree = \(\) => \{[\s\S]*form\.feeType = 'INTERNAL_FREE'[\s\S]*form\.amount = 0[\s\S]*\}/)
  assert.match(pages.meetingBooking, /const calculate = async \(\) => \{[\s\S]*if \(form\.sourceType === 'INTERNAL'\)[\s\S]*applyInternalFree\(\)[\s\S]*return/)
})
