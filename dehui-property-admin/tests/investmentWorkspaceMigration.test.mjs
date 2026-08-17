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
  content: read('src/views/investment/InvestmentContentList.vue'),
  listing: read('src/views/investment/InvestmentRoomListingList.vue'),
  lead: read('src/views/investment/InvestmentLeadList.vue')
}

test('investment main tables use the shared workspace stack', () => {
  for (const [name, source] of Object.entries(pages)) {
    assert.match(source, /<DataWorkspace\b/, `${name} must render DataWorkspace`)
    assert.match(source, /<DhicDataToolbar\b/, `${name} must render DhicDataToolbar`)
    assert.match(source, /<WorkspaceDataGrid\b/, `${name} must render WorkspaceDataGrid`)
    assert.match(source, /\.\.\.serverQueryParams\.value/)
    assert.match(source, /\.\.\.pageParams\(pagination\)/)
  }

  assert.doesNotMatch(pages.content, /<el-table(?:\s|>)/)
  assert.doesNotMatch(pages.lead, /<el-table(?:\s|>)/)
  assert.equal((pages.listing.match(/<el-table(?:\s|>)/g) || []).length, 1)
  assert.match(pages.listing, /<el-table :data="rooms"/)
})

test('investment pages retain their real business APIs and mutations', () => {
  assert.match(pages.content, /request\.get\('\/investment\/contents'/)
  assert.match(pages.content, /request\.get\('\/investment\/contents\/preview'/)
  assert.match(pages.content, /request\.post\('\/investment\/contents'/)
  assert.match(pages.content, /`\/investment\/contents\/\$\{row\.id\}\/publish`/)

  assert.match(pages.listing, /request\.get\('\/investment\/listings'/)
  assert.match(pages.listing, /request\.get\('\/investment\/rooms\/candidates'/)
  assert.match(pages.listing, /request\.post\('\/investment\/listings'/)
  assert.match(pages.listing, /`\/investment\/listings\/\$\{currentListing\.value\.id\}\/images`/)

  assert.match(pages.lead, /request\.get\('\/investment\/leads'/)
  assert.match(pages.lead, /request\.patch\(`\/investment\/leads\/\$\{row\.id\}\/status`/)
  assert.match(pages.lead, /request\.delete\(`\/investment\/leads\/\$\{row\.id\}`/)
})

test('investment row actions remain permission-code based', () => {
  const source = Object.values(pages).join('\n')
  assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
  assert.match(pages.content, /hasPermission\('investment-content:create'\)/)
  assert.match(pages.content, /hasPermission\('investment-content:publish'\)/)
  assert.match(pages.listing, /hasPermission\('investment-listing:create'\)/)
  assert.match(pages.listing, /hasPermission\('investment-listing:image'\)/)
  assert.match(pages.lead, /hasPermission\('investment-lead:follow'\)/)
  assert.match(pages.lead, /hasPermission\('investment-lead:delete'\)/)
})
