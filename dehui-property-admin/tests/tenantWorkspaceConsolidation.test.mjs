import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

test('tenant management exposes one workspace route and redirects all legacy entry points', () => {
  const router = read('src/router/index.js')
  const access = read('src/config/access.js')

  assert.match(router, /path: '\/tenant\/workspace'[\s\S]*TenantWorkspaceView\.vue/)
  assert.match(router, /path: '\/tenants'[\s\S]{0,180}redirect:[\s\S]{0,180}'\/tenant\/workspace'/)
  assert.match(router, /path: '\/contracts'[\s\S]{0,220}panel: 'contracts'/)
  assert.match(router, /path: '\/leases'[\s\S]{0,220}panel: 'occupancy'/)
  assert.match(router, /path: '\/fee-rules'[\s\S]{0,180}path: '\/bills'/)
  assert.match(router, /path: '\/billing-plans'[\s\S]{0,180}path: '\/bills'/)
  assert.match(access, /title: '租户管理'[\s\S]{0,260}title: '租户档案'[\s\S]{0,160}path: '\/tenant\/workspace'/)
})

test('tenant workspace is a two-column composition with contracts inside the detail workspace', () => {
  const source = read('src/views/tenant/TenantWorkspaceView.vue')
  const details = read('src/views/tenant/workspace/TenantWorkspaceDetails.vue')

  assert.match(source, /<TenantDirectoryPane\b/)
  assert.doesNotMatch(source, /<ContractDirectoryPane\b/)
  assert.match(source, /<TenantWorkspaceDetails\b/)
  assert.match(source, /<TenantEditorDialog\b/)
  assert.match(source, /<CustomFieldValueDrawer\b/)
  assert.match(source, /<CustomFieldForm\b/)
  assert.match(details, /<WorkspaceDataGrid\b/)
  assert.match(details, /合同关联的全部房间/)
  assert.match(details, /返回合同列表/)
  assert.match(details, /tenantContractFilterOptions/)
  assert.match(details, /关联账单（只读）/)
  assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
})

test('tenant logo is configured in the editor without restoring a default detail avatar', () => {
  const editor = read('src/views/tenant/workspace/TenantEditorDialog.vue')
  const logoField = read('src/views/tenant/workspace/TenantLogoField.vue')
  const details = read('src/views/tenant/workspace/TenantWorkspaceDetails.vue')
  const workspace = read('src/views/tenant/workspace/useTenantWorkspace.js')

  assert.match(editor, /<TenantLogoField\b/)
  assert.match(editor, /logoFileId/)
  assert.match(editor, /const \{ logoFileId, logoUrl, logoFileName, \.\.\.payload \} = form/)
  assert.match(logoField, /workspaceKey: 'tenants'/)
  assert.match(logoField, /fieldType: 'IMAGE'/)
  assert.match(workspace, /request\.put\(`\/tenants\/\$\{tenantId\}\/logo`, \{[\s\S]{0,100}logoFileId:/)
  assert.doesNotMatch(editor, /dehui-logo/)
  assert.doesNotMatch(logoField, /dehui-logo/)
  assert.doesNotMatch(details, /detail-avatar/)
})

test('tenant and legacy contract pages contain no bill generation entry point', () => {
  const workspace = read('src/views/tenant/TenantWorkspaceView.vue')
  const details = read('src/views/tenant/workspace/TenantWorkspaceDetails.vue')
  const legacyContract = read('src/views/tenant/ContractList.vue')

  for (const source of [workspace, details, legacyContract]) {
    assert.doesNotMatch(source, /生成到账期账单|生成到期账单|批量生成账单|contracts\/generate-bills/)
  }
})

test('contract lifecycle uses explicit audited endpoints instead of a dynamic action path', () => {
  const source = read('src/views/tenant/workspace/useTenantWorkspace.js')

  for (const action of ['submit', 'approve', 'activate', 'terminate', 'cancel']) {
    assert.match(source, new RegExp(`\\/contracts\\/\\$\\{contractId\\}\\/${action}`))
  }
  assert.doesNotMatch(source, /\/contracts\/\$\{contract\.id\}\/\$\{action\}/)
})

test('contract upload and detail actions make the rental-summary lifecycle explicit', () => {
  const workspace = read('src/views/tenant/TenantWorkspaceView.vue')
  const details = read('src/views/tenant/workspace/TenantWorkspaceDetails.vue')

  assert.match(workspace, /主合同已上传[\s\S]{0,120}仍为草稿[\s\S]{0,120}租赁摘要/)
  assert.match(details, /contract\.status === 'DRAFT'[\s\S]{0,140}提交审批/)
  assert.match(details, /pendingContract[\s\S]{0,140}审批通过/)
  assert.match(details, /h\(TenantContractLifecycleNotice\b/)
})

test('financial navigation keeps tenant and contract scope for server-side bill queries', () => {
  const workspace = read('src/views/tenant/TenantWorkspaceView.vue')
  const bills = read('src/views/tenant/BillList.vue')

  assert.match(workspace, /tenantId: selectedTenantId\.value[\s\S]{0,120}contractId: selectedContractId\.value/)
  assert.match(bills, /routeScopeParams/)
  assert.match(bills, /\.\.\.routeScopeParams\.value[\s\S]{0,100}\.\.\.pageParams\(pagination\)/)
  assert.match(bills, /return \{ \.\.\.filters, \.\.\.routeScopeParams\.value \}/)
})
