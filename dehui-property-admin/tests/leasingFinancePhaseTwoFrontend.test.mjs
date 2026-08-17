import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

import {
  getPathPermissionCodes,
  getPathViewPermissionCodes
} from '../src/config/permissionRules.js'

const adminRoot = path.resolve(import.meta.dirname, '..')

function readSource(relativePath) {
  const fullPath = path.join(adminRoot, relativePath)
  return fs.existsSync(fullPath) ? fs.readFileSync(fullPath, 'utf8') : ''
}

test('fee-rule ledger uses V2 server paging and summary without the legacy empty endpoint', () => {
  const source = readSource('src/views/tenant/FeeRuleList.vue')

  assert.match(source, /request\.get\(\s*['"`]\/fee-rules\/search['"`]/)
  assert.match(source, /request\.get\(\s*['"`]\/fee-rules\/summary['"`]/)
  assert.match(source, /request\.post\(\s*['"`]\/fee-rules['"`]/)
  assert.doesNotMatch(source, /request\.(?:get|post|put|delete)\(\s*['"`]\/feerules/)
  assert.match(source, /createLatestRequestGuard\(\)/)
  assert.match(source, /readPage\(data\)/)
  assert.match(source, /rules\.value\s*=\s*\[\]/)
  assert.match(source, /pagination\.total\s*=\s*0/)
})

test('fee-rule form models versioned rule fields and keeps unconfirmed calculations disabled', () => {
  const source = [
    readSource('src/views/tenant/FeeRuleList.vue'),
    readSource('src/workspaceConfigs/leasingWorkspaceConfig.js')
  ].join('\n')

  for (const field of ['ruleCode', 'ruleName', 'feeType', 'calculationType', 'description']) {
    assert.match(source, new RegExp(`form\\.${field}`), `missing fee rule field ${field}`)
  }
  for (const calculationType of ['FIXED', 'AREA_UNIT_PRICE', 'QUANTITY_UNIT_PRICE', 'ONE_TIME']) {
    assert.match(source, new RegExp(`['\"\`]${calculationType}['\"\`]`))
  }
  for (const disabledType of ['PERCENTAGE', 'LATE_FEE', 'FORMULA']) {
    assert.match(
      source,
      new RegExp(`value:\\s*['\"\`]${disabledType}['\"\`][^}]*disabled:\\s*true`),
      `${disabledType} must be visibly disabled`
    )
  }
  assert.doesNotMatch(source, /form\.(?:tenantId|contractId|amount|generateDay)/)
})

test('billing-plan has a minimal real page backed by V2 paging, summary and explicit DTO fields', () => {
  const pageSource = readSource('src/views/tenant/BillingPlanList.vue')
  const configSource = readSource('src/workspaceConfigs/leasingWorkspaceConfig.js')
  const source = [pageSource, configSource].join('\n')

  assert.notEqual(source, '', 'BillingPlanList.vue must exist')
  assert.match(source, /request\.get\(\s*['"`]\/billing-plans\/search['"`]/)
  assert.match(source, /request\.get\(\s*['"`]\/billing-plans\/summary['"`]/)
  assert.match(source, /request\.post\(\s*['"`]\/billing-plans['"`]/)
  assert.match(source, /createLatestRequestGuard\(\)/)
  assert.match(source, /readPage\(data\)/)
  assert.match(source, /plans\.value\s*=\s*\[\]/)
  assert.match(source, /pagination\.total\s*=\s*0/)
  assert.match(configSource, /key:\s*['"`]itemCount['"`]/, 'workspace fields must use the server-side item aggregate')
  assert.doesNotMatch(pageSource, /row\.items\.length/, 'list rows must not infer item totals from omitted detail data')

  for (const field of [
    'tenantId',
    'contractId',
    'leaseId',
    'effectiveFrom',
    'effectiveTo',
    'billingCycle',
    'billingLeadDays',
    'dueRule',
    'currency'
  ]) {
    assert.match(source, new RegExp(`form\\.${field}`), `missing billing plan field ${field}`)
  }
  for (const itemField of ['feeRuleVersionId', 'itemName', 'quantity', 'unitPrice', 'fixedAmount', 'sortOrder']) {
    assert.match(source, new RegExp(`item\\.${itemField}`), `missing billing plan item field ${itemField}`)
  }
})

test('legacy fee-rule and billing-plan routes redirect while retaining dedicated API permission codes', () => {
  const routerSource = readSource('src/router/index.js')
  const accessSource = readSource('src/config/access.js')

  assert.match(routerSource, /path:\s*['"`]\/fee-rules['"`][\s\S]{0,260}redirect:[\s\S]{0,260}path:\s*['"`]\/bills['"`]/)
  assert.match(routerSource, /path:\s*['"`]\/billing-plans['"`][\s\S]{0,260}redirect:[\s\S]{0,260}path:\s*['"`]\/bills['"`]/)
  assert.doesNotMatch(accessSource, /title:\s*['"`]收费规则['"`][^\n]*path:\s*['"`]\/fee-rules['"`]/)
  assert.doesNotMatch(accessSource, /title:\s*['"`]计费计划['"`][^\n]*path:\s*['"`]\/billing-plans['"`]/)

  assert.deepEqual(getPathPermissionCodes('/fee-rules'), [
    'fee-rule:view',
    'fee-rule:create',
    'fee-rule:update',
    'fee-rule:activate'
  ])
  assert.deepEqual(getPathViewPermissionCodes('/fee-rules'), ['fee-rule:view'])
  assert.deepEqual(getPathPermissionCodes('/billing-plans'), [
    'billing-plan:view',
    'billing-plan:create',
    'billing-plan:update',
    'billing-plan:terminate'
  ])
  assert.deepEqual(getPathViewPermissionCodes('/billing-plans'), ['billing-plan:view'])
})

test('phase-two action buttons and permission labels use real granular codes', () => {
  const feeRuleSource = readSource('src/views/tenant/FeeRuleList.vue')
  const billingPlanSource = readSource('src/views/tenant/BillingPlanList.vue')
  const workspaceSource = readSource('src/workspaceConfigs/leasingWorkspaceConfig.js')
  const labelSource = readSource('src/config/permissionLabels.js')

  for (const [key, code] of Object.entries({
    create: 'fee-rule:create',
    update: 'fee-rule:update',
    activate: 'fee-rule:activate'
  })) {
    assert.match(feeRuleSource, new RegExp(`hasPermission\\(['\"\`]${code}['\"\`]\\)`))
    assert.ok(workspaceSource.includes(`${key}: '${code}'`), `missing workspace permission for ${code}`)
    assert.ok(labelSource.includes(`'${code}'`), `missing label for ${code}`)
  }
  for (const [key, code] of Object.entries({
    create: 'billing-plan:create',
    update: 'billing-plan:update',
    terminate: 'billing-plan:terminate'
  })) {
    assert.match(billingPlanSource, new RegExp(`hasPermission\\(['\"\`]${code}['\"\`]\\)`))
    assert.ok(workspaceSource.includes(`${key}: '${code}'`), `missing workspace permission for ${code}`)
    assert.ok(labelSource.includes(`'${code}'`), `missing label for ${code}`)
  }
})
