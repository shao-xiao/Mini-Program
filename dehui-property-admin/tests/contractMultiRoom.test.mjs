import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('contract form supports selecting buildings, floors and rooms separately', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

  assert.match(source, /v-model="form\.buildingIds"/)
  assert.match(source, /v-model="form\.floorIds"/)
  assert.match(source, /v-model="form\.roomIds"/)
  assert.match(source, /multiple/)
  assert.match(source, /collapse-tags/)
  assert.match(source, /handleBuildingIdsChange/)
  assert.match(source, /handleFloorIdsChange/)
  assert.match(source, /availableFloors/)
})

test('contract payload and table use multi-room fields', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

  assert.match(source, /roomSummary/)
  assert.match(source, /roomIds:\s*form\.roomIds\.map\(Number\)/)
  assert.match(source, /buildingIds:\s*\[\]/)
  assert.match(source, /floorIds:\s*\[\]/)
  assert.match(source, /roomIds:\s*\[\]/)
})

test('contract room options load floors from actual buildings instead of fixed building id', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

  assert.doesNotMatch(source, /BUILDING_ID\s*=\s*1/)
  assert.match(source, /request\.get\('\/buildings'\)/)
  assert.match(source, /for \(const building of buildings\.value\)/)
  assert.match(source, /\/buildings\/\$\{building\.id\}\/floors/)
  assert.match(source, /\/buildings\/\$\{building\.id\}\/floors\/\$\{floor\.id\}\/rooms/)
})

test('contract labels keep building separate from floor and room labels', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

  assert.match(source, /function buildingLabel/)
  assert.doesNotMatch(source, /function floorLabel[\s\S]*floor\.buildingName[\s\S]*function roomLabel/)
  assert.doesNotMatch(source, /function roomLabel[\s\S]*room\.buildingName[\s\S]*function roomDisplayName/)
})

test('contract dialog separates location, period and fee form layout grids', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

  assert.match(source, /class="form-grid location-grid"/)
  assert.match(source, /class="form-grid period-grid"/)
  assert.match(source, /class="form-grid fee-grid"/)
  assert.match(source, /class="remark-item"/)
  assert.match(source, /\.location-grid/)
  assert.match(source, /\.period-grid/)
  assert.match(source, /\.fee-grid/)
})
