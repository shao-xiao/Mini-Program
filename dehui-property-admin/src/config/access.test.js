import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { canAccessPath, canonicalAccessPath, getFirstAccessibleRoute, getVisibleMenuSections } from './access.js'
import { hasPermission } from '../utils/permission.js'

const store = new Map()

global.localStorage = {
  getItem(key) {
    return store.has(key) ? store.get(key) : null
  },
  setItem(key, value) {
    store.set(key, String(value))
  },
  removeItem(key) {
    store.delete(key)
  },
  clear() {
    store.clear()
  }
}

function findLeaseMenus(menus) {
  return menus.find(item => item.menuCode === 'lease' || item.index === 'lease')
}

function findInvestmentAnnouncementMenus(menus) {
  return menus.find(item => item.menuCode === 'investment-announcement' || item.index === 'investment-announcement')
}

beforeEach(() => {
  localStorage.clear()
})

test('SUPER_ADMIN consolidates stale investment menus into one announcement module', () => {
  localStorage.setItem('roles', JSON.stringify(['SUPER_ADMIN']))
  localStorage.setItem('menus', JSON.stringify([
    {
      title: '租赁管理',
      menuName: '租赁管理',
      menuCode: 'lease',
      children: [
        { title: '招商内容', menuName: '招商内容', path: '/investment/contents' },
        { title: '招商线索', menuName: '招商线索', path: '/investment/leads' },
        { title: '合同台账', menuName: '合同台账', path: '/contracts' },
        { title: '租户管理', menuName: '租户管理', path: '/tenants' },
        { title: '租户入驻', menuName: '租户入驻', path: '/leases' }
      ]
    }
  ]))

  const menus = getVisibleMenuSections(['SUPER_ADMIN'])
  const leaseMenu = findLeaseMenus(menus)
  const investmentMenu = findInvestmentAnnouncementMenus(menus)

  assert.ok(leaseMenu)
  assert.deepEqual(
    leaseMenu.children.map(item => item.path),
    ['/tenant/workspace']
  )
  assert.deepEqual(investmentMenu.children.map(item => item.path), [
    '/investment/manage',
    '/investment/leads',
    '/investment/announcements'
  ])
})

test('non-admin visible menus do not receive fallback-only menu items', () => {
  localStorage.setItem('roles', JSON.stringify(['ENGINEER']))
  localStorage.setItem('permissions', JSON.stringify(['investment-content:view', 'investment-listing:view']))
  localStorage.setItem('menus', JSON.stringify([
    {
      title: '租赁管理',
      menuName: '租赁管理',
      menuCode: 'lease',
      children: [
        { title: '招商内容', menuName: '招商内容', path: '/investment/contents' }
      ]
    }
  ]))

  const menus = getVisibleMenuSections(['ENGINEER'])
  const investmentMenu = findInvestmentAnnouncementMenus(menus)

  assert.equal(findLeaseMenus(menus), undefined)
  assert.deepEqual(investmentMenu.children.map(item => item.path), ['/investment/manage'])
})

test('non-admin visible menus require matching view permissions', () => {
  localStorage.setItem('roles', JSON.stringify(['ENGINEER']))
  localStorage.setItem('permissions', JSON.stringify(['asset:view']))
  localStorage.setItem('menus', JSON.stringify([
    {
      title: '资产管理',
      menuName: '资产管理',
      menuCode: 'asset',
      children: [
        { title: '楼宇管理', menuName: '楼宇管理', path: '/buildings' },
        { title: '设备台账', menuName: '设备台账', path: '/equipment' }
      ]
    }
  ]))

  const assetMenu = getVisibleMenuSections(['ENGINEER']).find(item => item.menuCode === 'asset' || item.index === 'asset')

  assert.ok(assetMenu)
  assert.deepEqual(assetMenu.children.map(item => item.path), ['/assets/equipment'])
})

test('route access follows visible menus and view permissions for non-admin users', () => {
  localStorage.setItem('token', 'token')
  localStorage.setItem('roles', JSON.stringify(['ENGINEER']))
  localStorage.setItem('permissions', JSON.stringify(['workorder:view']))
  localStorage.setItem('menus', JSON.stringify([
    {
      title: '运营管理',
      menuName: '运营管理',
      menuCode: 'operation',
      children: [
        { title: '工单管理', menuName: '工单管理', path: '/workorders' }
      ]
    },
    {
      title: '财务管理',
      menuName: '财务管理',
      menuCode: 'finance',
      children: [
        { title: '账单管理', menuName: '账单管理', path: '/bills' }
      ]
    }
  ]))

  assert.equal(canAccessPath(['ENGINEER'], '/workorders'), true)
  assert.equal(canAccessPath(['ENGINEER'], '/workorders/detail/1'), true)
  assert.equal(canAccessPath(['ENGINEER'], '/bills'), false)
  assert.equal(canAccessPath(['ENGINEER'], '/dashboard'), false)
  assert.equal(getFirstAccessibleRoute(['ENGINEER']), '/workorders')
})

test('dashboard remains the preferred home route when it is accessible', () => {
  localStorage.setItem('token', 'token')
  localStorage.setItem('roles', JSON.stringify(['SUPER_ADMIN']))
  localStorage.setItem('permissions', JSON.stringify(['dashboard:view']))
  localStorage.setItem('menus', JSON.stringify([
    { title: 'Work orders', menuName: 'Work orders', path: '/workorders' },
    { title: 'Dashboard', menuName: 'Dashboard', path: '/dashboard' }
  ]))

  assert.equal(getFirstAccessibleRoute(['SUPER_ADMIN']), '/dashboard')
})

