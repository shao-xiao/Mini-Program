import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  routedPageWorkspaceExceptions,
  routedWorkspacePages,
  tableWorkspaceLegacyAllowlist,
  workspaceRoutedTables
} from '../src/config/tableWorkspaceLegacyAllowlist.js'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = relativePath => fs.readFileSync(path.join(root, relativePath), 'utf8')
const normalize = value => value.replaceAll('\\', '/')

test('every dynamic admin route is classified as a workspace table or a documented exception', () => {
  const actualRoutes = parseDynamicRoutes(read('src/router/index.js'))
  const classified = [...routedWorkspacePages, ...routedPageWorkspaceExceptions]

  assert.equal(new Set(classified.map(item => item.route)).size, classified.length, 'route classifications must be unique')
  assert.deepEqual(
    [...actualRoutes.keys()].sort(),
    classified.map(item => item.route).sort()
  )
  classified.forEach(item => {
    assert.equal(actualRoutes.get(item.route), item.component, `${item.route} must point to ${item.component}`)
  })
})

test('every active formal workspace exposes the complete shared workspace controls', () => {
  workspaceRoutedTables.forEach(item => {
    const source = read(item.component)
    assert.match(source, /<DataWorkspace\b/, `${item.route} must use DataWorkspace`)
    assert.match(source, /<DhicDataToolbar\b/, `${item.route} must use DhicDataToolbar`)
    assert.match(source, /<WorkspaceDataGrid\b/, `${item.route} must use WorkspaceDataGrid`)
    assert.match(source, /useDhicTableView\(/, `${item.route} must use the shared table-view facade`)
    assert.match(source, new RegExp(`\\b${item.config}\\b`), `${item.route} must bind ${item.config}`)
    assert.match(source, /:filter-options=/, `${item.route} must expose filtering`)
    assert.match(source, /:sort-options=/, `${item.route} must expose sorting`)
    assert.match(source, /:group-options=/, `${item.route} must expose grouping`)
    assert.match(source, /:visible-columns=/, `${item.route} must expose field visibility`)
    assert.match(source, /:row-density=/, `${item.route} must expose row density`)
    assert.match(source, /(?:^|\s):?row-key=/m, `${item.route} must use a stable row key`)
    assert.doesNotMatch(
      source,
      /\b(?:list|create|update|delete|batchRead)CustomField(?:Definition|Values?)\b/,
      `${item.route} must use the shared custom-field facade instead of page-level API calls`
    )
  })
})

test('unified asset pages host the expected existing workspace implementations', () => {
  const spaces = read('src/views/assets/AssetSpacesView.vue')
  const energy = read('src/views/assets/AssetEnergyView.vue')
  const parking = read('src/views/assets/AssetParkingView.vue')
  const meetings = read('src/views/assets/AssetMeetingsView.vue')

  assert.match(spaces, /<RoomList\b/)
  assert.match(energy, /<EnergyMeterList\b/)
  assert.match(energy, /<EnergyRecordList\b/)
  assert.match(energy, /<EnergyStats\b/)
  assert.match(parking, /<ParkingSpaceList\b/)
  assert.match(parking, /parkingAssignmentWorkspaceConfig/)
  assert.match(meetings, /<MeetingBookingList\b/)
})

test('custom-field management is a real shared implementation rather than a page placeholder', () => {
  const fieldManager = read('src/components/FieldManager.vue')
  const customFieldForm = read('src/components/workspace/CustomFieldForm.vue')
  const toolbar = read('src/components/DhicDataToolbar.vue')
  const facade = read('src/composables/useDhicTableView.js')
  const grid = read('src/components/workspace/WorkspaceDataGrid.vue')

  assert.match(fieldManager, /create:custom-field/)
  assert.match(customFieldForm, /CustomFieldDefinitionDialog/)
  assert.match(customFieldForm, />新增字段</)
  assert.match(toolbar, /createManagedCustomField/)
  assert.match(facade, /useWorkspaceCustomFields/)
  assert.match(facade, /useWorkspaceCustomFieldValues/)
  assert.match(grid, /CustomFieldCellRenderer/)
  assert.match(grid, /CustomFieldCellEditor/)
})

test('raw Element Plus tables only remain in the strict documented allowlist', () => {
  const viewRoot = path.join(root, 'src/views')
  const actual = new Map()
  for (const file of walkVueFiles(viewRoot)) {
    const source = fs.readFileSync(file, 'utf8')
    const occurrences = (source.match(/<el-table(?:\s|>)/g) || []).length
    if (occurrences > 0) actual.set(normalize(path.relative(root, file)), occurrences)
  }

  const expected = new Map(tableWorkspaceLegacyAllowlist.map(item => [item.file, item.occurrences]))
  assert.deepEqual([...actual.entries()].sort(), [...expected.entries()].sort())

  tableWorkspaceLegacyAllowlist.forEach(item => {
    assert.ok(item.purpose.trim(), `${item.file} requires a purpose`)
    assert.ok(item.reason.trim(), `${item.file} requires a reason`)
    assert.equal(typeof item.isSummary, 'boolean', `${item.file} must classify summary usage`)
    assert.ok(item.followUp.trim(), `${item.file} requires an explicit follow-up decision`)
  })
})

test('legacy asset route remains explicitly non-operational instead of being presented as migrated', () => {
  const source = read('../dehui-property-management/src/main/java/com/dehui/property/modules/legacy/LegacyWriteController.java')
  assert.match(source, /@GetMapping\("\/assets"\)/)
  assert.match(source, /PageResponse<>\(List\.of\(\), 0L/)
  assert.match(source, /BusinessException\.notImplemented\("legacy module"\)/)
})

function parseDynamicRoutes(source) {
  const routes = new Map()
  const lines = source.split(/\r?\n/)
  for (let index = 0; index < lines.length; index += 1) {
    const pathMatch = lines[index].match(/\bpath:\s*'([^']+)'/)
    if (!pathMatch) continue
    for (let cursor = index + 1; cursor < Math.min(index + 6, lines.length); cursor += 1) {
      if (lines[cursor].match(/\bpath:\s*'/)) break
      const componentMatch = lines[cursor].match(/component:\s*\(\)\s*=>\s*import\('\.\.\/views\/([^']+)'\)/)
      if (!componentMatch) continue
      routes.set(pathMatch[1], `src/views/${normalize(componentMatch[1])}`)
      break
    }
  }
  return routes
}

function walkVueFiles(directory) {
  const files = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) files.push(...walkVueFiles(target))
    else if (entry.isFile() && entry.name.endsWith('.vue')) files.push(target)
  }
  return files
}
