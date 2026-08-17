import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('bill ledger exposes invoice preview action after invoice upload', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/BillList.vue'), 'utf8')

  assert.match(source, /预览发票/)
  assert.match(source, /previewInvoice\(row\)/)
})

test('bill invoice preview opens blob preview instead of forcing download', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/BillList.vue'), 'utf8')

  assert.match(source, /window\.open\('', '_blank'\)/)
  assert.match(source, /\/bills\/\$\{row\.id\}\/invoice\/download/)
  assert.match(source, /downloadBlob\(blob,\s*row\.invoiceFileName\s*\|\|\s*`\$\{row\.billNumber\}\.pdf`,\s*\{/)
  assert.match(source, /previewWindow/)
  assert.match(source, /download:\s*false/)
})
