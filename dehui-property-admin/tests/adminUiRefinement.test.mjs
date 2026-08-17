import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

const theme = read('src/styles/dhic-theme.css')
const layout = read('src/layout/MainLayout.vue')
const dataWorkspace = read('src/components/DataWorkspace.vue')
const dataToolbar = read('src/components/DhicDataToolbar.vue')
const billPage = read('src/views/tenant/BillList.vue')

test('admin theme keeps one token source and real compact control dimensions', () => {
  assert.equal((theme.match(/^:root\s*\{/gm) || []).length, 1)
  assert.match(theme, /--dhic-text-secondary:\s*#667085/)
  assert.match(theme, /\.el-button\.el-button--small:not\(\.is-link\)[\s\S]{0,260}min-height:\s*28px/)
  assert.match(theme, /\.dhic-multitable\.density-compact td\.el-table__cell\s*\{[\s\S]{0,100}height:\s*38px/)
})

test('admin shell exposes navigation, page context and restrained account actions', () => {
  assert.match(layout, /alt="德汇创新中心标志"/)
  assert.match(layout, /aria-label="后台主导航"/)
  assert.match(layout, /class="current-page-title">\{\{ currentPageTitle \}\}/)
  assert.match(layout, /const currentPageTitle = computed\(/)
  assert.match(layout, /class="change-password-btn"[\s\S]{0,120}\btext\b/)
  assert.match(layout, /class="logout-btn"[\s\S]{0,120}\bplain\b[\s\S]{0,120}type="danger"/)
})

test('shared workspaces use a compact accessible toolbar and header', () => {
  assert.match(dataToolbar, /role="toolbar" aria-label="数据视图工具栏"/)
  assert.match(dataToolbar, /min-height:\s*40px/)
  assert.match(dataWorkspace, /\.workspace-header\s*\{[\s\S]{0,260}min-height:\s*56px/)
  assert.match(dataWorkspace, /\.workspace-title-block h2\s*\{[\s\S]{0,180}font-size:\s*18px/)
})

test('bill workspace prevents repeated writes and preserves viewport space', () => {
  assert.match(billPage, /const billActionState = reactive\(new Map\(\)\)/)
  assert.match(billPage, /:loading="isBillActionPending\(row\)"/)
  assert.match(billPage, /:disabled="isBillActionPending\(row\)"/)
  assert.match(billPage, /startBillAction\(row,\s*['"]delete-bill['"]\)/)
  assert.match(billPage, /request\.delete\(`\/bills\/\$\{row\.id\}\/record`[\s\S]{0,260}silent:\s*true/)
  assert.match(billPage, /basisActionLoading === 'generate'/)
  assert.match(billPage, /:icon="MoreFilled"/)
  assert.match(billPage, /\.finance-bill-page\s*\{[\s\S]{0,220}height:\s*100%;[\s\S]{0,100}padding:\s*0;/)
})
