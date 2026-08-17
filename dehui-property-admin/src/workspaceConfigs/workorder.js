import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverOrder = param => ({ mode: 'server', param })

export const workOrderTypeOptions = [
  { label: '维修报修', value: 'REPAIR' },
  { label: '巡检任务', value: 'PATROL' },
  { label: '保洁服务', value: 'CLEAN' },
  { label: '安保事件', value: 'SECURITY' }
]

export const workOrderCategoryOptions = {
  REPAIR: [
    { label: '水电维修', value: 'WATER_ELECTRIC' },
    { label: '空调暖通', value: 'HVAC' },
    { label: '电梯故障', value: 'ELEVATOR' },
    { label: '门禁网络', value: 'ACCESS_NETWORK' },
    { label: '土建装饰', value: 'CIVIL_DECORATION' },
    { label: '其他维修', value: 'OTHER_REPAIR' }
  ],
  PATROL: [
    { label: '设备巡检', value: 'EQUIPMENT_PATROL' },
    { label: '消防巡检', value: 'FIRE_PATROL' },
    { label: '公共区域巡查', value: 'PUBLIC_AREA_PATROL' },
    { label: '安全隐患', value: 'SAFETY_RISK' }
  ],
  CLEAN: [
    { label: '公共区域保洁', value: 'PUBLIC_CLEAN' },
    { label: '卫生间保洁', value: 'RESTROOM_CLEAN' },
    { label: '垃圾清运', value: 'WASTE_REMOVAL' },
    { label: '专项清洁', value: 'SPECIAL_CLEAN' }
  ],
  SECURITY: [
    { label: '门岗秩序', value: 'GATE_ORDER' },
    { label: '车辆秩序', value: 'VEHICLE_ORDER' },
    { label: '访客协助', value: 'VISITOR_SUPPORT' },
    { label: '突发事件', value: 'EMERGENCY' }
  ]
}

export const workOrderPriorityOptions = [
  { label: '低', value: 'LOW', tone: 'info' },
  { label: '中', value: 'MEDIUM', tone: 'primary' },
  { label: '普通', value: 'NORMAL', tone: 'primary' },
  { label: '高', value: 'HIGH', tone: 'warning' },
  { label: '紧急', value: 'URGENT', tone: 'danger' }
]

export const workOrderStatusOptions = [
  { label: '待处理', value: 'CREATED', tone: 'warning', className: 'status-submitted' },
  { label: '待处理', value: 'SUBMITTED', tone: 'warning', className: 'status-submitted' },
  { label: '已派单', value: 'ASSIGNED', tone: 'primary', className: 'status-assigned' },
  { label: '处理中', value: 'PROCESSING', tone: 'primary', className: 'status-processing' },
  { label: '已完成', value: 'COMPLETED', tone: 'success', className: 'status-completed' },
  { label: '已关闭', value: 'CLOSED', tone: 'info', className: 'status-closed' },
  { label: '已撤回', value: 'CANCELLED', tone: 'info', className: 'status-cancelled' }
]

const categoryFilterOptions = [
  { label: '维修报修', value: 'REPAIR' },
  { label: '维护检查', value: 'MAINTENANCE' },
  { label: '门禁权限', value: 'ACCESS' },
  ...Object.values(workOrderCategoryOptions).flat()
]

