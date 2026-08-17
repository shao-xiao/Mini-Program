import { defineWorkspaceConfig } from './workspaceConfig.js'

const clientQuery = { mode: 'client' }
const serverFilter = (param, operators = ['equals']) => ({ mode: 'server', param, operators })
const serverOrder = param => ({ mode: 'server', param })

export const systemStatusOptions = [
  { label: '启用', value: 'ENABLED', tone: 'success' },
  { label: '禁用', value: 'DISABLED', tone: 'info' }
]

export const mobileUserTypeOptions = [
  { label: '普通用户', value: 'PUBLIC', tone: 'info' },
  { label: '租户', value: 'TENANT', tone: 'success' },
  { label: '物业员工', value: 'STAFF', tone: 'warning' }
]

export function createSystemUserWorkspaceConfig({ roleOptions = [] } = {}) {
  return defineWorkspaceConfig({
    id: 'system-users',
    title: '用户管理',
    subtitle: '只维护物业内部员工、后台登录和移动工作台资格',
    pageKey: '/system/users',
    storageKey: 'dhic-system-user-table-view-v2',
    defaultViewName: '全部用户',
    query: { dataMode: 'client', filterLogics: ['and', 'or'] },
    permissions: {
      view: 'system:user:view',
      create: 'system:user:create',
      update: 'system:user:update',
      disable: 'system:user:disable',
      delete: 'system:user:delete',
      assignRole: 'system:user:assign-role',
      resetPassword: 'system:user:reset-password'
    },
    primaryAction: { id: 'create', label: '新增用户', permission: 'create' },
    fields: [
      { key: 'code', label: '用户编号', type: 'text', sortable: true, minWidth: 140, query: clientQuery },
      { key: 'realName', label: '姓名', type: 'text', required: true, sortable: true, minWidth: 120, query: clientQuery },
      { key: 'username', label: '用户名', type: 'text', sortable: true, minWidth: 130, query: clientQuery },
      { key: 'phone', label: '手机号', type: 'text', sortable: true, minWidth: 140, query: clientQuery },
      {
        key: 'primaryRoleName',
        label: '主要角色',
        type: 'select',
        options: roleOptions,
        sortable: true,
        minWidth: 160,
        query: clientQuery
      },
      {
        key: 'status',
        label: '状态',
        type: 'status',
        options: systemStatusOptions,
        sortable: true,
        groupable: true,
        width: 100,
        query: clientQuery
      },
      { key: 'webLoginEnabled', label: '后台登录', type: 'checkbox', sortable: true, width: 100, query: clientQuery },
      { key: 'mobileEnabled', label: '移动工作台', type: 'checkbox', sortable: true, width: 110, query: clientQuery },
      { key: 'mobileBound', label: '小程序绑定', type: 'checkbox', sortable: true, width: 110, query: clientQuery },
      { key: 'lastLoginAt', label: '最后登录时间', type: 'date', sortable: true, width: 165, query: clientQuery },
      { key: 'remark', label: '备注', type: 'text', minWidth: 180, query: clientQuery }
    ],
    views: [
      { id: 'enabled-system-users', name: '启用用户', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'ENABLED' }] } },
      { id: 'disabled-system-users', name: '禁用用户', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'DISABLED' }] } },
      { id: 'unassigned-system-users', name: '未分配角色', system: true, prefs: { filters: [{ key: 'primaryRoleName', operator: 'isEmpty', value: '' }] } }
    ],
    metrics: [
      { title: '用户总数', field: 'totalCount', type: 'count', desc: '后台账号总数', tone: 'neutral' },
      { title: '当前视图', field: 'visibleCount', type: 'count', desc: '符合当前条件', tone: 'primary' },
      { title: '启用用户', field: 'enabledCount', type: 'count', desc: '可正常登录', tone: 'success' },
      { title: '禁用用户', field: 'disabledCount', type: 'count', desc: '不可登录', tone: 'warning' },
      { title: '角色配置', field: 'roleCount', type: 'count', desc: '可配置角色数量', tone: 'neutral' }
    ],
    table: {
      structuralColumnCount: 2,
      groupColumnIndex: 0,
      defaultPrefs: { fixedColumns: { username: 'left' }, rowDensity: 'compact' }
    }
  })
}

