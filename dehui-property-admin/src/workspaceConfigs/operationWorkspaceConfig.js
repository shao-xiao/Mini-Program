import { reactive } from 'vue'
import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverFilter = (param, operators = ['equals']) => ({ mode: 'server', param, operators })
const serverOrder = param => ({ mode: 'server', param })

export const visitorStatusOptions = [
  { label: '待审核', value: 'PENDING', tone: 'warning' },
  { label: '审核通过', value: 'REGISTERED', tone: 'success' },
  { label: '已驳回', value: 'REJECTED', tone: 'danger' },
  { label: '已进入', value: 'ENTERED', tone: 'success' },
  { label: '已离开', value: 'LEFT', tone: 'warning' },
  { label: '已取消', value: 'CANCELLED', tone: 'danger' }
]

export const inspectionTypeOptions = reactive([])

export const inspectionResultOptions = [
  { label: '正常', value: 'NORMAL', tone: 'success' },
  { label: '异常', value: 'ABNORMAL', tone: 'danger' },
  { label: '待处理', value: 'PENDING', tone: 'warning' }
]

export const inspectionStatusOptions = [
  { label: '待处理', value: 'OPEN', tone: 'warning' },
  { label: '已关闭', value: 'CLOSED', tone: 'success' }
]

export const announcementTypeOptions = [
  { label: '通知', value: 'NOTICE' },
  { label: '园区活动', value: 'ACTIVITY' },
  { label: '物业通知', value: 'PROPERTY' },
  { label: '运营提醒', value: 'REMINDER' }
]

export const announcementStatusOptions = [
  { label: '草稿', value: 'DRAFT', tone: 'info' },
  { label: '已发布', value: 'PUBLISHED', tone: 'success' },
  { label: '已下架', value: 'DISABLED', tone: 'info' },
  { label: '已归档', value: 'ARCHIVED', tone: 'warning' }
]

export const visitorWorkspaceConfig = defineWorkspaceConfig({
  id: 'visitors',
  title: '访客管理',
  subtitle: '支持访客审批、来访流程和离场管理，提供完整访问记录与状态跟踪',
  pageKey: '/visitors',
  storageKey: 'dhic-visitor-table-view-v1',
  defaultViewName: '默认访客',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'visitor:view',
    create: 'visitor:create',
    update: 'visitor:update'
  },
  primaryAction: { id: 'create', label: '访客登记', permission: 'create' },
  fields: [
    { key: 'code', label: '访客编码', type: 'text', sortable: true, minWidth: 145 },
    { key: 'visitorName', label: '访客姓名', type: 'text', required: true, sortable: true, minWidth: 120 },
    { key: 'visitorPhone', label: '手机号码', type: 'phone', minWidth: 135 },
    { key: 'carPlateNo', label: '车牌号', type: 'text', sortable: true, minWidth: 120 },
    { key: 'tenantName', label: '租户', type: 'text', sortable: true, groupable: true, minWidth: 155 },
    { key: 'visitTime', label: '访问时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    {
      key: 'status', label: '状态', type: 'status', options: visitorStatusOptions,
      sortable: true, groupable: true, width: 105,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    },
    { key: 'remark', label: '备注', type: 'text', minWidth: 180 }
  ],
  views: [
    { id: 'pending-review-visitors', name: '待审核', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'PENDING' }] } },
    { id: 'waiting-visitors', name: '待到访', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'REGISTERED' }] } },
    { id: 'entered-visitors', name: '已进入', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'ENTERED' }] } }
  ],
  metrics: [
    { title: '访客总数', field: 'total', type: 'count', desc: '当前返回总访客数量', tone: 'neutral' },
    { title: '当前列表', field: 'filtered', type: 'count', desc: '当前列表记录数', tone: 'primary' },
    { title: '待审核', field: 'pendingReview', type: 'count', desc: '等待物业审核的预约', tone: 'warning' },
    { title: '审核通过', field: 'approved', type: 'count', desc: '已通过且等待到访', tone: 'primary' },
    { title: '已进入', field: 'entered', type: 'count', desc: '当前已进入访客数量', tone: 'success' },
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: { fixedColumns: { visitorName: 'left' }, rowDensity: 'compact' }
  }
})

