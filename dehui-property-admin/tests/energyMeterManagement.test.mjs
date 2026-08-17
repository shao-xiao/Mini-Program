import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('energy module exposes the unified asset route while preserving meter APIs and legacy redirects', () => {
  const router = fs.readFileSync(path.join(root, 'src/router/index.js'), 'utf8')
  const access = fs.readFileSync(path.join(root, 'src/config/access.js'), 'utf8')
  const api = fs.readFileSync(path.join(root, 'src/api/energy.js'), 'utf8')
  const recordPage = fs.readFileSync(path.join(root, 'src/views/energy/EnergyRecordList.vue'), 'utf8')
  const meterPage = fs.readFileSync(path.join(root, 'src/views/energy/EnergyMeterList.vue'), 'utf8')
  const workspaceConfig = fs.readFileSync(path.join(root, 'src/workspaceConfigs/energyWorkspaceConfig.js'), 'utf8')

  assert.match(router, /path:\s*'\/assets\/energy'/)
  assert.match(router, /AssetEnergyView/)
  assert.match(router, /path:\s*'\/energy\/meters'[\s\S]*redirect:[\s\S]*\/assets\/energy/)
  assert.match(access, /能耗管理/)
  assert.match(access, /\/assets\/energy/)
  assert.match(api, /createEnergyMeter/)
  assert.match(api, /updateEnergyMeter/)
  assert.match(api, /deleteEnergyMeter/)
  assert.match(recordPage, /!props\.embedded[\s\S]*energyReadingDataActions\.meters\.label/)
  assert.match(workspaceConfig, /label: '表具管理'/)
  assert.match(workspaceConfig, /label: '新增表具'/)
  assert.match(meterPage, /createEnergyMeterWorkspaceConfig/)
  assert.match(meterPage, /meterNo/)
  assert.match(meterPage, /getEnergyMeters/)
  assert.match(meterPage, /createEnergyMeter/)
})

test('creating a meter inside an energy subject establishes its initial effective-dated binding', () => {
  const meterPage = fs.readFileSync(path.join(root, 'src/views/energy/EnergyMeterList.vue'), 'utf8')

  assert.match(meterPage, /subjectType:\s*initialSubjectType/)
  assert.match(meterPage, /effectiveFrom:\s*form\.commissioningDate\s*\|\|\s*currentDate\(\)/)
  assert.match(meterPage, /tenantId:\s*initialSubjectType\s*===\s*'TENANT'/)
})
