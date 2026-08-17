import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('contract ledger exposes electronic contract upload and online view actions', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

  assert.match(source, /上传电子合同/)
  assert.match(source, /查看电子合同/)
  assert.match(source, /selectContractFile\(row\)/)
  assert.match(source, /previewElectronicContract\(row\)/)
})

test('contract ledger uploads pdf to contract file endpoint and downloads blob for preview', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

  assert.match(source, /accept="application\/pdf"/)
  assert.match(source, /\/contracts\/\$\{row\.id\}\/file/)
  assert.match(source, /responseType:\s*'blob'/)
  assert.match(source, /downloadBlob\(blob,\s*row\.contractFileName/)
})