export function createSystemRoleWorkspaceConfig() {
  return defineWorkspaceConfig({
    id: 'system-roles',
    title: '角色管理',
    subtitle: '后台业务权限与物业移动工作站权限分开配置',
    pageKey: '/system/roles',
    storageKey: 'dhic-system-role-table-view-v2',
    defaultViewName: '全部角色',
    query: { dataMode: 'client', filterLogics: ['and', 'or'] },
    permissions: {
      view: 'system:role:view',
      create: 'system:role:create',
      update: 'system:role:update',
      disable: 'system:role:disable',
      delete: 'system:role:delete',
      assignPermission: 'system:role:assign-permission'
    },
    primaryAction: { id: 'create', label: '新增角色', permission: 'create' },
    fields: [
      { key: 'roleCode', label: '角色编码', type: 'text', required: true, sortable: true, minWidth: 180, query: clientQuery },
      { key: 'roleName', label: '角色名称', type: 'text', required: true, sortable: true, minWidth: 180, query: clientQuery },
      { key: 'roleType', label: '角色类型', type: 'text', sortable: true, groupable: true, width: 130, query: clientQuery },
      { key: 'description', label: '角色说明', type: 'text', minWidth: 260, query: clientQuery },
      {
        key: 'status',
        label: '状态',
        type: 'status',
        options: systemStatusOptions,
        sortable: true,
        groupable: true,
        width: 100,
        query: clientQuery
      },
      { key: 'createdTime', label: '创建时间', type: 'date', sortable: true, width: 165, query: clientQuery },
      { key: 'updatedTime', label: '更新时间', type: 'date', sortable: true, width: 165, query: clientQuery }
    ],
    views: [
      { id: 'enabled-system-roles', name: '启用角色', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'ENABLED' }] } },
      { id: 'business-system-roles', name: '业务角色', system: true, prefs: { filters: [{ key: 'roleType', operator: 'equals', value: 'BUSINESS' }] } }
    ],
    metrics: [
      { title: '角色总数', field: 'totalCount', type: 'count', desc: '全部角色', tone: 'neutral' },
      { title: '当前视图', field: 'visibleCount', type: 'count', desc: '符合当前条件', tone: 'primary' },
      { title: '启用角色', field: 'enabledCount', type: 'count', desc: '可分配使用', tone: 'success' },
      { title: '停用角色', field: 'disabledCount', type: 'count', desc: '不可分配', tone: 'warning' },
      { title: '权限项', field: 'permissionCount', type: 'count', desc: '可配置权限', tone: 'neutral' }
    ],
    table: {
      structuralColumnCount: 2,
      groupColumnIndex: 0,
      defaultPrefs: { fixedColumns: { roleCode: 'left' }, rowDensity: 'compact' }
    }
  })
}

