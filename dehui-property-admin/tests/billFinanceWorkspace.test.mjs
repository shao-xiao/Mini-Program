import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')
const page = read('src/views/tenant/BillList.vue')

test('bill management is a two-column tenant finance workspace', () => {
  assert.match(page, /<BillTenantDirectoryPane\b/)
  assert.match(page, /class="finance-detail-pane"/)
  assert.match(page, /财务概览/)
  assert.match(page, /账单明细/)
  assert.match(page, /收款记录/)
  assert.match(page, /发票管理/)
  assert.match(page, /<DataWorkspace\b/)
  assert.match(page, /<WorkspaceDataGrid\b/)
  assert.match(page, /class="bill-detail-workspace"/)
  assert.match(page, /class="bill-toolbar-scope"/)
  assert.match(page, /<WorkspaceDataGrid[\s\S]{0,1200}height="100%"/)
})

test('tenant and contract scope remain server-side for list, summary and export', () => {
  assert.match(page, /const routeScopeParams = computed\(\(\) => financeScopeParams/)
  assert.match(page, /request\.get\('\/bills'[\s\S]{0,220}\.\.\.routeScopeParams\.value[\s\S]{0,120}\.\.\.pageParams\(pagination\)/)
  assert.match(page, /request\.get\('\/bills\/summary'[\s\S]{0,140}summaryFilterParams\(\)/)
  assert.match(page, /return \{ \.\.\.filters, \.\.\.routeScopeParams\.value \}/)
  assert.match(page, /loadAllWorkspaceRecords[\s\S]{0,260}\.\.\.routeScopeParams\.value/)
})

test('payment and invoice ledgers use additive paged APIs and stale-request guards', () => {
  assert.match(page, /request\.get\('\/bills\/payments'/)
  assert.match(page, /request\.get\('\/bills\/invoices'/)
  assert.match(page, /paymentRequestGuard\.isCurrent/)
  assert.match(page, /invoiceRequestGuard\.isCurrent/)
  assert.match(page, /paymentRows\.value = \[\]/)
  assert.match(page, /invoiceRows\.value = \[\]/)
})

test('finance and tenant workspaces preserve tenant-contract context without role shortcuts', () => {
  assert.match(page, /path: '\/tenant\/workspace'/)
  assert.match(page, /tenantId: selectedTenantId\.value/)
  assert.match(page, /contractId: selectedContractId\.value/)
  assert.doesNotMatch(page, /role\s*===\s*['"]ADMIN['"]/)
  assert.doesNotMatch(page, /axios\.|fetch\(/)
})

test('bill number can be changed through the existing bill edit workflow', () => {
  assert.match(page, /<el-form-item label="账单编号" prop="billNumber">/)
  assert.match(page, /v-model="form\.billNumber"[\s\S]{0,180}:maxlength="64"/)
  assert.doesNotMatch(page, /v-model="form\.billNumber"[^>]*:disabled="isEditingBill"/)
  assert.match(page, /billNumber:\s*form\.billNumber\?\.trim\(\) \|\| null/)
})

test('bill voiding and record deletion remain separate destructive actions', () => {
  assert.match(page, /command="void"[\s\S]{0,180}>作废账单<\/el-dropdown-item>/)
  assert.match(page, /command="delete"[\s\S]{0,360}>删除账单<\/el-dropdown-item>/)
  assert.match(page, /hasPermission\(billPermissions\.void\)/)
  assert.match(page, /hasPermission\(billPermissions\.delete\)/)
  assert.match(page, /request\.post\(`\/bills\/\$\{row\.id\}\/void`/)
  assert.match(page, /request\.delete\(`\/bills\/\$\{row\.id\}\/record`[\s\S]{0,260}silent:\s*true/)
})

test('record deletion requires typing the complete bill number before submission', () => {
  assert.match(page, /request\.post\(`\/bills\/\$\{row\.id\}\/record\/check`/)
  assert.match(page, /if \(!eligibility\?\.allowed\)/)
  assert.match(page, /ElMessageBox\.prompt\([\s\S]{0,900}请输入完整账单编号/)
  assert.match(page, /inputValidator:[\s\S]{0,260}confirmedBillNumber/)
  assert.match(page, /inputValidator:[\s\S]{0,260}===/)
  assert.match(page, /confirmButtonType:\s*'danger'/)
})
