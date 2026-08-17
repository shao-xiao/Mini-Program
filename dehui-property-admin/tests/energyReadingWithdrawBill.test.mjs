import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('energy reading page withdraws generated bills through backend API', () => {
  const api = fs.readFileSync(path.join(root, 'src/api/energy.js'), 'utf8')
  const recordPage = fs.readFileSync(path.join(root, 'src/views/energy/EnergyRecordList.vue'), 'utf8')

  assert.match(api, /withdrawEnergyBill/)
  assert.match(api, /\/energy\/readings\/\$\{id\}\/withdraw-bill/)

  assert.match(recordPage, /withdrawEnergyBill/)
  assert.match(recordPage, /async function withdrawBill\(row\)/)
  assert.match(recordPage, /撤回后，表号 .* 账单会作废/)
  assert.match(recordPage, /withdrawEnergyBill\(row\.id/)
  assert.match(recordPage, /await loadRecords\(\)/)
  assert.match(recordPage, /row\.billStatus === 'GENERATED'[^]*撤回账单/)
})