export function createWorkOrderWorkspaceConfig({
  currentUserId,
  handlerOptions = [],
  tenantOptions = [],
  reporterValue,
  tenantValue,
  handlerValue
} = {}) {
  const personalViews = Number.isFinite(currentUserId) && currentUserId > 0
    ? [{
        id: 'my-workorders',
        name: '我的工单',
        system: true,
        prefs: { filters: [{ key: 'handlerId', operator: 'equals', value: currentUserId }] }
      }]
    : []

  return defineWorkspaceConfig({
    id: 'workorders',
    title: '工单管理',
    subtitle: '统一跟踪受理、派单、处理与闭环进度',
    pageKey: '/workorders',
    storageKey: 'dhic-workorder-table-view-v2',
    defaultViewName: '全部工单',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: {
      view: 'workorder:view',
      create: 'workorder:create',
      update: 'workorder:update',
      assign: 'workorder:assign',
      complete: 'workorder:complete',
      close: 'workorder:close',
      export: 'workorder:view',
      generateBill: 'workorder:create'
    },
    primaryAction: { id: 'create', label: '新增工单', permission: 'create' },
    importExport: [{ id: 'export-current', kind: 'export', label: '导出当前查询', permission: 'export' }],
    batchActions: [
      { id: 'assign', label: '批量分配', permission: 'assign', icon: 'assign' },
      { id: 'export', label: '批量导出', permission: 'export', icon: 'download' },
      { id: 'close', label: '批量关闭', permission: 'close', icon: 'close', tone: 'danger' }
    ],
    statuses: workOrderStatusOptions,
    statusTransitions: workOrderStatusOptions.filter(item => item.value !== 'CREATED'),
    fields: [
      {
        key: 'keyword',
        label: '关键词',
        type: 'text',
        filterOnly: true,
        fixedOnly: true,
        query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'], serialize: value => String(value || '').trim() },
        valueGetter: row => [
          row.orderNumber, row.title, row.reporterName, row.companyName,
          row.location, row.description, row.remark
        ].filter(Boolean).join(' ')
      },
      { key: 'handlerId', label: '负责人', type: 'select', options: handlerOptions, filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'handlerId', operators: ['equals'] }, valueGetter: row => row.handlerId },
      { key: 'tenantId', label: '租户', type: 'select', options: tenantOptions, filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'tenantId', operators: ['equals'] }, valueGetter: row => row.tenantId },
      { key: 'orderNumber', label: '工单编号', type: 'text', sortable: true, required: true, minWidth: 150, query: { sort: serverOrder('orderNumber') } },
      { key: 'title', label: '工单标题', type: 'text', sortable: true, required: true, minWidth: 220, query: { sort: serverOrder('title') } },
      { key: 'orderType', label: '工单类型', type: 'select', options: workOrderTypeOptions, sortable: true, groupable: true, width: 130 },
      {
        key: 'category',
        label: '问题类别',
        type: 'select',
        options: categoryFilterOptions,
        sortable: true,
        groupable: true,
        minWidth: 140,
        query: {
          mode: 'server',
          param: 'category',
          operators: ['equals'],
          sort: serverOrder('category'),
          group: serverOrder('category')
        }
      },
      {
        key: 'status',
        label: '状态',
        type: 'status',
        options: workOrderStatusOptions,
        editable: true,
        sortable: true,
        groupable: true,
        width: 130,
        query: {
          mode: 'server',
          param: 'status',
          operators: ['equals'],
          sort: serverOrder('status'),
          group: serverOrder('status')
        }
      },
      { key: 'priority', label: '优先级', type: 'select', options: workOrderPriorityOptions, sortable: true, groupable: true, width: 110, query: { sort: serverOrder('priority'), group: serverOrder('priority') } },
      { key: 'reporter', label: '报修人', type: 'user', valueGetter: reporterValue, minWidth: 150 },
      { key: 'companyName', label: '报修公司', type: 'text', sortable: true, required: true, minWidth: 170, query: { sort: serverOrder('companyName') } },
      { key: 'reportedAt', label: '报修时间', type: 'date', format: 'datetime', sortable: true, required: true, width: 165, query: { sort: serverOrder('reportedAt') } },
      {
        key: 'tenant',
        label: '租户',
        type: 'relation',
        relation: { entityType: 'tenant', idField: 'id', labelField: 'label', multiple: false },
        valueGetter: tenantValue,
        minWidth: 170,
        sortable: true,
        groupable: true,
        query: { sort: serverOrder('tenant'), group: serverOrder('tenant') }
      },
      { key: 'handler', label: '负责人', type: 'user', valueGetter: handlerValue, minWidth: 160, sortable: true, groupable: true, query: { sort: serverOrder('handler'), group: serverOrder('handler') } },
      { key: 'location', label: '位置', type: 'text', sortable: true, minWidth: 160, query: { sort: serverOrder('location') } },
      {
        key: 'createdTime',
        label: '创建时间',
        type: 'date',
        format: 'datetime',
        sortable: true,
        width: 165,
        query: {
          mode: 'server',
          operators: ['equals', 'dateRange', 'today', 'thisWeek', 'thisMonth'],
          serialize: serializeWorkOrderDateFilter,
          sort: serverOrder('createdTime')
        }
      },
      { key: 'updatedTime', label: '最后更新', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('updatedTime') } },
      { key: 'remark', label: '备注', type: 'text', minWidth: 200 }
    ],
    views: [
      {
        id: 'pending-workorders',
        name: '待处理工单',
        system: true,
        prefs: { filters: [{ key: 'status', operator: 'equals', value: 'SUBMITTED' }] }
      },
      ...personalViews,
      {
        id: 'completed-workorders',
        name: '已完成工单',
        system: true,
        prefs: { filters: [{ key: 'status', operator: 'equals', value: 'COMPLETED' }] }
      }
    ],
    metrics: [
      { title: '工单总数', field: 'total', type: 'count', desc: '可见工单总量', tone: 'neutral' },
      { title: '筛选结果', field: 'filtered', type: 'count', desc: '当前条件命中', tone: 'primary' },
      { title: '待处理', field: 'pending', type: 'count', desc: '待安排负责人', tone: context => context.pending > 0 ? 'warning' : 'success' },
      { title: '已派单', field: 'assigned', type: 'count', desc: '等待开始处理', tone: context => context.assigned > 0 ? 'warning' : 'neutral' },
      { title: '处理中', field: 'processing', type: 'count', desc: '正在现场处理', tone: 'primary' },
      { title: '已完成/关闭', field: 'finished', type: 'count', desc: '已完成闭环', tone: 'success' }
    ],
    table: {
      structuralColumnCount: 3,
      groupColumnIndex: 1,
      defaultPrefs: {
        fixedColumns: { orderNumber: 'left' },
        rowDensity: 'compact'
      }
    }
  })
}

export { categoryFilterOptions as workOrderCategoryFilterOptions }

function serializeWorkOrderDateFilter(_value, filter) {
  const range = workOrderDateRange(filter)
  return range ? { startTime: range[0], endTime: range[1] } : {}
}

function workOrderDateRange(filter) {
  if (filter.operator === 'dateRange' && Array.isArray(filter.value) && filter.value.length === 2) return filter.value
  if (filter.operator === 'equals' && filter.value) return [filter.value, filter.value]

  const today = new Date()
  if (filter.operator === 'today') {
    const value = localDateText(today)
    return [value, value]
  }
  if (filter.operator === 'thisWeek') {
    const day = today.getDay() || 7
    const start = new Date(today)
    start.setDate(today.getDate() - day + 1)
    const end = new Date(start)
    end.setDate(start.getDate() + 6)
    return [localDateText(start), localDateText(end)]
  }
  if (filter.operator === 'thisMonth') {
    const start = new Date(today.getFullYear(), today.getMonth(), 1)
    const end = new Date(today.getFullYear(), today.getMonth() + 1, 0)
    return [localDateText(start), localDateText(end)]
  }
  return null
}

function localDateText(date) {
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
}
