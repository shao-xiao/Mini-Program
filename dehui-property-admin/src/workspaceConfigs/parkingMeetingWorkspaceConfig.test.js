import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  buildMeetingBookingMetricSnapshot,
  buildMeetingRoomMetricSnapshot,
  meetingBookingWorkspaceConfig,
  meetingRoomWorkspaceConfig,
  parkingSpaceWorkspaceConfig
} from './parkingMeetingWorkspaceConfig.js'

test('parking and meeting workspaces use real data modes and permission codes', () => {
  assert.equal(parkingSpaceWorkspaceConfig.query.dataMode, 'server')
  assert.equal(meetingRoomWorkspaceConfig.query.dataMode, 'client')
  assert.equal(meetingBookingWorkspaceConfig.query.dataMode, 'client')

  assert.deepEqual(Object.values(parkingSpaceWorkspaceConfig.permissions), [
    'parking:view', 'parking:create', 'parking:update', 'parking:delete'
  ])
  assert.deepEqual(Object.values(meetingRoomWorkspaceConfig.permissions), [
    'meeting-room:view', 'meeting-room:create', 'meeting-room:update', 'meeting-room:delete'
  ])
  assert.deepEqual(Object.values(meetingBookingWorkspaceConfig.permissions), [
    'meeting-booking:view', 'meeting-booking:create', 'meeting-booking:confirm',
    'meeting-booking:cancel', 'meeting-booking:complete'
  ])
})

test('workspace columns only expose fields returned by the real list responses', () => {
  assert.deepEqual(parkingSpaceWorkspaceConfig.fields.map(field => field.key), [
    'spaceNo', 'area', 'floor', 'type', 'monthlyFee', 'monthlyRate',
    'partyNameSnapshot', 'plateNo', 'status', 'remark'
  ])
  assert.deepEqual(meetingRoomWorkspaceConfig.fields.map(field => field.key), [
    'code', 'name', 'capacity', 'location', 'facilities', 'openTime', 'closeTime',
    'workdayHourlyRate', 'status', 'remark'
  ])
  assert.deepEqual(meetingBookingWorkspaceConfig.fields.map(field => field.key), [
    'bookingNo', 'createdAt', 'roomName', 'sourceType', 'tenantName', 'applicantName',
    'departmentName', 'purpose', 'participantCount', 'startTime', 'endTime', 'status',
    'unitPriceSnapshot', 'amount', 'billingId', 'reviewReply', 'reviewerName', 'reviewedAt', 'remark'
  ])
})

test('meeting bookings default to newest submissions and retain a cancelled audit view', () => {
  const createdAtField = meetingBookingWorkspaceConfig.fields.find(field => field.key === 'createdAt')
  assert.deepEqual(createdAtField, {
    key: 'createdAt',
    label: '提交时间',
    type: 'date',
    format: 'datetime',
    sortable: true,
    width: 165
  })
  assert.deepEqual(meetingBookingWorkspaceConfig.table.defaultPrefs.sorts, [
    { key: 'createdAt', order: 'descending' }
  ])
  assert.deepEqual(
    meetingBookingWorkspaceConfig.views.find(view => view.id === 'cancelled-meeting-bookings'),
    {
      id: 'cancelled-meeting-bookings',
      name: '已取消预约',
      system: true,
      prefs: { filters: [{ key: 'status', operator: 'equals', value: 'CANCELLED' }] }
    }
  )
})

test('parking QueryPlan serializes server filters, grouping and sorting', () => {
  const plan = buildWorkspaceQueryPlan({
    fields: parkingSpaceWorkspaceConfig.fields,
    filters: [
      { key: 'status', operator: 'equals', value: 'OCCUPIED' },
      { key: 'monthlyFee', operator: 'between', value: [300, 800] }
    ],
    groups: [{ key: 'area', order: 'ascending' }],
    sorts: [{ key: 'spaceNo', order: 'descending' }],
    page: 2,
    pageSize: 20,
    query: parkingSpaceWorkspaceConfig.query
  })

  assert.deepEqual(plan.serverParams, {
    groupBy: 'area:asc',
    maxMonthlyFee: 800,
    minMonthlyFee: 300,
    page: 2,
    pageSize: 20,
    sorts: 'area:asc,spaceNo:desc',
    status: 'OCCUPIED'
  })
})

test('meeting workspaces filter, sort and group complete client arrays', () => {
  for (const config of [meetingRoomWorkspaceConfig, meetingBookingWorkspaceConfig]) {
    const filterField = config.fields.find(field => field.options)
    const groupField = config.fields.find(field => field.groupable)
    const sortField = config.fields.find(field => field.sortable && field.key !== groupField.key)
    const plan = buildWorkspaceQueryPlan({
      fields: config.fields,
      filters: [{ key: filterField.key, operator: 'equals', value: filterField.options[0].value }],
      groups: [{ key: groupField.key, order: 'ascending' }],
      sorts: [{ key: sortField.key, order: 'descending' }],
      query: config.query
    })

    assert.equal(plan.mode, 'client')
    assert.equal(plan.clientFilters.length, 1)
    assert.equal(plan.clientGroups.length, 1)
    assert.equal(plan.clientSorts.length, 1)
    assert.deepEqual(plan.serverParams, {})
  }
})

test('meeting metrics are calculated from actual loaded and visible records', () => {
  const rooms = [
    { status: 'AVAILABLE', capacity: 8 },
    { status: 'DISABLED', capacity: 12 }
  ]
  assert.deepEqual(buildMeetingRoomMetricSnapshot(rooms, rooms.slice(0, 1)), {
    total: 2,
    filtered: 1,
    available: 1,
    unavailable: 1,
    totalCapacity: 20
  })

  const bookings = [
    { status: 'PENDING', amount: 0 },
    { status: 'CONFIRMED', amount: 120 }
  ]
  assert.deepEqual(buildMeetingBookingMetricSnapshot(bookings, bookings.slice(1)), {
    total: 2,
    filtered: 1,
    pending: 1,
    confirmed: 1,
    totalAmount: 120
  })
})
