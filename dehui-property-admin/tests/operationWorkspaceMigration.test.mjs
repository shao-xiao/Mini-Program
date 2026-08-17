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

const pages = {
  visitor: read('src/views/operation/VisitorList.vue'),
  inspection: read('src/views/operation/InspectionList.vue'),
  announcement: read('src/views/operation/AnnouncementList.vue')
}

test('operation list pages use the shared workspace stack', () => {
  for (const [name, source] of Object.entries(pages)) {
    assert.match(source, /<DataWorkspace\b/, `${name} must render DataWorkspace`)
    assert.match(source, /<DhicDataToolbar\b/, `${name} must render DhicDataToolbar`)
    assert.match(source, /<WorkspaceDataGrid\b/, `${name} must render WorkspaceDataGrid`)
  }
})

test('operation pages retain real APIs and mutations', () => {
  assert.match(pages.visitor, /request\.get\('\/visitors'/)
  assert.match(pages.visitor, /request\.post\('\/visitors'/)
  assert.match(pages.visitor, /`\/visitors\/\$\{row\.id\}\/enter`/)
  assert.match(pages.visitor, /`\/visitors\/\$\{row\.id\}\/leave`/)
  assert.match(pages.visitor, /`\/visitors\/\$\{row\.id\}\/cancel`/)

  assert.match(pages.inspection, /request\.get\('\/inspections'/)
  assert.match(pages.inspection, /request\.get\('\/inspections\/summary'/)
  assert.match(pages.inspection, /request\.post\('\/inspections'/)
  assert.match(pages.inspection, /request\.put\(`\/inspections\/\$\{editingId\.value\}`/)
  assert.match(pages.inspection, /`\/inspections\/\$\{row\.id\}\/close`/)

  assert.match(pages.announcement, /request\.get\('\/announcements'/)
  assert.match(pages.announcement, /request\.post\('\/announcements'/)
  assert.match(pages.announcement, /request\.put\(`\/announcements\/\$\{form\.id\}`/)
  assert.match(pages.announcement, /`\/announcements\/\$\{row\.id\}\/publish`/)
})

test('operation row actions stay permission-code based', () => {
  const source = Object.values(pages).join('\n')
  assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
  assert.match(pages.visitor, /hasPermission\('visitor:create'\)/)
  assert.match(pages.visitor, /hasPermission\('visitor:update'\)/)
  assert.match(pages.inspection, /hasPermission\('inspection:create'\)/)
  assert.match(pages.inspection, /hasPermission\('inspection:update'\)/)
  assert.match(pages.inspection, /hasPermission\('inspection:delete'\)/)
  assert.match(pages.announcement, /hasPermission\('announcement:create'\)/)
  assert.match(pages.announcement, /hasPermission\('announcement:update'\)/)
  assert.match(pages.announcement, /hasPermission\('announcement:delete'\)/)
})

test('inspection keeps server paging and workspace query parameters', () => {
  assert.match(pages.inspection, /\.\.\.serverQueryParams\.value/)
  assert.match(pages.inspection, /\.\.\.pageParams\(pagination\)/)
  assert.match(pages.inspection, /:total="pagination\.total"/)
})
