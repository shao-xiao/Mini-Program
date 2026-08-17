import { getCurrentPermissions, getCurrentRoles } from '../utils/permission.js'

function defineModule(section, definition) {
  return Object.freeze({
    ...definition,
    sectionKey: section.key,
    sectionTitle: section.title,
    sectionMenuCode: section.menuCode,
    writeRequiresExact: Boolean(section.permissionOnly && !section.bulkAccess),
    readCodes: Object.freeze([...(definition.readCodes || [])]),
    writeCodes: Object.freeze([...(definition.writeCodes || [])])
  })
}

function defineSection(definition) {
  const section = {
    key: definition.key,
    title: definition.title,
    menuCode: definition.menuCode,
    standalone: Boolean(definition.standalone),
    permissionOnly: Boolean(definition.permissionOnly),
    bulkAccess: definition.bulkAccess !== false
  }
  return Object.freeze({
    ...section,
    accessModule: definition.accessModule
      ? defineModule(section, definition.accessModule)
      : null,
    modules: Object.freeze(definition.modules.map(module => defineModule(section, module)))
  })
}

export const modulePermissionSections = Object.freeze([
  defineSection({
    key: 'dashboard',
    title: '驾驶舱',
    menuCode: 'dashboard',
    standalone: true,
    modules: [
      {
        key: 'dashboard',
        title: '驾驶舱',
        menuCode: 'dashboard',
        route: '/dashboard',
        readCodes: ['dashboard:view']
      }
    ]
  }),
  defineSection({
    key: 'asset',
    title: '资产管理',
    menuCode: 'asset',
    modules: [
      {
        key: 'asset-spaces',
        title: '楼宇空间管理',
        menuCode: 'asset:spaces',
        route: '/assets/spaces',
        readCodes: ['building:view', 'floor:view', 'room:view'],
        writeCodes: [
          'building:create', 'building:update', 'building:delete',
          'floor:create', 'floor:update', 'floor:delete',
          'room:create', 'room:update', 'room:delete', 'room:lease', 'room:checkout'
        ]
      },
      {
        key: 'asset-equipment',
        title: '设备台账',
        menuCode: 'asset:equipment',
        route: '/assets/equipment',
        readCodes: ['asset:view'],
        writeCodes: ['asset:create', 'asset:update', 'asset:delete']
      },
      {
        key: 'asset-energy',
        title: '能耗管理',
        menuCode: 'asset:energy',
        route: '/assets/energy',
        readCodes: ['energy:meter:view', 'energy:view'],
        writeCodes: [
          'energy:meter:create', 'energy:meter:update', 'energy:meter:delete',
          'energy:create', 'energy:update', 'energy:reading:update', 'energy:reading:delete'
        ]
      },
      {
        key: 'asset-parking',
        title: '停车位管理',
        menuCode: 'asset:parking',
        route: '/assets/parking',
        readCodes: ['parking:view'],
        writeCodes: ['parking:create', 'parking:update', 'parking:delete']
      },
      {
        key: 'asset-meetings',
        title: '会议管理',
        menuCode: 'asset:meetings',
        route: '/assets/meetings',
        readCodes: ['meeting-room:view', 'meeting-booking:view'],
        writeCodes: [
          'meeting-room:create', 'meeting-room:update', 'meeting-room:delete',
          'meeting-booking:create', 'meeting-booking:confirm',
          'meeting-booking:cancel', 'meeting-booking:complete'
        ]
      }
    ]
  }),
  defineSection({
    key: 'investment-announcement',
    title: '招商公告',
    menuCode: 'investment-announcement',
    modules: [
      {
        key: 'investment-manage',
        title: '招商管理',
        menuCode: 'investment-announcement:manage',
        route: '/investment/manage',
        readCodes: ['investment-content:view', 'investment-listing:view'],
        writeCodes: [
          'investment-content:create', 'investment-content:update',
          'investment-content:delete', 'investment-content:publish',
          'investment-listing:create', 'investment-listing:update',
          'investment-listing:delete', 'investment-listing:publish', 'investment-listing:image'
        ]
      },
      {
        key: 'investment-leads',
        title: '招商线索',
        menuCode: 'investment-announcement:leads',
        route: '/investment/leads',
        readCodes: ['investment-lead:view'],
        writeCodes: ['investment-lead:follow', 'investment-lead:convert', 'investment-lead:delete']
      },
      {
        key: 'investment-announcements',
        title: '公告管理',
        menuCode: 'investment-announcement:announcements',
        route: '/investment/announcements',
        readCodes: ['announcement:view'],
        writeCodes: ['announcement:create', 'announcement:update', 'announcement:delete']
      }
    ]
  }),
  defineSection({
    key: 'tenant',
    title: '租户管理',
    menuCode: 'lease',
    modules: [
      {
        key: 'tenant-workspace',
        title: '租户档案',
        menuCode: 'lease:tenant-workspace',
        route: '/tenant/workspace',
        readCodes: ['tenant:view', 'contract:view', 'lease:view'],
        writeCodes: [
          'tenant:create', 'tenant:update', 'tenant:delete',
          'contract:create', 'contract:update', 'contract:delete',
          'contract:approve', 'contract:activate', 'contract:terminate', 'contract:cancel',
          'lease:create', 'lease:update', 'lease:delete', 'lease:checkin',
          'lease:checkout', 'lease:renew', 'lease:terminate'
        ]
      }
    ]
  }),
  defineSection({
    key: 'operation',
    title: '运营管理',
    menuCode: 'operation',
    modules: [
      {
        key: 'operation-workorders',
        title: '工单管理',
        menuCode: 'operation:workorder',
        route: '/workorders',
        readCodes: ['workorder:view'],
        writeCodes: [
          'workorder:create', 'workorder:update', 'workorder:assign',
          'workorder:complete', 'workorder:close'
        ]
      },
      {
        key: 'operation-inspections',
        title: '巡检管理',
        menuCode: 'operation:inspection',
        route: '/inspections',
        readCodes: ['inspection:view'],
        writeCodes: ['inspection:create', 'inspection:update', 'inspection:delete']
      },
      {
        key: 'operation-visitors',
        title: '访客管理',
        menuCode: 'operation:visitor',
        route: '/visitors',
        readCodes: ['visitor:view'],
        writeCodes: ['visitor:create', 'visitor:update']
      }
    ]
  }),
  defineSection({
    key: 'staff-mobile',
    title: '物业移动工作站',
    menuCode: '',
    permissionOnly: true,
    accessModule: {
      key: 'staff-mobile-access',
      title: '启用物业移动工作站',
      readCodes: ['staff-mobile:access']
    },
    modules: [
      {
        key: 'staff-mobile-workorders',
        title: '工单管理',
        readCodes: ['staff-mobile:workorder:view'],
        writeCodes: ['staff-mobile:workorder:write']
      },
      {
        key: 'staff-mobile-inspections',
        title: '巡检管理',
        readCodes: ['staff-mobile:inspection:view'],
        writeCodes: ['staff-mobile:inspection:write']
      },
      {
        key: 'staff-mobile-energy',
        title: '能耗管理',
        readCodes: ['staff-mobile:energy:view'],
        writeCodes: ['staff-mobile:energy:write']
      },
      {
        key: 'staff-mobile-meetings',
        title: '会议管理',
        readCodes: ['staff-mobile:meeting:view'],
        writeCodes: ['staff-mobile:meeting:write']
      },
      {
        key: 'staff-mobile-visitors',
        title: '访客管理',
        readCodes: ['staff-mobile:visitor:view'],
        writeCodes: ['staff-mobile:visitor:write']
      },
      {
        key: 'staff-mobile-investment',
        title: '招商看房',
        readCodes: ['staff-mobile:investment:view'],
        writeCodes: ['staff-mobile:investment:write']
      }
    ]
  }),
  defineSection({
    key: 'finance',
    title: '财务管理',
    menuCode: 'finance',
    modules: [
      {
        key: 'finance-bills',
        title: '账单管理',
        menuCode: 'finance:bill',
        route: '/bills',
        readCodes: ['bill:view'],
        writeCodes: [
          'bill:add', 'bill:update', 'bill:void', 'bill:audit', 'bill:pay',
          'finance:invoice:upload'
        ]
      },
      {
        key: 'finance-dashboard',
        title: '财务看板',
        menuCode: 'finance:dashboard',
        route: '/finance/dashboard',
        readCodes: ['finance:dashboard:view']
      }
    ]
  }),
  defineSection({
    key: 'finance-sensitive',
    title: '敏感财务操作',
    menuCode: '',
    permissionOnly: true,
    bulkAccess: false,
    modules: [
      {
        key: 'finance-bill-delete',
        title: '账单记录删除',
        readLabel: '草稿删除',
        writeLabel: '已审核删除',
        readCodes: ['bill:delete'],
        writeCodes: ['bill:delete-approved']
      }
    ]
  }),
  defineSection({
    key: 'ai',
    title: 'AI分析',
    menuCode: 'ai',
    modules: [
      {
        key: 'ai-daily-report',
        title: '运营日报',
        menuCode: 'ai:daily-report',
        route: '/ai/daily-report',
        readCodes: ['ai:view']
      }
    ]
  }),
  defineSection({
    key: 'system',
    title: '系统管理',
    menuCode: 'system',
    modules: [
      {
        key: 'system-users',
        title: '用户管理',
        menuCode: 'system:user',
        route: '/system/users',
        readCodes: ['system:user:view'],
        writeCodes: [
          'system:user:create', 'system:user:update', 'system:user:disable',
          'system:user:delete', 'system:user:assign-role', 'system:user:reset-password'
        ]
      },
      {
        key: 'system-roles',
        title: '角色管理',
        menuCode: 'system:role',
        route: '/system/roles',
        readCodes: ['system:role:view', 'system:menu:view', 'system:permission:view'],
        writeCodes: [
          'system:role:create', 'system:role:update', 'system:role:disable',
          'system:role:delete', 'system:role:assign-permission'
        ]
      },
      {
        key: 'system-mobile-users',
        title: '小程序用户',
        menuCode: 'system:mobile-user',
        route: '/system/mobile-users',
        readCodes: ['mobile:view'],
        writeCodes: ['mobile:update']
      }
    ]
  })
])

