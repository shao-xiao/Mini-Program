import assert from 'node:assert/strict'
import test from 'node:test'
import { fieldTypeOptions } from '../src/config/fieldTypes.js'
import { workspaceCapabilityBlockers, workspaceRoutedTables } from '../src/config/tableWorkspaceLegacyAllowlist.js'
import { workspaceFieldCapability } from '../src/utils/workspaceQueryPlan.js'
import { roomWorkspaceConfig } from '../src/workspaceConfigs/buildingWorkspaceConfig.js'
import { createEnergyMeterWorkspaceConfig, createEnergyReadingWorkspaceConfig } from '../src/workspaceConfigs/energyWorkspaceConfig.js'
import { createEquipmentWorkspaceConfig } from '../src/workspaceConfigs/equipment.js'
import { createBillWorkspaceConfig } from '../src/workspaceConfigs/financeWorkspaceConfig.js'
import {
  investmentContentWorkspaceConfig,
  investmentLeadWorkspaceConfig,
  investmentListingWorkspaceConfig
} from '../src/workspaceConfigs/investmentWorkspaceConfig.js'
import {
  announcementWorkspaceConfig,
  inspectionWorkspaceConfig,
  visitorWorkspaceConfig
} from '../src/workspaceConfigs/operationWorkspaceConfig.js'
import {
  meetingBookingWorkspaceConfig,
  parkingAssignmentWorkspaceConfig,
  parkingSpaceWorkspaceConfig
} from '../src/workspaceConfigs/parkingMeetingWorkspaceConfig.js'
import {
  createMobileUserWorkspaceConfig,
  createSystemRoleWorkspaceConfig,
  createSystemUserWorkspaceConfig
} from '../src/workspaceConfigs/systemWorkspaceConfig.js'
import { createWorkOrderWorkspaceConfig } from '../src/workspaceConfigs/workorder.js'

const configs = [
  roomWorkspaceConfig,
  createEquipmentWorkspaceConfig(),
  investmentLeadWorkspaceConfig,
  investmentContentWorkspaceConfig,
  investmentListingWorkspaceConfig,
  createBillWorkspaceConfig(),
  parkingSpaceWorkspaceConfig,
  parkingAssignmentWorkspaceConfig,
  meetingBookingWorkspaceConfig,
  createWorkOrderWorkspaceConfig(),
  visitorWorkspaceConfig,
  inspectionWorkspaceConfig,
  announcementWorkspaceConfig,
  createEnergyMeterWorkspaceConfig(),
  createEnergyReadingWorkspaceConfig(),
  createSystemUserWorkspaceConfig(),
  createMobileUserWorkspaceConfig(),
  createSystemRoleWorkspaceConfig()
]

const configByPageKey = new Map(configs.map(config => [config.pageKey, config]))
const blockers = new Map(workspaceCapabilityBlockers.map(item => [item.route, item]))
const validTypes = new Set(fieldTypeOptions().map(item => item.value))
const validDensities = new Set(['compact', 'standard', 'comfortable', 'spacious'])
const meaninglessGroupKeys = new Set(['id', 'remark', 'description', 'attachment', 'attachments', 'operations'])

test('workspace configs cover every routed formal table with unique identities', () => {
  assert.equal(configs.length, workspaceRoutedTables.length)
  assert.deepEqual(
    [...configByPageKey.keys()].sort(),
    workspaceRoutedTables.map(item => item.pageKey).sort()
  )
  assert.equal(new Set(configs.map(config => config.id)).size, configs.length, 'workspace ids must be unique')
  assert.equal(new Set(configs.map(config => config.storageKey)).size, configs.length, 'workspace storage keys must be unique')
})

test('every workspace config has valid fields, view permission and display preferences', () => {
  for (const config of configs) {
    assert.ok(config.fields.length > 0, `${config.pageKey} requires fields`)
    assert.equal(new Set(config.fields.map(field => field.key)).size, config.fields.length, `${config.pageKey} field keys must be unique`)
    assert.ok(config.fields.some(field => !field.filterOnly), `${config.pageKey} requires a visible data field`)
    assert.match(config.permissions.view || '', /^[a-z][a-z0-9-]*(?::[a-z*][a-z0-9-]*)+$/, `${config.pageKey} requires a permission code`)
    assert.ok(['client', 'server'].includes(config.query.dataMode), `${config.pageKey} requires a real data mode`)
    assert.ok(validDensities.has(config.table.defaultPrefs.rowDensity), `${config.pageKey} requires a standard row density`)

    for (const field of config.fields) {
      assert.ok(field.key && field.label, `${config.pageKey} fields require key and label`)
      if (field.type) assert.ok(validTypes.has(field.type), `${config.pageKey}.${field.key} has invalid type ${field.type}`)
    }
  }
})

test('workspace query capabilities are real or explicitly blocked without current-page fallback', () => {
  for (const config of configs) {
    const mode = config.query.dataMode
    const enabled = capability => config.fields.filter(field => (
      workspaceFieldCapability(field, capability, mode).mode === mode
    ))
    const blocker = blockers.get(config.pageKey)

    assert.ok(enabled('filter').length > 0, `${config.pageKey} requires a ${mode} filter`)
    for (const capability of ['sort', 'group']) {
      if (blocker?.capabilities.includes(capability)) {
        assert.equal(enabled(capability).length, 0, `${config.pageKey} blocker is stale for ${capability}`)
        continue
      }
      assert.ok(enabled(capability).length > 0, `${config.pageKey} requires a ${mode} ${capability}`)
    }

    const meaningfulGroups = enabled('group').filter(field => !meaninglessGroupKeys.has(field.key))
    if (!blocker?.capabilities.includes('group')) {
      assert.ok(meaningfulGroups.length > 0, `${config.pageKey} requires a meaningful group field`)
    }

    if (mode === 'server') {
      for (const capability of ['filter', 'sort', 'group']) {
        for (const field of enabled(capability)) {
          assert.equal(
            workspaceFieldCapability(field, capability, mode).mode,
            'server',
            `${config.pageKey}.${field.key} must not use a page-local ${capability}`
          )
        }
      }
    }
  }
})

test('every capability blocker is unique, actionable and still unresolved', () => {
  assert.equal(blockers.size, workspaceCapabilityBlockers.length)
  for (const blocker of workspaceCapabilityBlockers) {
    assert.ok(configByPageKey.has(blocker.route), `${blocker.route} must be a formal workspace page key`)
    assert.ok(blocker.capabilities.length > 0, `${blocker.route} requires blocked capabilities`)
    assert.ok(blocker.reason.trim(), `${blocker.route} requires a reason`)
    assert.ok(blocker.followUp.trim(), `${blocker.route} requires a follow-up`)
  }
})

test('room workspace has no remaining query capability blocker', () => {
  assert.equal(blockers.has('/rooms'), false)
})
