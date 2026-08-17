import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createEnergyMeterWorkspaceConfig,
  createEnergyReadingWorkspaceConfig
} from './energyWorkspaceConfig.js'

test('energy meter config only exposes fields returned by the meter API', () => {
  const config = createEnergyMeterWorkspaceConfig()
  const fieldKeys = config.fields.map(field => field.key)

  assert.equal(config.pageKey, '/energy/meters')
  assert.equal(config.permissions.view, 'energy:meter:view')
  assert.ok(fieldKeys.includes('meterNo'))
  assert.ok(fieldKeys.includes('updatedAt'))
  assert.equal(fieldKeys.includes('currentReading'), false)
  assert.equal(fieldKeys.includes('usageAmount'), false)
  assert.equal(config.query.dataMode, 'server')
  assert.deepEqual(config.views.map(view => view.id), ['disabled-meters'])
  assert.ok(config.views.every(view => !view.prefs?.sorts?.length))
})

test('energy reading config owns real reading, anomaly, amount and operator fields', () => {
  const config = createEnergyReadingWorkspaceConfig()
  const fieldKeys = config.fields.map(field => field.key)

  assert.equal(config.pageKey, '/energy/records')
  assert.equal(config.permissions.update, 'energy:reading:update')
  assert.equal(config.permissions.delete, 'energy:reading:delete')
  for (const field of ['readingDate', 'meter', 'previousReading', 'currentReading', 'usageAmount', 'operatorName', 'anomalyState', 'settlementAmount']) {
    assert.ok(fieldKeys.includes(field), `missing energy reading field ${field}`)
  }
  assert.deepEqual(
    config.views.map(view => view.id),
    ['abnormal-readings']
  )
  assert.equal(config.query.dataMode, 'server')
  assert.ok(config.views.every(view => !view.prefs?.sorts?.length))
  assert.equal(config.metrics.some(metric => metric.field === 'trend'), false)
})
