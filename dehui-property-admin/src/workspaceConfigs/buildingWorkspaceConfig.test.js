import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  buildingWorkspaceConfig,
  floorWorkspaceConfig,
  roomContractStageOptions,
  roomStatusOptions,
  roomWorkspaceConfig
} from './buildingWorkspaceConfig.js'
import { workspaceTableOptions } from './workspaceConfig.js'

test('building and floor workspaces use complete client datasets and existing permissions', () => {
  assert.equal(buildingWorkspaceConfig.query.dataMode, 'client')
  assert.equal(floorWorkspaceConfig.query.dataMode, 'client')
  assert.deepEqual(Object.values(buildingWorkspaceConfig.permissions), [
    'building:view',
    'building:create',
    'building:update',
    'building:delete'
  ])
  assert.deepEqual(Object.values(floorWorkspaceConfig.permissions), [
    'floor:view',
    'floor:create',
    'floor:update',
    'floor:delete'
  ])
  assert.deepEqual(floorWorkspaceConfig.dataActions.map(action => action.id), ['batch-generate'])
  assert.deepEqual(floorWorkspaceConfig.batchActions, [])
  assert.deepEqual(buildingWorkspaceConfig.metrics, [])
  assert.deepEqual(floorWorkspaceConfig.metrics, [])
})

test('building and floor fields stay aligned with their real list responses', () => {
  assert.deepEqual(buildingWorkspaceConfig.fields.map(field => field.key), [
    'buildingCode',
    'buildingName',
    'address',
    'totalFloors',
    'status',
    'description',
    'createdAt',
    'updatedAt'
  ])
  assert.deepEqual(floorWorkspaceConfig.fields.map(field => field.key), [
    'floorCode',
    'buildingId',
    'floorNumber',
    'floorName',
    'totalArea',
    'status',
    'description'
  ])
  assert.equal(floorWorkspaceConfig.table.defaultPrefs.visibleColumns.includes('buildingId'), false)
})

test('room workspace preserves server pagination and declares whitelisted server ordering', () => {
  const plan = buildWorkspaceQueryPlan({
    fields: roomWorkspaceConfig.fields,
    filters: [{ key: 'status', operator: 'equals', value: 'VACANT' }],
    sorts: [{ key: 'roomNumber', order: 'ascending' }],
    groups: [{ key: 'floorName', order: 'ascending' }],
    query: roomWorkspaceConfig.query
  })

  assert.equal(roomWorkspaceConfig.query.dataMode, 'server')
  assert.deepEqual(plan.serverParams, {
    groupBy: 'floorName:asc',
    sorts: 'floorName:asc,roomNumber:asc',
    status: 'VACANT'
  })
  assert.equal(plan.serverFilters.length, 1)
  assert.equal(plan.serverSorts.length, 1)
  assert.equal(plan.serverGroups.length, 1)
  assert.equal(plan.unsupportedSorts.length, 0)
  assert.equal(plan.unsupportedGroups.length, 0)
  assert.equal(plan.pageFilters.length, 0)
  assert.equal(plan.clientFilters.length, 0)
})

test('room workspace owns the real room fields, metrics and permission codes', () => {
  const options = workspaceTableOptions(roomWorkspaceConfig, {
    records: () => [],
    serverTotal: () => 0
  })

  assert.deepEqual(roomWorkspaceConfig.fields.map(field => field.key), [
    'roomCode',
    'buildingName',
    'floorName',
    'roomNumber',
    'roomName',
    'area',
    'propertyFeeArea',
    'roomType',
    'status',
    'contractStage',
    'description'
  ])
  assert.deepEqual(Object.values(roomWorkspaceConfig.permissions), [
    'room:view',
    'room:create',
    'room:update',
    'room:delete',
    'room:lease',
    'room:checkout'
  ])
  assert.deepEqual(roomWorkspaceConfig.metrics.map(metric => metric.field), [
    'total',
    'filtered',
    'available',
    'rented',
    'unavailable'
  ])
  assert.equal(roomWorkspaceConfig.subtitle, '占用状态以入住/退租为准，合同阶段同步审批流程')
  assert.ok(roomStatusOptions.some(option => option.value === 'VACANT'))
  assert.equal(roomStatusOptions.some(option => option.value === 'AVAILABLE'), false)
  assert.deepEqual(roomContractStageOptions.map(option => option.value), [
    'ACTIVE_CURRENT',
    'ACTIVE_FUTURE',
    'SUSPENDED',
    'PENDING_APPROVAL',
    'DRAFT',
    'NONE'
  ])
  assert.equal(roomWorkspaceConfig.fields.find(field => field.key === 'contractStage')?.required, true)
  assert.deepEqual(
    roomWorkspaceConfig.metrics.slice(2, 4).map(metric => metric.title),
    ['未入住', '已入住']
  )
  assert.equal(options.columns, roomWorkspaceConfig.fields)
  assert.equal(options.query.dataMode, 'server')
})