export const inspectionWorkspaceConfig = defineWorkspaceConfig({
  id: 'inspections',
  title: '巡检记录',
  subtitle: '用于记录巡检对象、责任人和处理状态的一体化巡检台账',
  pageKey: '/inspections',
  storageKey: 'dhic-inspection-table-view-v1',
  defaultViewName: '默认巡检',
  query: { dataMode: 'server', filterLogics: ['and'] },
  permissions: {
    view: 'inspection:view',
    create: 'inspection:create',
    update: 'inspection:update',
    delete: 'inspection:delete'
  },
  primaryAction: { id: 'create', label: '新增巡检', permission: 'create' },
  fields: [
    {
      key: 'inspectionDate', label: '巡检日期', type: 'date', sortable: true, groupable: true, width: 125,
      query: {
        filter: { mode: 'server', operators: ['equals', 'dateRange', 'today', 'thisWeek', 'thisMonth'], serialize: serializeInspectionDateFilter },
        sort: serverOrder('inspectionDate'),
        group: serverOrder('inspectionDate')
      }
    },
    {
      key: 'inspector', label: '巡检人', type: 'text', sortable: true, groupable: true, minWidth: 115,
      query: { filter: serverFilter('inspector', ['contains', 'equals']), sort: serverOrder('inspector'), group: serverOrder('inspector') }
    },
    {
      key: 'inspectionType', label: '巡检类型', type: 'select', options: () => inspectionTypeOptions,
      sortable: true, groupable: true, width: 125,
      query: { filter: serverFilter('inspectionType'), sort: serverOrder('inspectionType'), group: serverOrder('inspectionType') }
    },
    {
      key: 'area', label: '区域/点位', type: 'text', sortable: true, groupable: true, minWidth: 130,
      query: { sort: serverOrder('area'), group: serverOrder('area') }
    },
    {
      key: 'target', label: '巡检对象', type: 'text', sortable: true, groupable: true, minWidth: 160,
      query: { filter: serverFilter('target', ['contains', 'equals']), sort: serverOrder('target'), group: serverOrder('target') }
    },
    {
      key: 'result', label: '结果', type: 'status', options: inspectionResultOptions,
      sortable: true, groupable: true, width: 100,
      query: { filter: serverFilter('result'), sort: serverOrder('result'), group: serverOrder('result') }
    },
    { key: 'problemDescription', label: '问题描述', type: 'text', minWidth: 180 },
    { key: 'actionTaken', label: '处理措施', type: 'text', minWidth: 170 },
    {
      key: 'status', label: '状态', type: 'status', options: inspectionStatusOptions,
      sortable: true, groupable: true, width: 105,
      query: { filter: serverFilter('status'), sort: serverOrder('status'), group: serverOrder('status') }
    },
    { key: 'remark', label: '备注', type: 'text', minWidth: 150 },
    { key: 'createdAt', label: '创建时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('createdAt') } },
    { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('updatedAt') } }
  ],
  views: [
    { id: 'abnormal-inspections', name: '异常巡检', system: true, prefs: { filters: [{ key: 'result', operator: 'equals', value: 'ABNORMAL' }] } },
    { id: 'open-inspections', name: '待处理', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'OPEN' }] } }
  ],
  metrics: [
    { title: '巡检总数', field: 'totalCount', type: 'count', desc: '当前所有巡检记录', tone: 'neutral' },
    { title: '正常巡检', field: 'normalCount', type: 'count', desc: '结果为正常', tone: 'success' },
    { title: '异常巡检', field: 'abnormalCount', type: 'count', desc: '待处理异常巡检数量', tone: 'danger' },
    { title: '待处理', field: 'openCount', type: 'count', desc: '当前待处理数量', tone: 'warning' },
    { title: '已关闭', field: 'closedCount', type: 'count', desc: '已关闭巡检记录', tone: 'success' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: { fixedColumns: { inspectionDate: 'left' }, rowDensity: 'compact' }
  }
})

