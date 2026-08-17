import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = relativePath => fs.readFileSync(path.join(root, relativePath), 'utf8')

const pages = [
  ['设备台账', 'src/views/building/EquipmentList.vue'],
  ['工单管理', 'src/views/operation/WorkOrderList.vue'],
  ['表具管理', 'src/views/energy/EnergyMeterList.vue'],
  ['抄表管理', 'src/views/energy/EnergyRecordList.vue']
]

test('core server workspace pages forward query-plan ordering to their existing list APIs', () => {
  for (const [label, file] of pages) {
    const source = read(file)
    assert.match(source, /sorts:\s*serverQueryParams\.value\.sorts\s*\|\|\s*undefined|params\.sorts\s*=\s*serverQueryParams\.value\.sorts/, `${label} must forward sorts`)
    assert.match(source, /groupBy:\s*serverQueryParams\.value\.groupBy\s*\|\|\s*undefined|params\.groupBy\s*=\s*serverQueryParams\.value\.groupBy/, `${label} must forward groupBy`)
  }
})

test('workorder list and summary requests share custom field filters from the query plan', () => {
  const source = read('src/views/operation/WorkOrderList.vue')
  assert.match(
    source,
    /function buildSummaryParams[\s\S]*customFilters:\s*serverQueryParams\.value\.customFilters\s*\|\|\s*undefined/,
    'workorder list and metric requests must share customFilters through buildSummaryParams'
  )
  assert.match(source, /\.\.\.buildSummaryParams\(\)/)
  assert.match(source, /params:\s*buildSummaryParams\(\{ status:/)
})
