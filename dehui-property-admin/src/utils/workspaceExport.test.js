import test from 'node:test'
import assert from 'node:assert/strict'
import { loadAllWorkspaceRecords } from './workspaceExport.js'

test('loads every server page for the same query without truncating the export', async () => {
  const calls = []
  const records = await loadAllWorkspaceRecords(({ page, pageSize }) => {
    calls.push({ page, pageSize })
    const start = (page - 1) * pageSize
    return Promise.resolve({
      records: Array.from({ length: Math.min(pageSize, 205 - start) }, (_, index) => ({ id: start + index + 1 })),
      total: 205,
      page,
      pageSize
    })
  })

  assert.equal(records.length, 205)
  assert.deepEqual(calls, [
    { page: 1, pageSize: 100 },
    { page: 2, pageSize: 100 },
    { page: 3, pageSize: 100 }
  ])
})

test('rejects oversized exports before requesting later pages', async () => {
  let calls = 0
  await assert.rejects(() => loadAllWorkspaceRecords(() => {
    calls += 1
    return Promise.resolve({ records: [{ id: 1 }], total: 10001, page: 1, pageSize: 100 })
  }), /超过单次导出上限/)
  assert.equal(calls, 1)
})
