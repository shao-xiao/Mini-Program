import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const read = path => fs.readFileSync(new URL(path, import.meta.url), 'utf8')

test('visitor admin exposes review actions and binds QR through real protected file APIs', () => {
  const page = read('./VisitorList.vue')
  const dialog = read('../../components/visitor/VisitorQrCodeDialog.vue')

  assert.match(page, /\/visitors\/\$\{row\.id\}\/approve/)
  assert.match(page, /\/visitors\/\$\{row\.id\}\/reject/)
  assert.match(page, /row\.status === 'PENDING'/)
  assert.match(page, /row\.status === 'REGISTERED'/)
  assert.match(dialog, /uploadCustomFieldAttachment/)
  assert.match(dialog, /workspaceKey:\s*'visitors'/)
  assert.match(dialog, /\/visitors\/\$\{props\.visitor\.id\}\/qr-code/)
  assert.match(dialog, /responseType:\s*'blob'/)
})
