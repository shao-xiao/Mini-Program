import assert from 'node:assert/strict'
import test from 'node:test'
import {
  firstMeetingRoomImage,
  isMeetingRoomAvailable,
  localDateKey,
  meetingRoomDescriptionText,
  meetingRoomFacilitiesText,
  meetingRoomFeeText,
  meetingRoomOpenHours,
  normalizeMeetingRoomImages
} from './meetingRoomPresentation.js'

test('local date keys do not inherit UTC day boundaries', () => {
  const localDate = new Date(2026, 6, 29, 0, 30, 0)
  assert.equal(localDateKey(localDate), '2026-07-29')
  assert.equal(localDateKey('not-a-date'), '')
})

test('meeting room images support stored arrays and protected file urls', () => {
  const images = normalizeMeetingRoomImages(JSON.stringify([
    { fileId: 24, name: '会议室.png', url: '/api/files/24', sortOrder: 0 }
  ]))

  assert.equal(images.length, 1)
  assert.equal(images[0].url, '/api/files/24')
  assert.equal(firstMeetingRoomImage(images)?.fileId, 24)
})

test('meeting room images support standard versioned file values with id-only references', () => {
  const image = firstMeetingRoomImage({
    version: 1,
    files: [{ fileId: 31, sortOrder: 0 }]
  })

  assert.equal(image?.fileId, 31)
  assert.equal(image?.url, '/api/files/31')
})

test('meeting room summaries prefer real facilities and format fee and hours', () => {
  const room = {
    facilities: '白板\n投影仪',
    description: '',
    remark: '临近茶水间',
    openTime: '08:00:00',
    closeTime: '22:00:00',
    chargeType: 'HOURLY',
    workdayHourlyRate: 100
  }

  assert.equal(meetingRoomFacilitiesText(room), '白板 投影仪')
  assert.equal(meetingRoomDescriptionText(room), '临近茶水间')
  assert.equal(meetingRoomOpenHours(room), '08:00–22:00')
  assert.equal(meetingRoomFeeText(room), '¥100.00/时')
  assert.equal(meetingRoomFeeText({ chargeType: 'FREE', workdayHourlyRate: 100 }), '免费')
})

test('legacy active meeting rooms remain available in the unified status contract', () => {
  assert.equal(isMeetingRoomAvailable('ACTIVE'), true)
  assert.equal(isMeetingRoomAvailable({ status: 'AVAILABLE' }), true)
  assert.equal(isMeetingRoomAvailable({ status: 'ENABLED' }), true)
  assert.equal(isMeetingRoomAvailable({ status: 'DISABLED' }), false)
})
