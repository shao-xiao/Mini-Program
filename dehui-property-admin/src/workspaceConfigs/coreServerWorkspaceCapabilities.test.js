import assert from 'node:assert/strict'
import test from 'node:test'
import { workspaceFieldCapability } from '../utils/workspaceQueryPlan.js'
import { createEnergyMeterWorkspaceConfig, createEnergyReadingWorkspaceConfig } from './energyWorkspaceConfig.js'
import { createEquipmentWorkspaceConfig } from './equipment.js'
import { createWorkOrderWorkspaceConfig } from './workorder.js'

const cases = [
  ['设备台账', createEquipmentWorkspaceConfig(), ['equipmentType', 'status']],
  ['工单管理', createWorkOrderWorkspaceConfig(), ['category', 'status', 'priority']],
  ['表具管理', createEnergyMeterWorkspaceConfig(), ['meterType', 'buildingName', 'status']],
  ['抄表管理', createEnergyReadingWorkspaceConfig(), ['meterType', 'periodMonth', 'billStatus']]
]

test('core server workspaces expose real server filter, sort and grouping capabilities', () => {
  for (const [label, config, meaningfulGroups] of cases) {
    assert.equal(config.query.dataMode, 'server', `${label} must remain server paged`)
    const enabled = capability => config.fields.filter(field => (
      workspaceFieldCapability(field, capability, config.query.dataMode).mode === 'server'
    ))

    assert.ok(enabled('filter').length > 0, `${label} requires a server filter`)
    assert.ok(enabled('sort').length > 0, `${label} requires a server sort`)
    assert.ok(enabled('group').length > 0, `${label} requires a server group`)
    assert.ok(
      enabled('group').some(field => meaningfulGroups.includes(field.key)),
      `${label} requires a meaningful grouping field`
    )
  }
})

test('server ordering declarations serialize through whitelisted public field keys', () => {
  for (const [label, config] of cases) {
    for (const capability of ['sort', 'group']) {
      for (const field of config.fields) {
        const declaration = workspaceFieldCapability(field, capability, 'server')
        if (declaration.mode !== 'server') continue
        const expected = field.custom ? `custom.${field.fieldCode || field.key}` : field.key
        assert.equal(declaration.param, expected, `${label} ${field.key} ${capability} must use its safe public field key`)
      }
    }
  }
})
