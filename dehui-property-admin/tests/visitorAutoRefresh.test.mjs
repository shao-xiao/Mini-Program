import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const visitorPage = fs.readFileSync(
  path.join(root, 'src/views/operation/VisitorList.vue'),
  'utf8'
)

test('visitor queue refreshes on focus and at a bounded interval', () => {
  assert.match(visitorPage, /VISITOR_AUTO_REFRESH_MS = 15_000/)
  assert.match(visitorPage, /window\.addEventListener\('focus', refreshVisitorsWhenVisible\)/)
  assert.match(visitorPage, /document\.addEventListener\('visibilitychange', refreshVisitorsWhenVisible\)/)
  assert.match(visitorPage, /refreshVisitorsInBackground/)
  assert.match(visitorPage, /window\.clearInterval\(visitorRefreshTimer\)/)
})
