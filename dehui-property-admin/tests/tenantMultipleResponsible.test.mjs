import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const editor = fs.readFileSync(path.join(root, 'src/views/tenant/workspace/ContactEditorDialog.vue'), 'utf8')
const details = fs.readFileSync(path.join(root, 'src/views/tenant/workspace/TenantWorkspaceDetails.vue'), 'utf8')
const workspace = fs.readFileSync(path.join(root, 'src/views/tenant/TenantWorkspaceView.vue'), 'utf8')

test('tenant contacts separate the multi-responsible role from the single primary responsible', () => {
  assert.match(editor, /label="主负责人"/)
  assert.match(editor, /可设置多位负责人/)
  assert.match(editor, /form\.role !== 'RESPONSIBLE'/)
  assert.match(details, /设为负责人/)
  assert.match(details, /设为主负责人/)
  assert.match(details, /promote-responsible/)
  assert.match(workspace, /@promote-responsible="promoteResponsibleContact"/)
  assert.match(workspace, /当前其他负责人保持不变/)
})