test('users without any visible page fall back to the forbidden page', () => {
  localStorage.setItem('token', 'token')
  localStorage.setItem('roles', JSON.stringify(['STAFF']))
  localStorage.setItem('permissions', JSON.stringify([]))
  localStorage.setItem('menus', JSON.stringify([
    { title: 'Dashboard', menuName: 'Dashboard', path: '/dashboard' }
  ]))

  assert.equal(getFirstAccessibleRoute(['STAFF']), '/403')
})

test('unified asset route and legacy aliases preserve granular permission codes', () => {
  const assetMenus = JSON.stringify([
    {
      title: '资产管理',
      menuName: '资产管理',
      menuCode: 'asset',
      children: [
        { title: '楼宇管理', menuName: '楼宇管理', path: '/buildings' },
        { title: '楼层管理', menuName: '楼层管理', path: '/floors' },
        { title: '房间管理', menuName: '房间管理', path: '/rooms' }
      ]
    }
  ])

  localStorage.setItem('token', 'token')
  localStorage.setItem('roles', JSON.stringify(['ADMIN']))
  localStorage.setItem('menus', assetMenus)
  localStorage.setItem('permissions', JSON.stringify([
    'building:view',
    'building:create',
    'floor:view',
    'room:view'
  ]))

  assert.equal(canAccessPath(['ADMIN'], '/assets/spaces'), true)
  assert.equal(canAccessPath(['ADMIN'], '/buildings'), true)
  assert.equal(canAccessPath(['ADMIN'], '/floors'), true)
  assert.equal(canAccessPath(['ADMIN'], '/rooms'), true)
  assert.equal(hasPermission('building:create'), true)
  assert.equal(hasPermission('building:update'), false)

  localStorage.setItem('roles', JSON.stringify(['STAFF']))
  localStorage.setItem('permissions', JSON.stringify(['floor:view']))

  assert.equal(canAccessPath(['STAFF'], '/assets/spaces'), true)
  assert.equal(canAccessPath(['STAFF'], '/buildings'), true)
  assert.equal(canAccessPath(['STAFF'], '/floors'), true)
  assert.equal(canAccessPath(['STAFF'], '/rooms'), true)
  assert.equal(hasPermission('floor:create'), false)
  assert.equal(hasPermission('floor:update'), false)
  assert.equal(hasPermission('floor:delete'), false)
})

test('legacy asset, energy, parking and meeting routes resolve to unified access paths', () => {
  assert.equal(canonicalAccessPath('/buildings'), '/assets/spaces')
  assert.equal(canonicalAccessPath('/energy/records'), '/assets/energy')
  assert.equal(canonicalAccessPath('/parking/spaces'), '/assets/parking')
  assert.equal(canonicalAccessPath('/parking/bills'), '/bills')
  assert.equal(canonicalAccessPath('/meetings/bookings'), '/assets/meetings')
  assert.equal(canonicalAccessPath('/workorders'), '/workorders')
})

test('legacy investment and announcement routes resolve to unified access paths', () => {
  assert.equal(canonicalAccessPath('/investment/contents'), '/investment/manage')
  assert.equal(canonicalAccessPath('/investment/listings'), '/investment/manage')
  assert.equal(canonicalAccessPath('/announcements'), '/investment/announcements')
  assert.equal(canonicalAccessPath('/investment/leads'), '/investment/leads')
})

test('legacy tenant, contract and occupancy routes resolve to the unified tenant workspace', () => {
  assert.equal(canonicalAccessPath('/tenants'), '/tenant/workspace')
  assert.equal(canonicalAccessPath('/contracts'), '/tenant/workspace')
  assert.equal(canonicalAccessPath('/leases'), '/tenant/workspace')
  assert.equal(canonicalAccessPath('/fee-rules'), '/bills')
  assert.equal(canonicalAccessPath('/billing-plans'), '/bills')
})

test('tenant workspace menu requires the complete retained domain read bundle', () => {
  localStorage.setItem('token', 'token')
  localStorage.setItem('roles', JSON.stringify(['STAFF']))
  localStorage.setItem('permissions', JSON.stringify(['tenant:view', 'contract:view', 'lease:view']))
  localStorage.setItem('menus', JSON.stringify([{
    title: '租赁管理',
    menuCode: 'lease',
    children: [{ title: '合同台账', path: '/contracts' }]
  }]))

  const menu = findLeaseMenus(getVisibleMenuSections(['STAFF']))
  assert.equal(menu.title, '租户管理')
  assert.deepEqual(menu.children.map(item => item.path), ['/tenant/workspace'])
  assert.equal(canAccessPath(['STAFF'], '/tenant/workspace'), true)
})

test('a partial unified investment read bundle exposes only its permitted module route', () => {
  localStorage.setItem('token', 'token')
  localStorage.setItem('roles', JSON.stringify(['INVESTMENT_MANAGER']))
  localStorage.setItem('permissions', JSON.stringify(['investment-listing:view']))
  localStorage.setItem('menus', JSON.stringify([{
    title: '租赁管理',
    menuCode: 'lease',
    children: [{ title: '招商房源', path: '/investment/listings' }]
  }]))

  const investmentMenu = findInvestmentAnnouncementMenus(getVisibleMenuSections(['INVESTMENT_MANAGER']))
  assert.equal(investmentMenu, undefined)
  assert.equal(canAccessPath(['INVESTMENT_MANAGER'], '/investment/manage'), true)
  assert.equal(hasPermission('investment-content:view'), false)
})
