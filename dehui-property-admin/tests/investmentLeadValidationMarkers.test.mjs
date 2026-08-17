import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const root = path.resolve(process.cwd())

test('investment lead list marks historical invalid contact and company values without blocking actions', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/investment/InvestmentLeadList.vue'), 'utf8')

  assert.match(source, /isValidLeadPhone/)
  assert.match(source, /isValidLeadCompany/)
  assert.match(source, /leadFieldText\(row\.phone, 'phone'\)/)
  assert.match(source, /leadFieldText\(row\.companyName, 'company'\)/)
  assert.match(source, /格式异常/)
  assert.match(source, /invalid-lead-field/)
  assert.match(source, /updateStatus\(row, 'FOLLOWING'\)/)
  assert.match(source, /updateStatus\(row, 'VIEWED'\)/)
  assert.match(source, /updateStatus\(row, 'INVALID'\)/)
  assert.match(source, /deleteLead\(row\)/)
})
