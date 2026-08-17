const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

test('energy page consumes server summary and history without client business ids', () => {
  const source = read('pages/energy/index.js')
  assert.match(source, /\/mobile\/energy\/summary/)
  assert.match(source, /\/mobile\/energy\/history/)
  assert.match(source, /canAccessFeature\('energy', profile\)/)
  assert.doesNotMatch(source, /tenantId|tenantContactId|sysUserId|mobileUserId/)
  assert.doesNotMatch(source, /unitPrice\s*\*/)
})

test('parking page selects company or self endpoint from capabilities only', () => {
  const source = read('pages/parking/index.js')
  assert.match(source, /\/mobile\/parking\/\$\{this\.data\.scope\}/)
  assert.match(source, /parkingCompany/)
  assert.match(source, /parkingMine/)
  assert.doesNotMatch(source, /tenantId|tenantContactId|sysUserId|mobileUserId/)
})
