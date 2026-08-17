import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

const billPage = read('src/views/tenant/BillList.vue')
const energyStatsPage = read('src/views/energy/EnergyStats.vue')
const meterPage = read('src/views/energy/EnergyMeterList.vue')
const readingPage = read('src/views/energy/EnergyRecordList.vue')

test('finance bill page uses the complete shared workspace stack', () => {
  assert.match(billPage, /<DataWorkspace\b/)
  assert.match(billPage, /<DhicDataToolbar\b/)
  assert.match(billPage, /<WorkspaceDataGrid\b/)
  assert.match(billPage, /\.\.\.serverQueryParams\.value/)
  assert.match(billPage, /\.\.\.pageParams\(pagination\)/)
  assert.match(billPage, /request\.get\('\/bills\/summary'/)
})

test('bill migration retains real CRUD, audit, payment and invoice mutations', () => {
  const requiredEndpoints = [
    /request\.post\('\/bills'/,
    /request\.put\(`\/bills\/\$\{editingBillId\.value\}`/,
    /`\/bills\/\$\{row\.id\}\/approve`/,
    /`\/bills\/\$\{row\.id\}\/reject`/,
    /`\/bills\/\$\{row\.id\}\/pay`/,
    /`\/bills\/\$\{row\.id\}\/invoice`/,
    /`\/bills\/\$\{row\.id\}\/invoice\/download`/,
    /request\.post\(`\/bills\/\$\{row\.id\}\/void`/,
    /request\.delete\(`\/bills\/\$\{row\.id\}\/record`/
  ]
  requiredEndpoints.forEach(pattern => assert.match(billPage, pattern))
  assert.match(billPage, /hasPermission\('bill:add'\)/)
  assert.match(billPage, /hasPermission\(billPermissions\.update\)/)
  assert.match(billPage, /hasPermission\(billPermissions\.void\)/)
  assert.match(billPage, /hasPermission\(billPermissions\.delete\)/)
  assert.match(billPage, /hasPermission\('bill:audit'\)/)
  assert.match(billPage, /hasPermission\('bill:pay'\)/)
  assert.match(billPage, /hasPermission\('finance:invoice:upload'\)/)
  assert.doesNotMatch(billPage, /role\s*===\s*['"]ADMIN['"]/)
})

test('energy anomaly detail uses the shared grid while retaining BI and status mutations', () => {
  assert.match(energyStatsPage, /<WorkspaceDataGrid\b/)
  assert.doesNotMatch(energyStatsPage, /<el-table(?:\s|>)/)
  assert.match(energyStatsPage, /getEnergyStats/)
  assert.match(energyStatsPage, /updateAnomalyStatus/)
  assert.match(energyStatsPage, /hasPermission\('energy:reading:update'\)/)
  assert.match(energyStatsPage, /renderCharts/)
})

test('meter and reading pages remain on the existing DataWorkspace stack', () => {
  for (const source of [meterPage, readingPage]) {
    assert.match(source, /<DataWorkspace\b/)
    assert.match(source, /<DhicDataToolbar\b/)
    assert.match(source, /<WorkspaceDataGrid\b/)
  }
})
