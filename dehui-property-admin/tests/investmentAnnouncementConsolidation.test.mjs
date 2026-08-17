import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

import { fallbackMenuSections } from '../src/config/access.js'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const router = read('src/router/index.js')
const managePage = read('src/views/investment/InvestmentManageView.vue')

test('investment management is one routed page with permission-isolated tabs', () => {
  assert.match(router, /path: '\/investment\/manage'/)
  assert.match(router, /InvestmentManageView\.vue/)
  assert.match(managePage, /label="招商展示" name="content"/)
  assert.match(managePage, /label="招商房源" name="listings"/)
  assert.match(managePage, /investment-content:view/)
  assert.match(managePage, /investment-listing:view/)
  assert.match(managePage, /router\.replace\(\{ query:/)
})

test('legacy investment and announcement routes preserve query parameters', () => {
  assert.match(router, /path: '\/investment\/contents'[\s\S]*tab: 'content'/)
  assert.match(router, /path: '\/investment\/listings'[\s\S]*tab: 'listings'/)
  assert.match(router, /path: '\/announcements'[\s\S]*path: '\/investment\/announcements'/)
  assert.match(router, /query: \{ \.\.\.to\.query/)
})

test('fallback navigation has exactly three investment announcement children', () => {
  const module = fallbackMenuSections.find(item => item.menuCode === 'investment-announcement')
  const lease = fallbackMenuSections.find(item => item.menuCode === 'lease')
  const operation = fallbackMenuSections.find(item => item.menuCode === 'operation')

  assert.deepEqual(module.children.map(item => item.path), [
    '/investment/manage',
    '/investment/leads',
    '/investment/announcements'
  ])
  assert.deepEqual(lease.children.map(item => item.path), ['/tenant/workspace'])
  assert.deepEqual(operation.children.map(item => item.path), [
    '/workorders',
    '/inspections',
    '/visitors'
  ])
})
