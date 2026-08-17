import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  investmentContentWorkspaceConfig,
  investmentLeadWorkspaceConfig,
  investmentListingWorkspaceConfig
} from './investmentWorkspaceConfig.js'

const configs = [
  investmentContentWorkspaceConfig,
  investmentListingWorkspaceConfig,
  investmentLeadWorkspaceConfig
]

test('investment workspaces use server paging and concrete permission codes', () => {
  assert.deepEqual(configs.map(config => config.query.dataMode), ['server', 'server', 'server'])
  assert.deepEqual(Object.values(configs[0].permissions), [
    'investment-content:view',
    'investment-content:create',
    'investment-content:update',
    'investment-content:publish',
    'investment-content:delete'
  ])
  assert.deepEqual(Object.values(configs[1].permissions), [
    'investment-listing:view',
    'investment-listing:create',
    'investment-listing:update',
    'investment-listing:publish',
    'investment-listing:image',
    'investment-listing:delete'
  ])
  assert.deepEqual(Object.values(configs[2].permissions), [
    'investment-lead:view',
    'investment-lead:follow',
    'investment-lead:convert',
    'investment-lead:delete'
  ])
})

test('investment workspace fields stay aligned with real list responses', () => {
  assert.deepEqual(configs[0].fields.filter(field => !field.filterOnly).map(field => field.key), [
    'sectionKey', 'title', 'subtitle', 'sortOrder', 'effectiveAt', 'expiresAt',
    'publishStatus', 'updatedAt'
  ])
  assert.deepEqual(configs[1].fields.filter(field => !field.filterOnly).map(field => field.key), [
    'coverImageUrl', 'title', 'buildingName', 'floorName', 'roomNumber', 'area',
    'roomType', 'status', 'publishStatus', 'recommended', 'reservable',
    'sortOrder', 'imageCount', 'updatedAt'
  ])
  assert.deepEqual(configs[2].fields.filter(field => !field.filterOnly).map(field => field.key), [
    'name', 'phone', 'companyName', 'source', 'intendedRoom', 'desiredArea', 'intendedUse',
    'preferredVisitTime', 'remark', 'status', 'createdTime'
  ])
})

test('investment QueryPlans serialize real filters, multi-sort and grouping', () => {
  for (const config of configs) {
    const filterField = config.fields.find(field => field.query?.filter || Object.hasOwn(field.query || {}, 'mode'))
    const groupField = config.fields.find(field => field.groupable)
    const sortField = config.fields.find(field => field.sortable && field.key !== groupField.key)
    const filterValue = filterField.options?.[0]?.value || 'example'
    const plan = buildWorkspaceQueryPlan({
      fields: config.fields,
      filters: [{ key: filterField.key, operator: 'equals', value: filterValue }],
      sorts: [{ key: sortField.key, order: 'descending' }],
      groups: [{ key: groupField.key, order: 'ascending' }],
      page: 3,
      pageSize: 20,
      query: config.query
    })

    assert.equal(plan.unsupportedFilters.length, 0)
    assert.equal(plan.unsupportedSorts.length, 0)
    assert.equal(plan.unsupportedGroups.length, 0)
    assert.equal(plan.serverParams.page, 3)
    assert.equal(plan.serverParams.pageSize, 20)
    assert.equal(plan.serverParams.groupBy, `${groupField.key}:asc`)
    assert.equal(plan.serverParams.sorts, `${groupField.key}:asc,${sortField.key}:desc`)
  }
})