export const announcementWorkspaceConfig = defineWorkspaceConfig({
  id: 'announcements',
  title: '公告管理',
  subtitle: '提供通知公告内容发布、首页展示和状态管理的公告中心',
  pageKey: '/announcements',
  storageKey: 'dhic-announcement-table-view-v1',
  defaultViewName: '默认公告',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'announcement:view',
    create: 'announcement:create',
    update: 'announcement:update',
    delete: 'announcement:delete'
  },
  primaryAction: { id: 'create', label: '新增公告', permission: 'create' },
  fields: [
    { key: 'code', label: '公告编码', type: 'text', sortable: true, minWidth: 145 },
    { key: 'title', label: '标题', type: 'text', required: true, sortable: true, minWidth: 190 },
    { key: 'summary', label: '摘要', type: 'text', minWidth: 200 },
    { key: 'type', label: '类型', type: 'select', options: announcementTypeOptions, sortable: true, groupable: true, width: 125 },
    { key: 'showOnHome', label: '首页展示', type: 'checkbox', sortable: true, groupable: true, width: 105 },
    { key: 'carouselSort', label: '轮播排序', type: 'number', sortable: true, width: 95 },
    {
      key: 'status', label: '状态', type: 'status', options: announcementStatusOptions,
      sortable: true, groupable: true, width: 105,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    },
    { key: 'publishTime', label: '发布时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'effectiveAt', label: '生效时间', type: 'date', format: 'datetime', width: 165 },
    { key: 'expiresAt', label: '失效时间', type: 'date', format: 'datetime', width: 165 },
    { key: 'createdTime', label: '创建时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'content', label: '内容', type: 'text', minWidth: 260 }
  ],
  views: [
    { id: 'published-announcements', name: '已发布', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'PUBLISHED' }] } },
    { id: 'draft-announcements', name: '草稿', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'DRAFT' }] } }
  ],
  metrics: [
    { title: '公告总数', field: 'total', type: 'count', desc: '当前公告总数', tone: 'neutral' },
    { title: '当前列表', field: 'filtered', type: 'count', desc: '当前列表中的公告数', tone: 'primary' },
    { title: '已发布', field: 'published', type: 'count', desc: '当前已发布公告', tone: 'success' },
    { title: '草稿', field: 'draft', type: 'count', desc: '待发布草稿', tone: 'warning' },
    { title: '首页展示', field: 'home', type: 'count', desc: '首页焦点展示公告数量', tone: 'primary' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: { fixedColumns: { title: 'left' }, rowDensity: 'compact' }
  }
})

export function normalizeVisitorWorkspaceRecord(record = {}, tenantById = new Map()) {
  return {
    ...record,
    tenantName: tenantById.get(Number(record.tenantId)) || (record.tenantId ? `租户 #${record.tenantId}` : '')
  }
}

export function buildVisitorMetricSnapshot(rows = [], visibleRows = rows) {
  const records = Array.isArray(rows) ? rows : []
  const visible = Array.isArray(visibleRows) ? visibleRows : []
  return {
    total: records.length,
    filtered: visible.length,
    pendingReview: records.filter(row => row.status === 'PENDING').length,
    approved: records.filter(row => row.status === 'REGISTERED').length,
    entered: records.filter(row => row.status === 'ENTERED').length,
  }
}

export function normalizeAnnouncementWorkspaceRecord(record = {}) {
  return { ...record, showOnHome: booleanValue(record.showOnHome) }
}

export function buildAnnouncementMetricSnapshot(rows = [], visibleRows = rows) {
  const records = Array.isArray(rows) ? rows : []
  const visible = Array.isArray(visibleRows) ? visibleRows : []
  return {
    total: records.length,
    filtered: visible.length,
    published: records.filter(row => row.status === 'PUBLISHED').length,
    draft: records.filter(row => row.status === 'DRAFT').length,
    home: records.filter(row => booleanValue(row.showOnHome)).length
  }
}

function serializeInspectionDateFilter(_value, filter) {
  const range = inspectionDateRange(filter)
  return range ? { startDate: range[0], endDate: range[1] } : {}
}

function inspectionDateRange(filter) {
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

function booleanValue(value) {
  return value === true || value === 1 || value === '1' || value === 'true'
}
