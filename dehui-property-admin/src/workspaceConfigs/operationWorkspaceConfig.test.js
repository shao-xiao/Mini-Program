import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  announcementStatusOptions,
  announcementWorkspaceConfig,
  inspectionWorkspaceConfig,
  visitorWorkspaceConfig
} from './operationWorkspaceConfig.js'

test('operation workspaces keep real data modes and permission codes', () => {
  assert.equal(visitorWorkspaceConfig.query.dataMode, 'client')
  assert.equal(inspectionWorkspaceConfig.query.dataMode, 'server')
  assert.equal(announcementWorkspaceConfig.query.dataMode, 'client')

  assert.deepEqual(Object.values(visitorWorkspaceConfig.permissions), [
    'visitor:view', 'visitor:create', 'visitor:update'
  ])
  assert.deepEqual(Object.values(inspectionWorkspaceConfig.permissions), [
    'inspection:view', 'inspection:create', 'inspection:update', 'inspection:delete'
  ])
  assert.deepEqual(Object.values(announcementWorkspaceConfig.permissions), [
    'announcement:view', 'announcement:create', 'announcement:update', 'announcement:delete'
  ])
})

test('operation workspace columns only expose real or explicitly derived list fields', () => {
  assert.deepEqual(visitorWorkspaceConfig.fields.map(field => field.key), [
    'code', 'visitorName', 'visitorPhone', 'carPlateNo', 'tenantName', 'visitTime', 'status', 'remark'
  ])
  assert.deepEqual(inspectionWorkspaceConfig.fields.map(field => field.key), [
    'inspectionDate', 'inspector', 'inspectionType', 'area', 'target', 'result',
    'problemDescription', 'actionTaken', 'status', 'remark', 'createdAt', 'updatedAt'
  ])
  assert.deepEqual(announcementWorkspaceConfig.fields.map(field => field.key), [
    'code', 'title', 'summary', 'type', 'showOnHome', 'carouselSort', 'status',
    'publishTime', 'effectiveAt', 'expiresAt', 'createdTime', 'content'
  ])
})

test('announcement status options include the backend disabled state', () => {
  assert.deepEqual(
    announcementStatusOptions.find(option => option.value === 'DISABLED'),
    { label: '已下架', value: 'DISABLED', tone: 'info' }
  )
})

test('inspection QueryPlan serializes filters, date range, group ordering and explicit sorting', () => {
  const plan = buildWorkspaceQueryPlan({
    fields: inspectionWorkspaceConfig.fields,
    filters: [
      { key: 'inspector', operator: 'contains', value: '测试' },
      { key: 'inspectionDate', operator: 'dateRange', value: ['2026-07-01', '2026-07-31'] },
      { key: 'status', operator: 'equals', value: 'OPEN' }
    ],
    sorts: [{ key: 'updatedAt', order: 'descending' }],
    groups: [{ key: 'inspectionType', order: 'ascending' }],
    page: 2,
    pageSize: 20,
    query: inspectionWorkspaceConfig.query
  })

  assert.equal(plan.unsupportedFilters.length, 0)
  assert.equal(plan.unsupportedSorts.length, 0)
  assert.equal(plan.unsupportedGroups.length, 0)
  assert.deepEqual(plan.serverParams, {
    endDate: '2026-07-31',
    groupBy: 'inspectionType:asc',
    inspector: '测试',
    page: 2,
    pageSize: 20,
    sorts: 'inspectionType:asc,updatedAt:desc',
    startDate: '2026-07-01',
    status: 'OPEN'
  })
})

test('client operation workspaces support complete-array filtering, sorting and grouping', () => {
  for (const config of [visitorWorkspaceConfig, announcementWorkspaceConfig]) {
    const groupField = config.fields.find(field => field.groupable)
    const sortField = config.fields.find(field => field.sortable && field.key !== groupField.key)
    const filterField = config.fields.find(field => field.options)
    const plan = buildWorkspaceQueryPlan({
      fields: config.fields,
      filters: [{ key: filterField.key, operator: 'equals', value: filterField.options[0].value }],
      sorts: [{ key: sortField.key, order: 'descending' }],
      groups: [{ key: groupField.key, order: 'ascending' }],
      query: config.query
    })

    assert.equal(plan.mode, 'client')
    assert.equal(plan.clientFilters.length, 1)
    assert.equal(plan.clientSorts.length, 1)
    assert.equal(plan.clientGroups.length, 1)
    assert.deepEqual(plan.serverParams, {})
  }
})
