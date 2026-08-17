import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  CUSTOM_FIELD_WORKSPACE_REGISTRATIONS,
  customFieldWorkspaceRegistration
} from './customFieldWorkspaceRegistry.js'
import { buildingWorkspaceConfig, floorWorkspaceConfig, roomWorkspaceConfig } from './buildingWorkspaceConfig.js'
import { contractWorkspaceConfig } from './contractWorkspaceConfig.js'
import { createEquipmentWorkspaceConfig } from './equipment.js'
import { createEnergyMeterWorkspaceConfig, createEnergyReadingWorkspaceConfig } from './energyWorkspaceConfig.js'
import { createBillWorkspaceConfig } from './financeWorkspaceConfig.js'
import {
  investmentContentWorkspaceConfig,
  investmentLeadWorkspaceConfig,
  investmentListingWorkspaceConfig
} from './investmentWorkspaceConfig.js'
import { createBillingPlanWorkspaceConfig, createLeaseWorkspaceConfig, feeRuleWorkspaceConfig } from './leasingWorkspaceConfig.js'
import { announcementWorkspaceConfig, inspectionWorkspaceConfig, visitorWorkspaceConfig } from './operationWorkspaceConfig.js'
import { parkingBillWorkspaceConfig } from './parkingBillWorkspaceConfig.js'
import {
  meetingBookingWorkspaceConfig,
  meetingRoomWorkspaceConfig,
  parkingAssignmentWorkspaceConfig,
  parkingSpaceWorkspaceConfig
} from './parkingMeetingWorkspaceConfig.js'
import { createMobileUserWorkspaceConfig, createSystemRoleWorkspaceConfig, createSystemUserWorkspaceConfig } from './systemWorkspaceConfig.js'
import { tenantWorkspaceConfig } from './tenantWorkspaceConfig.js'
import { createWorkOrderWorkspaceConfig } from './workorder.js'

const formalConfigs = [
  buildingWorkspaceConfig,
  floorWorkspaceConfig,
  roomWorkspaceConfig,
  createEquipmentWorkspaceConfig(),
  tenantWorkspaceConfig,
  createLeaseWorkspaceConfig(),
  feeRuleWorkspaceConfig,
  createBillingPlanWorkspaceConfig(),
  investmentLeadWorkspaceConfig,
  investmentContentWorkspaceConfig,
  investmentListingWorkspaceConfig,
  contractWorkspaceConfig,
  createBillWorkspaceConfig(),
  parkingSpaceWorkspaceConfig,
  parkingAssignmentWorkspaceConfig,
  parkingBillWorkspaceConfig,
  meetingRoomWorkspaceConfig,
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

test('frontend custom-field registry mirrors all 27 backend workspaces', () => {
  assert.equal(CUSTOM_FIELD_WORKSPACE_REGISTRATIONS.length, 27)
  assert.equal(new Set(CUSTOM_FIELD_WORKSPACE_REGISTRATIONS.map(item => item.configId)).size, 27)
  assert.equal(new Set(CUSTOM_FIELD_WORKSPACE_REGISTRATIONS.map(item => item.workspaceKey)).size, 27)
  CUSTOM_FIELD_WORKSPACE_REGISTRATIONS.forEach(item => {
    assert.match(item.workspaceKey, /^[a-z][a-z0-9-]*$/)
    assert.match(item.viewPermission, /^[a-z0-9-]+(?::[a-z0-9-]+)+$/)
    assert.match(item.valueUpdatePermission, /^[a-z0-9-]+(?::[a-z0-9-]+)+$/)
    assert.ok(['client', 'server'].includes(item.queryMode))
  })
})

test('transitional config ids resolve to stable backend workspace keys', () => {
  assert.equal(customFieldWorkspaceRegistration('finance-bills').workspaceKey, 'bills')
  assert.equal(customFieldWorkspaceRegistration('energy-readings').workspaceKey, 'energy-records')
  assert.equal(customFieldWorkspaceRegistration('system-mobile-users').workspaceKey, 'mobile-users')
})

test('registry permissions preserve existing module update boundaries', () => {
  assert.equal(customFieldWorkspaceRegistration('equipment').valueUpdatePermission, 'asset:update')
  assert.equal(customFieldWorkspaceRegistration('meeting-bookings').valueUpdatePermission, 'meeting-booking:confirm')
  assert.equal(customFieldWorkspaceRegistration('investment-leads').valueUpdatePermission, 'investment-lead:follow')
})

test('all formal workspace configs enable the registered platform contract', () => {
  assert.equal(formalConfigs.length, 27)
  assert.equal(new Set(formalConfigs.map(config => config.id)).size, 27)
  formalConfigs.forEach(config => {
    const registration = customFieldWorkspaceRegistration(config.id)
    assert.ok(registration, `${config.id} must be registered`)
    assert.equal(config.customFields.enabled, true)
    assert.equal(config.customFields.workspaceKey, registration.workspaceKey)
    assert.equal(config.customFields.entityType, registration.entityType)
    assert.equal(config.customFields.viewPermission, registration.viewPermission)
    assert.equal(config.customFields.valueUpdatePermission, registration.valueUpdatePermission)
    assert.equal(config.customFields.queryMode, config.query.dataMode)
    assert.equal(config.customFields.valueSource, config.query.dataMode === 'server' ? 'projected' : 'batch')
  })
})

test('frontend registrations mirror the backend registry contract exactly', () => {
  const testDirectory = path.dirname(fileURLToPath(import.meta.url))
  const backendRegistryPath = path.resolve(
    testDirectory,
    '../../../dehui-property-management/src/main/java/com/dehui/property/modules/customfield/application/CustomFieldWorkspaceRegistry.java'
  )
  const backendSource = fs.readFileSync(backendRegistryPath, 'utf8')
  const backendEntries = [...backendSource.matchAll(
    /entry\("([^"]+)",\s*"([^"]+)",\s*"[^"]+",\s*"([^"]+)",\s*"([^"]+)",\s*QueryMode\.(CLIENT|SERVER)\)/g
  )].map(match => ({
    workspaceKey: match[1],
    entityType: match[2],
    viewPermission: match[3],
    valueUpdatePermission: match[4],
    queryMode: match[5].toLowerCase()
  }))
  const frontendEntries = CUSTOM_FIELD_WORKSPACE_REGISTRATIONS.map(item => ({
    workspaceKey: item.workspaceKey,
    entityType: item.entityType,
    viewPermission: item.viewPermission,
    valueUpdatePermission: item.valueUpdatePermission,
    queryMode: item.queryMode
  }))

  assert.deepEqual(
    backendEntries.sort((left, right) => left.workspaceKey.localeCompare(right.workspaceKey)),
    frontendEntries.sort((left, right) => left.workspaceKey.localeCompare(right.workspaceKey))
  )
})
