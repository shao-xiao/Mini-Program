import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverFilter = (param, operators = ['equals']) => ({ mode: 'server', param, operators })
const serverOrder = param => ({ mode: 'server', param })

export const parkingSpaceStatusOptions = [
  { label: '空闲', value: 'AVAILABLE', tone: 'success' },
  { label: '占用', value: 'OCCUPIED', tone: 'danger' },
  { label: '维护中', value: 'MAINTENANCE', tone: 'warning' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const parkingSpaceTypeOptions = [
  { label: '普通车位', value: 'NORMAL' },
  { label: '货车车位', value: 'TRUCK' },
  { label: '专用车位', value: 'DEDICATED' },
  { label: '月租车位', value: 'MONTHLY' },
  { label: '临停车位', value: 'TEMPORARY' },
  { label: '充电车位（快充）', value: 'CHARGING_FAST' },
  { label: '充电车位（慢充）', value: 'CHARGING_SLOW' },
  { label: '机械车位', value: 'MECHANICAL' }
]

export const meetingRoomStatusOptions = [
  { label: '可预约', value: 'AVAILABLE', tone: 'success' },
  { label: '可预约', value: 'ENABLED', tone: 'success' },
  { label: '维护中', value: 'MAINTENANCE', tone: 'warning' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const meetingBookingStatusOptions = [
  { label: '待审核', value: 'PENDING', tone: 'warning' },
  { label: '审核通过', value: 'CONFIRMED', tone: 'success' },
  { label: '已驳回', value: 'REJECTED', tone: 'danger' },
  { label: '已取消', value: 'CANCELLED', tone: 'info' },
  { label: '已完成', value: 'COMPLETED', tone: 'primary' },
  { label: '待审核', value: 'BOOKED', tone: 'warning' }
]

export const meetingBookingSourceOptions = [
  { label: '内部员工', value: 'INTERNAL', tone: 'primary' },
  { label: '租户', value: 'TENANT', tone: 'success' }
]

export const parkingSpaceWorkspaceConfig = defineWorkspaceConfig({
  id: 'parking-spaces',
  title: '车位管理',
  subtitle: '维护车位资源、当前绑定关系和停车月费',
  pageKey: '/parking/spaces',
  storageKey: 'dhic-parking-space-table-view-v2',
  defaultViewName: '全部车位',
  query: { dataMode: 'server', filterLogics: ['and'] },
  permissions: {
    view: 'parking:view',
    create: 'parking:create',
    update: 'parking:update',
    delete: 'parking:delete'
  },
  customFields: {
    enabled: true,
    workspaceKey: 'parking-spaces',
    managePermission: 'custom-field:manage',
    valueUpdatePermission: 'parking:update'
  },
  primaryAction: { id: 'create', label: '新增车位', permission: 'create' },
  fields: [
    {
      key: 'spaceNo', label: '车位编号', type: 'text', required: true, sortable: true, minWidth: 135,
      query: { filter: serverFilter('spaceNo', ['contains']), sort: serverOrder('spaceNo') }
    },
    {
      key: 'area', label: '区域', type: 'select', options: ['A', 'B', 'C', 'D'].map(value => ({ label: value, value })),
      sortable: true, groupable: true, width: 90,
      query: { filter: serverFilter('area'), sort: serverOrder('area'), group: serverOrder('area') }
    },
    {
      key: 'floor', label: '楼层/位置', type: 'select', options: ['B1', 'B2', '地面'].map(value => ({ label: value, value })),
      sortable: true, groupable: true, width: 115,
      query: { filter: serverFilter('floor'), sort: serverOrder('floor'), group: serverOrder('floor') }
    },
    {
      key: 'type', label: '车位类型', type: 'select', options: parkingSpaceTypeOptions,
      sortable: true, groupable: true, minWidth: 135,
      query: { filter: serverFilter('type'), sort: serverOrder('type'), group: serverOrder('type') }
    },
    {
      key: 'monthlyFee', label: '停车月费', type: 'money', currency: 'CNY', decimals: 2,
      sortable: true, width: 125, align: 'right',
      query: {
        filter: {
          mode: 'server',
          operators: ['equals', 'greaterThanOrEqual', 'lessThanOrEqual', 'between'],
          serialize: serializeParkingMonthlyFee
        },
        sort: serverOrder('monthlyFee')
      }
    },
    { key: 'monthlyRate', label: '月租标准', type: 'money', currency: 'CNY', decimals: 2, width: 120 },
    {
      key: 'partyNameSnapshot', label: '使用方', type: 'text', sortable: true, groupable: true, minWidth: 155,
      query: {
        filter: serverFilter('partyName', ['contains']),
        sort: serverOrder('partyNameSnapshot'),
        group: serverOrder('partyNameSnapshot')
      }
    },
    {
      key: 'plateNo', label: '车牌号', type: 'text', sortable: true, minWidth: 125,
      query: { filter: serverFilter('plateNo', ['contains']), sort: serverOrder('plateNo') }
    },
    {
      key: 'status', label: '状态', type: 'status', options: parkingSpaceStatusOptions,
      sortable: true, groupable: true, width: 105,
      query: { filter: serverFilter('status'), sort: serverOrder('status'), group: serverOrder('status') }
    },
    { key: 'remark', label: '备注', type: 'text', minWidth: 180, filterable: false }
  ],
  views: [
    { id: 'available-parking-spaces', name: '空闲车位', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'AVAILABLE' }] } },
    { id: 'occupied-parking-spaces', name: '已占用车位', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'OCCUPIED' }] } }
  ],
  metrics: [
    { title: '车位总数', field: 'totalCount', type: 'count', desc: '当前筛选范围', tone: 'neutral' },
    { title: '空闲车位', field: 'availableCount', type: 'count', desc: '可绑定使用方', tone: 'success' },
    { title: '已占用', field: 'occupiedCount', type: 'count', desc: '已有有效绑定', tone: 'primary' },
    { title: '维护/停用', field: 'unavailableCount', type: 'count', desc: '暂不可分配', tone: 'warning' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: { fixedColumns: { spaceNo: 'left' }, rowDensity: 'compact', pageSize: 20 }
  }
})