export const modulePermissionBundles = Object.freeze(
  modulePermissionSections.flatMap(section => [
    ...(section.accessModule ? [section.accessModule] : []),
    ...section.modules
  ])
)

const bundlesByKey = new Map(modulePermissionBundles.map(module => [module.key, module]))

export const modulePermissionMenuSections = Object.freeze(
  modulePermissionSections.filter(section => !section.permissionOnly).map(section => {
    if (section.standalone) {
      const module = section.modules[0]
      return Object.freeze({
        title: module.title,
        menuName: module.title,
        menuCode: module.menuCode,
        path: module.route
      })
    }
    return Object.freeze({
      title: section.title,
      menuName: section.title,
      menuCode: section.menuCode,
      children: Object.freeze(section.modules.map(module => Object.freeze({
        title: module.title,
        menuName: module.title,
        menuCode: module.menuCode,
        path: module.route
      })))
    })
  })
)

export function permissionCodeIsGranted(grantedCodes = [], requiredCode = '') {
  if (!requiredCode) return false
  const granted = grantedCodes instanceof Set ? grantedCodes : new Set(grantedCodes)
  if (granted.has('*') || granted.has(requiredCode)) return true
  const parts = String(requiredCode).split(':')
  for (let index = parts.length - 1; index > 0; index -= 1) {
    if (granted.has(`${parts.slice(0, index).join(':')}:*`)) return true
  }
  return false
}