export function createMobileUserWorkspaceConfig() {
  return defineWorkspaceConfig({
    id: 'system-mobile-users',
    title: '小程序用户',
    subtitle: '查看微信验证手机号与合法业务身份绑定，不创建第二套账号或角色',
    pageKey: '/system/mobile-users',
    storageKey: 'dhic-system-mobile-user-table-view-v2',
    defaultViewName: '全部小程序用户',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: { view: 'mobile:view', update: 'mobile:update' },
    fields: [
      {
        key: 'keyword', label: '关键词', type: 'text', filterOnly: true, fixedOnly: true,
        query: serverFilter('keyword', ['contains', 'equals'])
      },
      {
        key: 'code', label: '用户编号', type: 'text', sortable: true, minWidth: 145,
        query: { filter: serverFilter('code', ['contains', 'equals']), sort: serverOrder('code') }
      },
      {
        key: 'verifiedPhone', label: '验证手机号', type: 'text', sortable: false, minWidth: 145,
        query: { filter: serverFilter('phone', ['contains', 'equals']), sort: serverOrder('phone') }
      },
      {
        key: 'nickname', label: '微信昵称', type: 'text', sortable: true, minWidth: 145,
        query: { filter: serverFilter('nickname', ['contains', 'equals']), sort: serverOrder('nickname') }
      },
      {
        key: 'realName', label: '姓名', type: 'text', sortable: true, minWidth: 130,
        query: { filter: serverFilter('realName', ['contains', 'equals']), sort: serverOrder('realName') }
      },
      {
        key: 'userType', label: '身份', type: 'status', options: mobileUserTypeOptions, sortable: true, groupable: true, width: 115,
        query: { filter: serverFilter('userType'), sort: serverOrder('userType'), group: serverOrder('userType') }
      },
      { key: 'bindingState', label: '绑定状态', type: 'text', sortable: false, width: 130 },
      { key: 'contactRole', label: '联系人类型', type: 'text', sortable: false, width: 120 },
      { key: 'availableIdentityCount', label: '可用身份数', type: 'number', sortable: false, width: 110 },
      {
        key: 'tenantName', label: '绑定租户公司', type: 'text', sortable: true, groupable: true, minWidth: 190,
        query: { filter: serverFilter('tenantName', ['contains', 'equals']), sort: serverOrder('tenantName'), group: serverOrder('tenantName') }
      },
      {
        key: 'tenantContactName', label: '绑定联系人', type: 'text', sortable: true, minWidth: 170,
        query: { filter: serverFilter('tenantContactName', ['contains', 'equals']), sort: serverOrder('tenantContactName') }
      },
      {
        key: 'boundSysRealName', label: '内部员工', type: 'text', sortable: true, minWidth: 150,
        query: { filter: serverFilter('boundSysRealName', ['contains', 'equals']), sort: serverOrder('boundSysRealName') }
      },
      { key: 'backendRoleSummary', label: '后台角色摘要', type: 'text', sortable: false, minWidth: 170 },
      { key: 'mobileWorkbenchState', label: '移动工作台', type: 'text', sortable: false, width: 120 },
      {
        key: 'status', label: '状态', type: 'status', options: systemStatusOptions, sortable: true, groupable: true, width: 100,
        query: { filter: serverFilter('status'), sort: serverOrder('status'), group: serverOrder('status') }
      },
      { key: 'createdAt', label: '创建时间', type: 'date', sortable: true, width: 165, filterable: false, query: { sort: serverOrder('createdAt') } },
      { key: 'lastLoginAt', label: '最近登录', type: 'date', sortable: false, width: 165, filterable: false },
      { key: 'updatedAt', label: '更新时间', type: 'date', sortable: true, width: 165, filterable: false, query: { sort: serverOrder('updatedAt') } }
    ],
    views: [
      { id: 'tenant-mobile-users', name: '租户用户', system: true, prefs: { filters: [{ key: 'userType', operator: 'equals', value: 'TENANT' }] } },
      { id: 'internal-mobile-users', name: '物业员工', system: true, prefs: { filters: [{ key: 'userType', operator: 'equals', value: 'STAFF' }] } },
      { id: 'disabled-mobile-users', name: '停用用户', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'DISABLED' }] } }
    ],
    metrics: [
      { title: '用户总数', field: 'totalCount', type: 'count', desc: '当前服务端查询', tone: 'neutral' },
      { title: '当前页', field: 'currentPageCount', type: 'count', desc: '当前页已加载记录', tone: 'primary' }
    ],
    table: {
      structuralColumnCount: 2,
      groupColumnIndex: 0,
      defaultPrefs: { fixedColumns: { phone: 'left' }, rowDensity: 'compact', pageSize: 20 }
    }
  })
}