export const parkingAssignmentWorkspaceConfig = defineWorkspaceConfig({
  id: 'parking-assignments',
  title: '绑定记录',
  subtitle: '查看车位、租户、车辆、有效期和月费历史',
  pageKey: '/assets/parking',
  storageKey: 'dhic-parking-assignment-table-view-v1',
  defaultViewName: '全部绑定记录',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: { view: 'parking:view', update: 'parking:update' },
  fields: [
    { key: 'assignmentCode', label: '绑定编号', type: 'text', sortable: true, minWidth: 145 },
    { key: 'spaceNo', label: '车位编号', type: 'text', sortable: true, groupable: true, minWidth: 125 },
    { key: 'tenantName', label: '绑定租户', type: 'text', sortable: true, groupable: true, minWidth: 160 },
    { key: 'contactName', label: '绑定联系人', type: 'text', sortable: true, minWidth: 130 },
    { key: 'plateNo', label: '车牌号', type: 'text', sortable: true, minWidth: 125 },
    { key: 'vehicleType', label: '车辆类型', type: 'text', sortable: true, width: 110 },
    { key: 'startDate', label: '生效日期', type: 'date', sortable: true, width: 120 },
    { key: 'endDate', label: '失效日期', type: 'date', sortable: true, width: 120 },
    { key: 'unitPriceSnapshot', label: '收费标准快照', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 135, align: 'right' },
    {
      key: 'status', label: '状态', type: 'status', sortable: true, groupable: true, width: 105,
      options: [
        { label: '生效中', value: 'ACTIVE', tone: 'success' },
        { label: '生效中', value: 'ENABLED', tone: 'success' },
        { label: '已解除', value: 'RELEASED', tone: 'info' },
        { label: '已失效', value: 'DISABLED', tone: 'info' }
      ]
    },
    { key: 'remark', label: '备注', type: 'text', minWidth: 180 }
  ],
  views: [
    { id: 'active-parking-assignments', name: '当前绑定', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'ACTIVE' }] } },
    { id: 'released-parking-assignments', name: '已解除', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'RELEASED' }] } }
  ],
  table: {
    structuralColumnCount: 1,
    groupColumnIndex: 0,
    defaultPrefs: { fixedColumns: { assignmentCode: 'left' }, rowDensity: 'compact' }
  }
})

