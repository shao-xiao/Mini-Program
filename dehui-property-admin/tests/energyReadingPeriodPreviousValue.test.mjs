import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('reading form refreshes previous value for the selected meter and accounting period', () => {
  const page = fs.readFileSync(path.join(root, 'src/views/energy/EnergyRecordList.vue'), 'utf8')
  const api = fs.readFileSync(path.join(root, 'src/api/energy.js'), 'utf8')

  assert.match(page, /@change="handlePeriodMonthChange"/)
  assert.match(page, /getLastReading\(meterId,\s*\{\s*periodMonth:\s*form\.periodMonth\s*\}\)/)
  assert.match(api, /getLastReading\(meterId,\s*params\s*=\s*\{\}\)/)
  assert.match(api, /request\.get\(`\/energy\/meters\/\$\{meterId\}\/last-reading`,\s*\{\s*params\s*\}\)/)
})
