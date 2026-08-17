import assert from 'node:assert/strict'
import test from 'node:test'

import {
  appendUniqueWorkspaceCondition,
  availableWorkspaceFields,
  moveWorkspaceCondition,
  resolveWorkspaceQueryCapability
} from './workspaceToolbar.js'

const fields = [
  {
    key: 'status',
    label: '状态',
    query: {
      filter: { mode: 'server', param: 'status', operators: ['equals'] },
      sort: { mode: 'server', param: 'sort' },
      group: { mode: 'page' }
    }
  },
  {
    key: 'owner',
    label: '负责人',
    query: {
      filter: { mode: 'page' },
      sort: { mode: 'client' },
      group: { mode: 'client' }
    }
  },
  {
    key: 'secret',
    label: '内部字段',
    query: {
      filter: { mode: 'disabled', reason: '后端不支持' },
      sort: { mode: 'disabled' },
      group: { mode: 'disabled' }
    }
  }
]

test('workspace toolbar resolves query capability and its visible scope', () => {
  assert.deepEqual(resolveWorkspaceQueryCapability(fields[0], 'filter', 'server'), {
    mode: 'server',
    label: '全量',
    reason: '',
    operators: ['equals']
  })
  assert.equal(resolveWorkspaceQueryCapability(fields[1], 'filter', 'server').label, '仅当前页')
  assert.equal(resolveWorkspaceQueryCapability(fields[2], 'filter', 'server').reason, '后端不支持')
})

test('disabled workspace fields are not offered by builders', () => {
  assert.deepEqual(
    availableWorkspaceFields(fields, 'filter', 'server').map(field => field.key),
    ['status', 'owner']
  )
})

test('sort and group builders append only unused fields', () => {
  const existing = [{ key: 'status', order: 'ascending' }]
  const next = appendUniqueWorkspaceCondition(existing, fields, {
    capability: 'sort',
    dataMode: 'server',
    defaults: { order: 'ascending' }
  })

  assert.deepEqual(existing, [{ key: 'status', order: 'ascending' }])
  assert.deepEqual(next, [
    { key: 'status', order: 'ascending' },
    { key: 'owner', order: 'ascending' }
  ])
})

test('workspace conditions move without mutating the source list', () => {
  const source = [
    { key: 'status', order: 'ascending' },
    { key: 'owner', order: 'descending' }
  ]
  const moved = moveWorkspaceCondition(source, 1, -1)

  assert.deepEqual(source.map(item => item.key), ['status', 'owner'])
  assert.deepEqual(moved.map(item => item.key), ['owner', 'status'])
  assert.notEqual(moved, source)
})