export const meetingRoomWorkspaceConfig = defineWorkspaceConfig({
  id: 'meeting-rooms',
  title: '会议室管理',
  subtitle: '维护会议室档案、开放时间、收费规则和可预约状态',
  pageKey: '/meetings/rooms',
  storageKey: 'dhic-meeting-room-table-view-v2',
  defaultViewName: '全部会议室',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'meeting-room:view',
    create: 'meeting-room:create',
    update: 'meeting-room:update',
    delete: 'meeting-room:delete'
  },
  primaryAction: { id: 'create', label: '新增会议室', permission: 'create' },
  fields: [
    { key: 'code', label: '会议室编号', type: 'text', sortable: true, minWidth: 140 },
    { key: 'name', label: '会议室名称', type: 'text', required: true, sortable: true, minWidth: 160 },
    { key: 'capacity', label: '容量', type: 'number', sortable: true, width: 105, align: 'right' },
    { key: 'location', label: '位置', type: 'text', sortable: true, groupable: true, minWidth: 160 },
    { key: 'facilities', label: '设备设施', type: 'text', minWidth: 180 },
    { key: 'openTime', label: '开放时间', type: 'text', width: 110 },
    { key: 'closeTime', label: '关闭时间', type: 'text', width: 110 },
    { key: 'workdayHourlyRate', label: '当前规则单价', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 135, align: 'right' },
    { key: 'status', label: '状态', type: 'status', options: meetingRoomStatusOptions, sortable: true, groupable: true, width: 105 },
    { key: 'remark', label: '备注', type: 'text', minWidth: 190 }
  ],
  views: [
    { id: 'available-meeting-rooms', name: '可预约会议室', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'AVAILABLE' }] } },
    { id: 'maintenance-meeting-rooms', name: '维护中会议室', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'MAINTENANCE' }] } }
  ],
  metrics: [
    { title: '会议室总数', field: 'total', type: 'count', desc: '完整会议室列表', tone: 'neutral' },
    { title: '当前视图', field: 'filtered', type: 'count', desc: '当前视图命中', tone: 'primary' },
    { title: '可预约', field: 'available', type: 'count', desc: '当前可用', tone: 'success' },
    { title: '维护/停用', field: 'unavailable', type: 'count', desc: '暂不可用', tone: 'warning' },
    { title: '总容量', field: 'totalCapacity', type: 'count', desc: '可容纳人数', tone: 'primary' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: { fixedColumns: { name: 'left' }, rowDensity: 'compact' }
  }
})

