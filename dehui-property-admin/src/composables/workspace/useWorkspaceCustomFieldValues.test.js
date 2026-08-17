import test from 'node:test'
import assert from 'node:assert/strict'
import { ref } from 'vue'
import { useWorkspaceCustomFieldValues } from './useWorkspaceCustomFieldValues.js'

const definitions = [
  { key: 'custom_level', fieldCode: 'custom_level', type: 'select', custom: true, platformCustom: true, options: [{ value: 'A', label: 'A' }] },
  { key: 'custom_budget', fieldCode: 'custom_budget', type: 'money', custom: true, platformCustom: true }
]

test('only registered platform fields enter the shared value mutation path', () => {
  const controller = useWorkspaceCustomFieldValues({
    enabled: true,
    workspaceKey: 'equipment',
    definitions: ref(definitions),
    valueUpdatePermission: 'asset:update',
    permissionCheck: () => true,
    client: { read: async () => ({}), save: async () => {} }
  })

  assert.equal(controller.canEditCell({ id: 1 }, definitions[0]), true)
  assert.equal(controller.canEditCell({ id: 1 }, { key: 'legacy_custom', custom: true, editable: true }), false)
  assert.equal(controller.canEditCell({ id: 1 }, { key: 'unknown', custom: true, platformCustom: true }), false)
})

test('loads one batch for unique entity ids and hydrates records without mutating source rows', async () => {
  const calls = []
  const controller = useWorkspaceCustomFieldValues({
    enabled: true,
    workspaceKey: 'equipment',
    definitions: ref(definitions),
    valueUpdatePermission: 'asset:update',
    permissionCheck: () => true,
    client: {
      read: async (workspaceKey, ids) => {
        calls.push([workspaceKey, ids])
        return { 1: { custom_level: 'A' }, 2: { custom_budget: 12.5 } }
      },
      save: async () => {}
    }
  })
  const source = [{ id: 1, name: 'one' }, { id: 1, name: 'duplicate' }, { id: 2, name: 'two' }]

  await controller.load(source)
  const hydrated = controller.hydrateRows(source)

  assert.deepEqual(calls, [['equipment', [1, 2]]])
  assert.equal(source[0].customFields, undefined)
  assert.deepEqual(hydrated[0].customFields, { custom_level: 'A', custom_budget: '' })
  assert.deepEqual(hydrated[2].customFields, { custom_level: '', custom_budget: 12.5 })
})

test('a failed load clears stale sidecar values and exposes an error', async () => {
  let fail = false
  const controller = useWorkspaceCustomFieldValues({
    enabled: true,
    workspaceKey: 'equipment',
    definitions: ref(definitions),
    valueUpdatePermission: 'asset:update',
    permissionCheck: () => true,
    client: {
      read: async () => {
        if (fail) throw new Error('network failed')
        return { 1: { custom_level: 'A' } }
      },
      save: async () => {}
    }
  })

  await controller.load([{ id: 1 }])
  fail = true
  await assert.rejects(controller.load([{ id: 1 }]), /network failed/)

  assert.deepEqual(controller.valuesByEntity.value, {})
  assert.match(controller.error.value, /network failed/)
})

test('older batch responses cannot overwrite the newest record scope', async () => {
  const resolvers = []
  const controller = useWorkspaceCustomFieldValues({
    enabled: true,
    workspaceKey: 'equipment',
    definitions: ref(definitions),
    valueUpdatePermission: 'asset:update',
    permissionCheck: () => true,
    client: {
      read: (_workspaceKey, ids) => new Promise(resolve => resolvers.push({ ids, resolve })),
      save: async () => {}
    }
  })

  const older = controller.load([{ id: 1 }])
  const newer = controller.load([{ id: 2 }])
  resolvers[1].resolve({ 2: { custom_level: 'A' } })
  await newer
  resolvers[0].resolve({ 1: { custom_budget: 99 } })
  await older

  assert.deepEqual(controller.valuesByEntity.value, { 2: { custom_level: 'A' } })
})

test('cell save sends the complete active field payload and applies values only after success', async () => {
  const calls = []
  const row = { id: 7, customFields: { custom_level: 'A', custom_budget: 10 } }
  const controller = useWorkspaceCustomFieldValues({
    enabled: true,
    workspaceKey: 'equipment',
    definitions: ref(definitions),
    valueUpdatePermission: 'asset:update',
    permissionCheck: code => code === 'asset:update',
    client: {
      read: async () => ({}),
      save: async (workspaceKey, entityId, values) => calls.push({ workspaceKey, entityId, values })
    }
  })

  const pending = controller.saveCell(row, definitions[1], 25.5)
  assert.deepEqual(row.customFields, { custom_level: 'A', custom_budget: 10 })
  const result = await pending

  assert.equal(result.success, true)
  assert.deepEqual(calls, [{
    workspaceKey: 'equipment',
    entityId: 7,
    values: { custom_level: 'A', custom_budget: 25.5 }
  }])
  assert.deepEqual(row.customFields, { custom_level: 'A', custom_budget: 25.5 })
  assert.deepEqual(controller.valuesByEntity.value['7'], row.customFields)
})

test('failed and unauthorized saves leave the record unchanged', async () => {
  const row = { id: 8, customFields: { custom_level: 'A', custom_budget: 10 } }
  const denied = useWorkspaceCustomFieldValues({
    enabled: true,
    workspaceKey: 'equipment',
    definitions: ref(definitions),
    valueUpdatePermission: 'asset:update',
    permissionCheck: () => false,
    client: { read: async () => ({}), save: async () => {} }
  })
  const rejected = await denied.saveCell(row, definitions[1], 20)
  assert.equal(rejected.success, false)
  assert.deepEqual(row.customFields, { custom_level: 'A', custom_budget: 10 })

  const failing = useWorkspaceCustomFieldValues({
    enabled: true,
    workspaceKey: 'equipment',
    definitions: ref(definitions),
    valueUpdatePermission: 'asset:update',
    permissionCheck: () => true,
    client: { read: async () => ({}), save: async () => { throw new Error('save failed') } }
  })
  const failed = await failing.saveCell(row, definitions[1], 20)
  assert.equal(failed.success, false)
  assert.match(failed.message, /save failed/)
  assert.deepEqual(row.customFields, { custom_level: 'A', custom_budget: 10 })
})
