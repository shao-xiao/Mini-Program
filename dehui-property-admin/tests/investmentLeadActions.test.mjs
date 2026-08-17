import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('investment lead actions keep real status updates, detail and delete without fake conversion', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/investment/InvestmentLeadList.vue'), 'utf8')

  assert.doesNotMatch(source, /convertTenant/)
  assert.doesNotMatch(source, /convertContract/)
  assert.doesNotMatch(source, /转租户/)
  assert.doesNotMatch(source, /转合同/)
  assert.match(source, /updateStatus\(row, 'VIEWED'\)/)
  assert.match(source, /updateStatus\(row, 'INVALID'\)/)
  assert.match(source, /openDetail\(row\)/)
  assert.match(source, /updateStatus\(row, 'FOLLOWING'\)/)
  assert.match(source, /招商线索详情/)
  assert.match(source, /deleteLead\(row\)/)
  assert.match(source, /type="danger"[\s\S]{0,120}@click="deleteLead\(row\)"/)
})
