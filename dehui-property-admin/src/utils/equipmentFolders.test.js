import assert from 'node:assert/strict'
import test from 'node:test'
import { groupEquipmentRecords, loadEquipmentFolderRecords } from './equipmentFolders.js'

function pageOf({ page, pageSize }, total) {
  const start = (page - 1) * pageSize
  const records = Array.from({ length: Math.min(pageSize, total - start) }, (_, i) => ({ id: start + i + 1 }))
  return { page, pageSize, records, total }
}

test('groups standard labels, codes and case variants, preserving custom types and visible row order', () => {
  const rows = ['BMS', ' hvAc ', null, '空调', 'bms', 'OTHER', '会议系统', 'ELEVATOR', '  ', '电梯', '其他']
    .map((equipmentType, i) => ({ id: i + 1, equipmentType, equipmentName: '设备' }))
  const folders = groupEquipmentRecords(rows)
  assert.deepEqual(folders.slice(0, 2).map(folder => [folder.key, folder.count]), [
    ['ELEVATOR', 2], ['HVAC', 2]
  ])
  assert.equal(folders.length, 5)
  assert.deepEqual(folders.map(folder => folder.count), folders.map(folder => folder.rows.length))
  assert.deepEqual(folders[1].rows, [rows[1], rows[3]])
  assert.equal(folders[1].label, '空调')
  const custom = folders.find(folder => folder.key === 'bms')
  assert.equal(custom.label, 'BMS')
  assert.deepEqual(custom.rows, [rows[0], rows[4]])
  assert.equal(folders.find(folder => folder.key === '会议系统').count, 1)
  assert.deepEqual(folders.at(-1), { key: '待确认', label: '待确认', count: 4, rows: [rows[2], rows[5], rows[8], rows[10]] })
})

test('reads beyond the export limit, keeps ordered ids, and bounds in-flight requests', async () => {
  let active = 0
  let peak = 0
  const total = 10121
  const rows = await loadEquipmentFolderRecords(async params => {
    assert.equal(params.pageSize, 100)
    active++
    peak = Math.max(active, peak)
    await new Promise(resolve => setTimeout(resolve, (params.page % 3) + 1))
    active--
    return pageOf(params, total)
  })
  assert(peak <= 3)
  assert.deepEqual(rows.map(row => row.id), Array.from({ length: total }, (_, i) => i + 1))
})

for (const problem of ['duplicate id', 'short page', 'changed total', 'wrong page']) {
  function corrupt(page) {
    if (problem === 'duplicate id') page.records[0].id = '1'
    if (problem === 'short page') page.records.pop()
    if (problem === 'changed total') page.total++
    if (problem === 'wrong page') page.page = 1
  }

  test(`discards the incomplete read and starts from page one after ${problem}`, async () => {
    let starts = 0
    const rows = await loadEquipmentFolderRecords(params => {
      if (params.page === 1) starts++
      const page = pageOf(params, 135)
      if (starts === 1 && params.page === 2) corrupt(page)
      return page
    })
    assert.equal(starts, 2)
    assert.equal(rows.length, 135)
    assert.equal(rows[100].id, 101)
  })
}

test('shows a retryable error after repeated inconsistent pages or malformed data', async () => {
  let starts = 0
  await assert.rejects(loadEquipmentFolderRecords(params => {
    if (params.page === 1) starts++
    const page = pageOf(params, 125)
    if (params.page === 2) page.records = []
    return page
  }), /重新加载/)
  assert.equal(starts, 2)
  await assert.rejects(loadEquipmentFolderRecords(() => ({ records: [] })), /有效的分页数据/)
  assert.deepEqual(await loadEquipmentFolderRecords(() => ({ records: [], total: 0 })), [])
})

test('forwards cancellation and never returns partial data or starts another batch', async () => {
  const controller = new AbortController()
  const calls = []
  await assert.rejects(loadEquipmentFolderRecords(async (params, signal) => {
    assert.equal(signal, controller.signal)
    calls.push(params.page)
    if (params.page === 3) controller.abort()
    return pageOf(params, 800)
  }, controller.signal), { name: 'AbortError' })
  assert(calls.length <= 4)
  assert.equal(calls.filter(page => page === 1).length, 1)
})

test('files imported devices by object, keeps insufficient evidence visible and conserves the total', () => {
  const rows = [
    ['其他', '万能蒸烤箱用工作台'], ['OTHER', '电十盘万能蒸烤箱'], ['其他', '保温饭桶'],
    ['其他', '待核实项目'], ['其他', '特来电'], ['ELEVATOR', '低噪音新风风机'], ['消防', '消防排烟风机']
  ].map(([equipmentType, equipmentName], id) => ({ id, equipmentType, equipmentName }))
  const folders = groupEquipmentRecords(rows)
  const byId = new Map(folders.flatMap(folder => folder.rows.map(row => [row.id, folder.label])))
  assert.deepEqual(rows.map(row => byId.get(row.id)), ['工作台与储物', '热厨与蒸烤', '备餐与保温', '待确认', '充电设施', '通风与净化', '消防'])
  assert.equal(folders.reduce((sum, folder) => sum + folder.count, 0), rows.length)
  assert.equal(rows[0].equipmentType, '其他')
})
