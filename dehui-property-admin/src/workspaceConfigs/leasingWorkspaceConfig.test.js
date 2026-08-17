import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  createBillingPlanWorkspaceConfig,
  createLeaseWorkspaceConfig,
  feeRuleWorkspaceConfig
} from './leasingWorkspaceConfig.js'

const configs = [
  createLeaseWorkspaceConfig(),
  feeRuleWorkspaceConfig,
  createBillingPlanWorkspaceConfig()
]

test('lease, fee rule and billing plan workspaces use real server paging and permissions', () => {
  assert.deepEqual(configs.map(config => config.query.dataMode), ['server', 'server', 'server'])
  assert.deepEqual(Object.values(configs[0].permissions), [
    'lease:view',
    'lease:create',
    'lease:update',
    'lease:checkin',
    'lease:checkout',
    'lease:renew',
    'lease:terminate',
    'lease:delete'
  ])
  assert.deepEqual(Object.values(configs[1].permissions), [
    'fee-rule:view',
    'fee-rule:create',
    'fee-rule:update',
    'fee-rule:activate'
  ])
  assert.deepEqual(Object.values(configs[2].permissions), [
    'billing-plan:view',
    'billing-plan:create',
    'billing-plan:update',
    'billing-plan:terminate'
  ])
})
test('leasing workspace fields stay aligned with real list DTOs', () => {
  assert.deepEqual(configs[0].fields.filter(field => !field.filterOnly).map(field => field.key), [
    'leaseNo', 'contractNo', 'tenantName', 'roomNames', 'roomCount', 'activeRoomCount',
    'plannedStartDate', 'plannedEndDate', 'actualCheckinAt', 'actualCheckoutAt', 'status', 'updatedAt'
  ])
  assert.deepEqual(configs[1].fields.filter(field => !field.filterOnly).map(field => field.key), [
    'ruleCode', 'ruleName', 'feeType', 'calculationType', 'billingCycle', 'fixedAmount',
    'unitPrice', 'effectiveFrom', 'effectiveTo', 'currentVersionNo', 'status', 'description', 'updatedAt'
  ])
  assert.deepEqual(configs[2].fields.filter(field => !field.filterOnly).map(field => field.key), [
    'planNo', 'planName', 'tenantName', 'contractNo', 'leaseNo', 'billingCycle', 'dueRule',
    'effectiveFrom', 'effectiveTo', 'itemCount', 'estimatedPeriodAmount', 'status', 'updatedAt'
  ])
})

test('leasing QueryPlans serialize filters, multi-sort and grouping for the existing search endpoints', () => {
  for (const config of configs) {
    const groupField = config.fields.find(field => field.groupable)
    const sortField = config.fields.find(field => field.sortable && field.key !== groupField.key)
    const filterField = config.fields.find(field => field.query?.filter || Object.hasOwn(field.query || {}, 'mode'))
    const filterValue = filterField.options?.[0]?.value || 'example'
    const plan = buildWorkspaceQueryPlan({
      fields: config.fields,
      filters: [{ key: filterField.key, operator: 'equals', value: filterValue }],
      sorts: [{ key: sortField.key, order: 'descending' }],
      groups: [{ key: groupField.key, order: 'ascending' }],
      page: 2,
      pageSize: 50,
      query: config.query
    })

    assert.equal(plan.unsupportedFilters.length, 0)
    assert.equal(plan.unsupportedSorts.length, 0)
    assert.equal(plan.unsupportedGroups.length, 0)
    assert.equal(plan.serverParams.page, 2)
    assert.equal(plan.serverParams.pageSize, 50)
    assert.equal(plan.serverParams.groupBy, `${groupField.key}:asc`)
    assert.equal(plan.serverParams.sorts, `${groupField.key}:asc,${sortField.key}:desc`)
  }
})