export function getModulePermissionBundle(key) {
  return bundlesByKey.get(String(key || '')) || null
}

export function getModulePermissionBundleForPath(path) {
  const cleanPath = String(path || '').split('?')[0].split('#')[0]
  return modulePermissionBundles.find(module => module.route && (
    cleanPath === module.route || cleanPath.startsWith(`${module.route}/`)
  )) || null
}

export function getModulePermissionState(moduleOrKey, permissionCodes = []) {
  const module = typeof moduleOrKey === 'string'
    ? getModulePermissionBundle(moduleOrKey)
    : moduleOrKey
  if (!module) {
    return { read: false, write: false, readPartial: false, writePartial: false }
  }
  const granted = permissionCodes instanceof Set ? permissionCodes : new Set(permissionCodes)
  const readCount = module.readCodes.filter(code => permissionCodeIsGranted(permissionCodes, code)).length
  const writeCount = module.writeCodes.filter(code => module.writeRequiresExact
    ? granted.has(code)
    : permissionCodeIsGranted(granted, code)).length
  const read = module.readCodes.length > 0 && readCount === module.readCodes.length
  const write = module.writeCodes.length > 0 && read && writeCount === module.writeCodes.length
  return {
    read,
    write,
    readPartial: readCount > 0 && !read,
    writePartial: writeCount > 0 && !write,
    selectedCodeCount: readCount + writeCount,
    totalCodeCount: module.readCodes.length + module.writeCodes.length
  }
}

function currentAccess(defaultPermissions, defaultRoles) {
  let permissions = defaultPermissions
  let roles = defaultRoles
  if (permissions === null || permissions === undefined) {
    try {
      permissions = getCurrentPermissions()
    } catch (error) {
      permissions = []
    }
  }
  if (roles === null || roles === undefined) {
    if (defaultPermissions !== null && defaultPermissions !== undefined) {
      roles = []
    } else {
      try {
        roles = getCurrentRoles()
      } catch (error) {
        roles = []
      }
    }
  }
  return {
    permissions,
    roles
  }
}

export function canReadModule(key, permissionCodes = null, roleCodes = null) {
  const access = currentAccess(permissionCodes, roleCodes)
  if (access.roles.includes('SUPER_ADMIN')) return true
  return getModulePermissionState(key, access.permissions).read
}

export function canWriteModule(key, permissionCodes = null, roleCodes = null) {
  const access = currentAccess(permissionCodes, roleCodes)
  if (access.roles.includes('SUPER_ADMIN')) return true
  return getModulePermissionState(key, access.permissions).write
}
