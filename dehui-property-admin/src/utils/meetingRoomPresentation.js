import { normalizeCustomFieldFiles } from './customFieldValues.js'

const compactText = (value) => String(value || '').trim().replace(/\s+/g, ' ')
const AVAILABLE_ROOM_STATUSES = new Set(['ACTIVE', 'AVAILABLE', 'ENABLED'])

export function localDateKey(date = new Date()) {
  const value = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(value.getTime())) return ''
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function normalizeMeetingRoomImages(value) {
  return normalizeCustomFieldFiles(value)
    .map(file => ({
      ...file,
      url: file.url || (file.fileId ? `/api/files/${file.fileId}` : '')
    }))
    .filter(file => Boolean(file.url))
}

export function firstMeetingRoomImage(value) {
  return normalizeMeetingRoomImages(value)[0] || null
}

export function isMeetingRoomAvailable(roomOrStatus = '') {
  const status = typeof roomOrStatus === 'object' && roomOrStatus
    ? roomOrStatus.status
    : roomOrStatus
  return AVAILABLE_ROOM_STATUSES.has(String(status || '').toUpperCase())
}

export function meetingRoomFeeText(room = {}) {
  if (String(room.chargeType || '').toUpperCase() === 'FREE') return '免费'
  const amount = Number(room.workdayHourlyRate ?? room.hourlyPrice ?? 0)
  return `¥${Number.isFinite(amount) ? amount.toFixed(2) : '0.00'}/时`
}

export function meetingRoomOpenHours(room = {}) {
  const openTime = compactText(room.openTime).slice(0, 5)
  const closeTime = compactText(room.closeTime).slice(0, 5)
  if (openTime && closeTime) return `${openTime}–${closeTime}`
  if (openTime) return `${openTime} 起开放`
  if (closeTime) return `${closeTime} 前开放`
  return '开放时间未配置'
}

export function meetingRoomFacilitiesText(room = {}) {
  return compactText(room.facilities) || '设施未配置'
}

export function meetingRoomDescriptionText(room = {}) {
  return compactText(room.description) || compactText(room.remark) || '暂无描述'
}
