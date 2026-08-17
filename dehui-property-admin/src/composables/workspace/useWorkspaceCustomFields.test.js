import assert from 'node:assert/strict'
import test from 'node:test'

import { createWorkspaceCustomFieldRepository } from './useWorkspaceCustomFields.js'

function definition(id, status = 'ENABLED') {
  return {
    id,
    fieldCode: `custom_${id}`,
    fieldName: `字段${id}`,
    fieldType: 'TEXT',
    status
  }
}

test('custom field repository deduplicates concurrent loads and separates user scopes', async () => {
  let calls = 0
  const gate = Promise.withResolvers()
  const repository = createWorkspaceCustomFieldRepository({
    list: async () => {
      calls += 1
      await gate.promise
      return [definition(1)]
    }
  })

  const tech = repository.scope('equipment', 'tech')
  const admin = repository.scope('equipment', 'admin')
  const first = tech.load()
  const second = tech.load()
  gate.resolve()
  await Promise.all([first, second])

  assert.equal(calls, 1)
  assert.deepEqual(tech.definitions.value.map(item => item.fieldCode), ['custom_1'])
  assert.deepEqual(admin.definitions.value, [])
})

test('custom field repository upgrades an active-only in-flight load for field managers', async () => {
  const gates = [Promise.withResolvers(), Promise.withResolvers()]
  const includeDisabledCalls = []
  const repository = createWorkspaceCustomFieldRepository({
    list: async (_workspaceKey, options) => {
      const callIndex = includeDisabledCalls.length
      includeDisabledCalls.push(options.includeDisabled)
      await gates[callIndex].promise
      return options.includeDisabled
        ? [definition(1), definition(2, 'DISABLED')]
        : [definition(1)]
    }
  })
  const scope = repository.scope('equipment', 'admin')

  const activeLoad = scope.load({ includeDisabled: false })
  const managerLoad = scope.load({ includeDisabled: true })
  gates[0].resolve()
  await activeLoad
  gates[1].resolve()
  await managerLoad

  assert.deepEqual(includeDisabledCalls, [false, true])
  assert.deepEqual(scope.definitions.value.map(item => item.status), ['ENABLED', 'DISABLED'])
})

test('custom field repository exposes active definitions and keeps failed refresh explicit', async () => {
  let shouldFail = false
  const repository = createWorkspaceCustomFieldRepository({
    list: async () => {
      if (shouldFail) throw new Error('network failed')
      return [definition(1), definition(2, 'DISABLED')]
    }
  })
  const scope = repository.scope('equipment', 'tech')

  await scope.load({ includeDisabled: true })
  assert.deepEqual(scope.activeDefinitions.value.map(item => item.fieldCode), ['custom_1'])

  shouldFail = true
  await assert.rejects(scope.load({ force: true }), /network failed/)
  assert.equal(scope.error.value, 'network failed')
  assert.deepEqual(scope.definitions.value, [])
})

test('custom field repository refreshes definitions after create, update and status changes', async () => {
  let rows = [definition(1)]
  const calls = []
  const repository = createWorkspaceCustomFieldRepository({
    list: async () => rows,
    create: async (_workspaceKey, payload) => {
      calls.push(['create', payload])
      rows = [...rows, { ...definition(2), fieldName: payload.fieldName }]
      return rows.at(-1)
    },
    update: async (_workspaceKey, id, payload) => {
      calls.push(['update', id, payload])
      rows = rows.map(item => item.id === id ? { ...item, fieldName: payload.fieldName } : item)
      return rows.find(item => item.id === id)
    },
    changeStatus: async (_workspaceKey, id, status) => {
      calls.push(['status', id, status])
      rows = rows.map(item => item.id === id ? { ...item, status } : item)
      return rows.find(item => item.id === id)
    },
    remove: async (_workspaceKey, id) => {
      calls.push(['remove', id])
      rows = rows.filter(item => item.id !== id)
    },
    reorder: async (_workspaceKey, order) => calls.push(['reorder', order])
  })
  const scope = repository.scope('equipment', 'tech')

  await scope.load({ includeDisabled: true })
  await scope.create({ fieldName: '新增字段' })
  await scope.update(2, { fieldName: '重命名字段' })
  await scope.changeStatus(2, 'DISABLED')
  await scope.reorder([{ fieldId: 2, displayOrder: 0 }])
  await scope.remove(2)

  assert.deepEqual(scope.definitions.value.map(item => item.fieldCode), ['custom_1'])
  assert.deepEqual(calls.map(item => item[0]), ['create', 'update', 'status', 'reorder', 'remove'])
})