export const meetingBookingWorkspaceConfig = defineWorkspaceConfig({
  id: 'meeting-bookings',
  title: '会议预约',
  subtitle: '统一查看预约排期、申请主体、收费快照与财务计费依据',
  pageKey: '/meetings/bookings',
  storageKey: 'dhic-meeting-booking-table-view-v3',
  defaultViewName: '全部预约',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'meeting-booking:view',
    create: 'meeting-booking:create',
    confirm: 'meeting-booking:confirm',
    cancel: 'meeting-booking:cancel',
    complete: 'meeting-booking:complete'
  },
  primaryAction: { id: 'create', label: '新增预约', permission: 'create' },
  fields: [
    { key: 'bookingNo', label: '预约编号', type: 'text', required: true, sortable: true, minWidth: 155 },
    { key: 'createdAt', label: '提交时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'roomName', label: '会议室', type: 'text', sortable: true, groupable: true, minWidth: 155 },
    { key: 'sourceType', label: '来源', type: 'select', options: meetingBookingSourceOptions, sortable: true, groupable: true, width: 110 },
    { key: 'tenantName', label: '租户', type: 'text', sortable: true, groupable: true, minWidth: 160 },
    { key: 'applicantName', label: '申请主体', type: 'text', sortable: true, minWidth: 135 },
    { key: 'departmentName', label: '部门', type: 'text', sortable: true, groupable: true, minWidth: 130 },
    { key: 'purpose', label: '会议用途', type: 'text', minWidth: 160 },
    { key: 'participantCount', label: '参会人数', type: 'number', width: 105, align: 'right' },
    { key: 'startTime', label: '开始时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'endTime', label: '结束时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'status', label: '状态', type: 'status', options: meetingBookingStatusOptions, sortable: true, groupable: true, width: 105 },
    { key: 'unitPriceSnapshot', label: '单价快照', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 125, align: 'right' },
    { key: 'amount', label: '费用依据', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 125, align: 'right' },
    { key: 'billingId', label: '财务账单', type: 'number', sortable: true, width: 110, align: 'right' },
    { key: 'reviewReply', label: '物业回复', type: 'text', minWidth: 210 },
    { key: 'reviewerName', label: '审核人', type: 'text', sortable: true, minWidth: 120 },
    { key: 'reviewedAt', label: '审核时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'remark', label: '备注', type: 'text', minWidth: 190 }
  ],
  views: [
    { id: 'pending-meeting-bookings', name: '待审核预约', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'PENDING' }] } },
    { id: 'confirmed-meeting-bookings', name: '审核通过预约', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'CONFIRMED' }] } },
    { id: 'rejected-meeting-bookings', name: '已驳回预约', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'REJECTED' }] } },
    { id: 'cancelled-meeting-bookings', name: '已取消预约', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'CANCELLED' }] } },
    { id: 'completed-meeting-bookings', name: '已完成预约', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'COMPLETED' }] } }
  ],
  metrics: [
    { title: '预约总数', field: 'total', type: 'count', desc: '当前业务上下文', tone: 'neutral' },
    { title: '当前视图', field: 'filtered', type: 'count', desc: '当前视图命中', tone: 'primary' },
    { title: '待审核', field: 'pending', type: 'count', desc: '等待物业审核', tone: 'warning' },
    { title: '审核通过', field: 'confirmed', type: 'count', desc: '已确认排期', tone: 'success' },
    { title: '费用合计', field: 'totalAmount', type: 'money', desc: '当前业务上下文', tone: 'money' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: {
      fixedColumns: { bookingNo: 'left' },
      rowDensity: 'compact',
      sorts: [{ key: 'createdAt', order: 'descending' }]
    }
  }
})

export function buildMeetingRoomMetricSnapshot(rows = [], visibleRows = rows) {
  const records = Array.isArray(rows) ? rows : []
  const visible = Array.isArray(visibleRows) ? visibleRows : []
  return {
    total: records.length,
    filtered: visible.length,
    available: records.filter(row => ['AVAILABLE', 'ENABLED'].includes(row.status)).length,
    unavailable: records.filter(row => ['DISABLED', 'MAINTENANCE'].includes(row.status)).length,
    totalCapacity: records.reduce((sum, row) => sum + Number(row.capacity || 0), 0)
  }
}

export function buildMeetingBookingMetricSnapshot(rows = [], visibleRows = rows) {
  const records = Array.isArray(rows) ? rows : []
  const visible = Array.isArray(visibleRows) ? visibleRows : []
  return {
    total: records.length,
    filtered: visible.length,
    pending: records.filter(row => ['PENDING', 'BOOKED'].includes(row.status)).length,
    confirmed: records.filter(row => row.status === 'CONFIRMED').length,
    totalAmount: records.reduce((sum, row) => sum + Number(row.amount || 0), 0)
  }
}

export function workspaceOptionLabel(options, value) {
  return options.find(option => String(option.value) === String(value))?.label || value || '未填写'
}

function serializeParkingMonthlyFee(value, condition) {
  if (condition.operator === 'equals') return { monthlyFee: value }
  if (condition.operator === 'greaterThanOrEqual') return { minMonthlyFee: value }
  if (condition.operator === 'lessThanOrEqual') return { maxMonthlyFee: value }
  if (condition.operator === 'between' && Array.isArray(value) && value.length === 2) {
    return { minMonthlyFee: value[0], maxMonthlyFee: value[1] }
  }
  return {}
}
