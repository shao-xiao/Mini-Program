import { modulePermissionBundles } from '../config/modulePermissionBundles.js'
import { permissionIdsForCodes, resolveModulePermissionCodes } from './modulePermissionResolver.js'
import { permissionCode, uniquePermissionIds } from './rolePermissionTree.js'

export const matrixRoleCodes = [
  'SUPER_ADMIN',
  'ADMIN',
  'INVESTMENT_MANAGER',
  'STAFF',
  'FINANCE',
  'ACCOUNTANT',
  'CASHIER',
  'ENGINEER_MANAGER',
  'ENGINEER',
  'SECURITY_MANAGER',
  'SECURITY'
]

const roleTemplates = {
  SUPER_ADMIN: { all: true },
  ADMIN: {
    write: [
      'asset-spaces', 'asset-equipment', 'asset-energy', 'asset-parking', 'asset-meetings',
      'investment-manage', 'investment-leads', 'investment-announcements', 'tenant-workspace',
      'operation-workorders', 'operation-inspections', 'operation-visitors', 'finance-bills',
      'staff-mobile-workorders', 'staff-mobile-inspections', 'staff-mobile-energy',
      'staff-mobile-meetings', 'staff-mobile-visitors', 'staff-mobile-investment'
    ],
    read: ['dashboard', 'finance-dashboard', 'ai-daily-report', 'staff-mobile-access']
  },
  INVESTMENT_MANAGER: {
    write: ['investment-manage', 'investment-leads', 'tenant-workspace', 'staff-mobile-investment'],
    read: ['dashboard', 'asset-spaces', 'finance-bills', 'finance-dashboard', 'staff-mobile-access']
  },
  STAFF: {
    write: [
      'operation-workorders', 'operation-inspections', 'operation-visitors',
      'investment-announcements', 'asset-meetings',
      'staff-mobile-workorders', 'staff-mobile-inspections',
      'staff-mobile-meetings', 'staff-mobile-visitors'
    ],
    read: [
      'asset-spaces', 'asset-equipment', 'asset-parking', 'asset-energy',
      'tenant-workspace', 'finance-bills', 'staff-mobile-access', 'staff-mobile-energy'
    ]
  },
  FINANCE: {
    write: ['finance-bills'],
    read: ['finance-dashboard', 'tenant-workspace', 'asset-energy']
  },
  ACCOUNTANT: {
    write: ['finance-bills'],
    read: ['finance-dashboard', 'tenant-workspace', 'asset-energy']
  },
  CASHIER: {
    write: ['finance-bills'],
    read: ['finance-dashboard', 'tenant-workspace']
  },
  ENGINEER_MANAGER: {
    write: [
      'operation-workorders', 'operation-inspections', 'asset-equipment', 'asset-energy',
      'staff-mobile-workorders', 'staff-mobile-inspections', 'staff-mobile-energy'
    ],
    read: ['asset-spaces', 'tenant-workspace', 'staff-mobile-access']
  },
  ENGINEER: {
    write: [
      'operation-workorders', 'operation-inspections', 'asset-energy',
      'staff-mobile-workorders', 'staff-mobile-inspections', 'staff-mobile-energy'
    ],
    read: ['asset-equipment', 'asset-spaces', 'staff-mobile-access']
  },
  SECURITY_MANAGER: {
    write: [
      'operation-visitors', 'asset-parking', 'investment-announcements',
      'staff-mobile-visitors'
    ],
    read: ['operation-workorders', 'staff-mobile-access']
  },
  SECURITY: {
    write: ['operation-visitors', 'staff-mobile-visitors'],
    read: ['asset-parking', 'investment-announcements', 'staff-mobile-access']
  }
}

function treePermissions(tree = []) {
  const permissions = []
  tree.forEach(section => {
    ;(section.children || []).forEach(feature => {
      ;(feature.permissions || []).forEach(item => permissions.push(item))
    })
  })
  const seen = new Set()
  return permissions.filter(item => {
    const code = permissionCode(item)
    if (!code || seen.has(code)) return false
    seen.add(code)
    return true
  })
}

export function isMatrixRoleCode(roleCode) {
  return matrixRoleCodes.includes(String(roleCode || '').toUpperCase())
}

export function roleTemplateLabel(roleCode) {
  const code = String(roleCode || '').toUpperCase()
  const labels = {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    INVESTMENT_MANAGER: '物业招商经理',
    STAFF: '物业员工',
    FINANCE: '财务主管',
    ACCOUNTANT: '会计',
    CASHIER: '出纳',
    ENGINEER_MANAGER: '工程部负责人',
    ENGINEER: '工程部员工',
    SECURITY_MANAGER: '安保经理',
    SECURITY: '安保员工'
  }
  return labels[code] || code
}

export function buildRoleTemplateChanges(roleCode) {
  const template = roleTemplates[String(roleCode || '').toUpperCase()]
  if (!template) return {}
  if (template.all) {
    return Object.fromEntries(modulePermissionBundles.map(module => [module.key, {
      read: true,
      write: module.writeCodes.length > 0
    }]))
  }

  const read = new Set(template.read || [])
  const write = new Set(template.write || [])
  return Object.fromEntries(modulePermissionBundles.map(module => [module.key, {
    read: read.has(module.key) || write.has(module.key),
    write: module.writeCodes.length > 0 && write.has(module.key)
  }]))
}

export function buildRoleTemplatePermissionCodes(roleCode, availableCodes = []) {
  const template = roleTemplates[String(roleCode || '').toUpperCase()]
  if (!template) return []
  if (template.all) return uniquePermissionIds(availableCodes)
  return resolveModulePermissionCodes({
    originalCodes: [],
    changes: buildRoleTemplateChanges(roleCode),
    availableCodes
  })
}

export function buildRoleTemplatePermissionIds(roleCode, tree = []) {
  const permissions = treePermissions(tree)
  const codes = permissions.map(permissionCode)
  return uniquePermissionIds(permissionIdsForCodes(
    permissions,
    buildRoleTemplatePermissionCodes(roleCode, codes)
  ))
}

export function summarizeTemplateSelection(roleCode, tree = []) {
  const changes = buildRoleTemplateChanges(roleCode)
  const permissions = treePermissions(tree)
  const ids = buildRoleTemplatePermissionIds(roleCode, tree)
  let full = 0
  let readonly = 0
  let none = 0

  modulePermissionBundles.forEach(module => {
    const selection = changes[module.key]
    if (selection?.write) full += 1
    else if (selection?.read) readonly += 1
    else none += 1
  })

  return {
    label: roleTemplateLabel(roleCode),
    selected: ids.length,
    total: permissions.filter(item => !permissionCode(item).endsWith(':*')).length,
    featureTotal: modulePermissionBundles.length,
    permissionCount: ids.length,
    full,
    readonly,
    none
  }
}
