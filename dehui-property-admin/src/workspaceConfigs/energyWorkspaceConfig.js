import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverOrder = param => ({ mode: 'server', param })

export const energyMeterTypeOptions = [
  { label: '电', value: 'ELECTRIC', tone: 'warning' },
  { label: '水', value: 'WATER', tone: 'primary' },
  { label: '燃气', value: 'GAS', tone: 'danger' }
]

export const energyMeterStatusOptions = [
  { label: '启用', value: 'ENABLED', tone: 'success' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const energyBillStatusOptions = [
  { label: '未生成', value: 'NOT_GENERATED', tone: 'warning' },
  { label: '已生成', value: 'GENERATED', tone: 'primary' },
  { label: '已入账', value: 'POSTED', tone: 'success' }
]

export const energyAnomalyStateOptions = [
  { label: '正常', value: 'NORMAL', tone: 'success' },
  { label: '待处理', value: 'PENDING', tone: 'danger' },
  { label: '已确认', value: 'CONFIRMED', tone: 'warning' },
  { label: '已忽略', value: 'IGNORED', tone: 'info' },
  { label: '已解决', value: 'RESOLVED', tone: 'success' }
]

export const energyAbnormalFlagOptions = [
  { label: '异常', value: true, tone: 'danger' },
  { label: '正常', value: false, tone: 'success' }
]

export const energyAbnormalReasonOptions = [
  { label: '负用量', value: 'NEGATIVE_USAGE', tone: 'danger' },
  { label: '零用量', value: 'ZERO_USAGE', tone: 'danger' },
  { label: '高用量', value: 'HIGH_USAGE', tone: 'danger' },
  { label: '低用量', value: 'LOW_USAGE', tone: 'danger' },
  { label: '连续读数无变化', value: 'NO_CHANGE', tone: 'danger' }
]

export const energyAnomalyColumns = [
  { key: 'meterNo', label: '表号', type: 'text', required: true, minWidth: 125 },
  { key: 'meterType', label: '类型', type: 'select', options: energyMeterTypeOptions, width: 90 },
  { key: 'periodMonth', label: '账期', type: 'text', width: 100 },
  { key: 'buildingName', label: '楼宇', type: 'text', minWidth: 130 },
  { key: 'roomName', label: '房间', type: 'text', minWidth: 120 },
  { key: 'usageAmount', label: '本期用量', type: 'number', decimals: 2, width: 120, align: 'right' },
  { key: 'abnormalReason', label: '异常原因', type: 'status', options: energyAbnormalReasonOptions, minWidth: 140 },
  { key: 'abnormalStatus', label: '状态', type: 'status', options: energyAnomalyStateOptions, width: 110 }
]

export function createEnergyMeterWorkspaceConfig({ buildingOptions = [] } = {}) {
  return defineWorkspaceConfig({
    id: 'energy-meters',
    title: '表具管理',
    subtitle: '水、电、燃气表具档案',
    pageKey: '/energy/meters',
    storageKey: 'dhic-energy-meter-table-view-v1',
    defaultViewName: '全部表具',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: {
      view: 'energy:meter:view',
      readingView: 'energy:view',
      create: 'energy:meter:create',
      update: 'energy:meter:update',
      delete: 'energy:meter:delete'
    },
    primaryAction: { id: 'create', label: '新增表具', permission: 'create' },
    dataActions: [{ id: 'readings', label: '抄表管理', permission: 'readingView' }],
    statuses: energyMeterStatusOptions,
    fields: [
      {
        key: 'keyword',
        label: '关键词',
        type: 'text',
        filterOnly: true,
        fixedOnly: true,
        query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'], serialize: value => String(value || '').trim() },
        valueGetter: row => [row.meterNo, row.meterName, row.buildingName, row.floorName, row.roomName, row.tenantName].filter(Boolean).join(' ')
      },
      { key: 'buildingId', label: '所属楼宇', type: 'select', options: buildingOptions, filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'buildingId', operators: ['equals'] }, valueGetter: row => row.buildingId },
      { key: 'meterNo', label: '表具编号', type: 'text', sortable: true, required: true, minWidth: 140, query: { sort: serverOrder('meterNo') } },
      { key: 'meterName', label: '表具名称', type: 'text', sortable: true, required: true, minWidth: 160, query: { sort: serverOrder('meterName') } },
      {
        key: 'meterType',
        label: '类型',
        type: 'select',
        options: energyMeterTypeOptions,
        sortable: true,
        groupable: true,
        width: 100,
        query: {
          mode: 'server',
          param: 'meterType',
          operators: ['equals'],
          sort: serverOrder('meterType'),
          group: serverOrder('meterType')
        }
      },
      {
        key: 'location',
        label: '安装位置',
        type: 'text',
        minWidth: 220,
        valueGetter: row => [row.buildingName, row.floorName, row.roomName].filter(Boolean).join(' / ')
      },
      { key: 'buildingName', label: '所属楼宇', type: 'text', sortable: true, groupable: true, minWidth: 130, query: { sort: serverOrder('buildingName'), group: serverOrder('buildingName') } },
      { key: 'roomName', label: '所属房间', type: 'text', sortable: true, groupable: true, minWidth: 120, query: { sort: serverOrder('roomName'), group: serverOrder('roomName') } },
      { key: 'tenantName', label: '关联租户', type: 'text', sortable: true, minWidth: 150, query: { sort: serverOrder('tenantName') } },
      { key: 'unit', label: '单位', type: 'text', width: 90 },
      { key: 'multiplier', label: '倍率', type: 'number', decimals: 4, sortable: true, width: 100, query: { sort: serverOrder('multiplier') } },
      {
        key: 'status',
        label: '状态',
        type: 'status',
        options: energyMeterStatusOptions,
        sortable: true,
        groupable: true,
        width: 110,
        query: {
          mode: 'server',
          param: 'status',
          operators: ['equals'],
          sort: serverOrder('status'),
          group: serverOrder('status')
        }
      },
      { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('updatedAt') } },
      { key: 'remark', label: '备注', type: 'text', minWidth: 180 }
    ],
    views: [
      {
        id: 'disabled-meters',
        name: '停用表具',
        system: true,
        prefs: { filters: [{ key: 'status', operator: 'equals', value: 'DISABLED' }] }
      }
    ],
    metrics: [
      { title: '表具总数', field: 'total', type: 'count', desc: '全部表具档案', tone: 'neutral' },
      { title: '筛选结果', field: 'filtered', type: 'count', desc: '当前条件命中', tone: 'primary' },
      { title: '正常表具', field: 'enabled', type: 'count', desc: '参与抄表', tone: 'success' },
      { title: '停用表具', field: 'disabled', type: 'count', desc: '不参与抄表', tone: context => context.disabled > 0 ? 'warning' : 'neutral' },
      { title: '电表', field: 'electric', type: 'count', desc: '电力计量', tone: 'warning' },
      { title: '水表', field: 'water', type: 'count', desc: '用水计量', tone: 'primary' },
      { title: '燃气表', field: 'gas', type: 'count', desc: '燃气计量', tone: 'danger' }
    ],
    table: {
      structuralColumnCount: 2,
      groupColumnIndex: 0,
      defaultPrefs: {
        fixedColumns: { meterNo: 'left' },
        rowDensity: 'compact'
      }
    }
  })
}

export function createEnergyReadingWorkspaceConfig({ buildingOptions = [], billingEnabled = true } = {}) {
  return defineWorkspaceConfig({
    id: 'energy-readings',
    title: '抄表管理',
    subtitle: billingEnabled ? '抄表记录、异常识别与费用依据确认' : '抄表记录、异常识别与结算数据',
    pageKey: '/energy/records',
    storageKey: 'dhic-energy-reading-table-view-v1',
    defaultViewName: '全部抄表',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: {
      view: 'energy:view',
      meterView: 'energy:meter:view',
      create: 'energy:create',
      price: 'energy:update',
      update: 'energy:reading:update',
      delete: 'energy:reading:delete',
      import: 'energy:create',
      export: 'energy:create',
      generateBill: 'energy:create'
    },
    primaryAction: { id: 'create', label: '新增抄表', permission: 'create' },
    dataActions: [
      { id: 'meters', label: '表具管理', permission: 'meterView' },
      { id: 'price', label: '设置单价', permission: 'price' },
      { id: 'monthly-task', label: '生成本月任务', permission: 'create' }
    ],
    importExport: [
      { id: 'export-template', kind: 'template', label: '导出模板', permission: 'export' },
      { id: 'import', kind: 'import', label: '导入读数', permission: 'import', accept: '.csv,text/csv' },
      ...(billingEnabled
        ? [{ id: 'batch-bill', kind: 'batch', label: '批量确认费用依据', permission: 'generateBill' }]
        : [])
    ],
    statuses: energyAnomalyStateOptions,
    fields: [
      {
        key: 'meterType',
        label: '表具类型',
        type: 'select',
        options: energyMeterTypeOptions,
        sortable: true,
        groupable: true,
        width: 110,
        query: {
          mode: 'server',
          param: 'meterType',
          operators: ['equals'],
          sort: serverOrder('meterType'),
          group: serverOrder('meterType')
        }
      },
      {
        key: 'periodMonth',
        label: '账期',
        type: 'text',
        sortable: true,
        groupable: true,
        width: 100,
        query: {
          mode: 'server',
          param: 'periodMonth',
          operators: ['equals'],
          sort: serverOrder('periodMonth'),
          group: serverOrder('periodMonth')
        }
      },
      {
        key: 'meterNo',
        label: '表具编号',
        type: 'text',
        sortable: true,
        minWidth: 140,
        query: {
          mode: 'server',
          param: 'meterNo',
          operators: ['contains', 'equals'],
          serialize: value => String(value || '').trim(),
          sort: serverOrder('meterNo')
        }
      },
      {
        key: 'meter',
        label: '表具',
        type: 'relation',
        relation: { entityType: 'energy-meter', idField: 'id', labelField: 'label', multiple: false },
        valueGetter: row => ({ id: row.meterId || row.meterNo, label: [row.meterNo, row.meterName].filter(Boolean).join(' / '), entityType: 'energy-meter' }),
        minWidth: 210
      },
      { key: 'buildingId', label: '所属楼宇', type: 'select', options: buildingOptions, filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'buildingId', operators: ['equals'] } },
      { key: 'buildingName', label: '所属楼宇', type: 'text', sortable: true, groupable: true, minWidth: 130, query: { sort: serverOrder('buildingName'), group: serverOrder('buildingName') } },
      { key: 'roomName', label: '所属房间', type: 'text', sortable: true, groupable: true, minWidth: 120, query: { sort: serverOrder('roomName'), group: serverOrder('roomName') } },
      { key: 'tenantName', label: '关联租户', type: 'text', sortable: true, minWidth: 150, query: { sort: serverOrder('tenantName') } },
      { key: 'readingDate', label: '抄表日期', type: 'date', sortable: true, width: 120, query: { sort: serverOrder('readingDate') } },
      { key: 'previousReading', label: '上次读数', type: 'number', decimals: 2, sortable: true, width: 120, query: { sort: serverOrder('previousReading') } },
      { key: 'currentReading', label: '当前读数', type: 'number', decimals: 2, sortable: true, width: 120, query: { sort: serverOrder('currentReading') } },
      { key: 'usageAmount', label: '使用量', type: 'number', decimals: 2, sortable: true, width: 120, query: { sort: serverOrder('usageAmount') } },
      { key: 'operatorName', label: '操作人', type: 'user', sortable: true, minWidth: 130, query: { sort: serverOrder('operatorName') } },
      { key: 'abnormalFlag', label: '异常', type: 'select', options: energyAbnormalFlagOptions, filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'abnormalFlag', operators: ['equals'] } },
      {
        key: 'anomalyState',
        label: '异常状态',
        type: 'status',
        options: energyAnomalyStateOptions,
        sortable: true,
        groupable: true,
        width: 120,
        valueGetter: row => row.abnormalFlag ? (row.abnormalStatus || 'PENDING') : 'NORMAL',
        query: { sort: serverOrder('anomalyState'), group: serverOrder('anomalyState') }
      },
      {
        key: 'billStatus',
        label: '账单状态',
        type: 'status',
        options: energyBillStatusOptions,
        sortable: true,
        groupable: true,
        width: 120,
        query: {
          mode: 'server',
          param: 'billStatus',
          operators: ['equals'],
          sort: serverOrder('billStatus'),
          group: serverOrder('billStatus')
        }
      },
      { key: 'unitPrice', label: '单价', type: 'money', currency: 'CNY', decimals: 4, sortable: true, width: 120, query: { sort: serverOrder('unitPrice') } },
      { key: 'settlementAmount', label: '费用', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 130, query: { sort: serverOrder('settlementAmount') } },
      { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('updatedAt') } },
      { key: 'remark', label: '备注', type: 'text', minWidth: 180 }
    ],
    views: [
      {
        id: 'abnormal-readings',
        name: '异常抄表',
        system: true,
        prefs: { filters: [{ key: 'abnormalFlag', operator: 'equals', value: true }] }
      }
    ],
    metrics: [
      { title: '抄表总数', field: 'total', type: 'count', desc: '全部抄表记录', tone: 'neutral' },
      { title: '筛选结果', field: 'filtered', type: 'count', desc: '当前条件命中', tone: 'primary' },
      { title: '异常记录', field: 'abnormal', type: 'count', desc: '需要核对处理', tone: context => context.abnormal > 0 ? 'danger' : 'success' },
      { title: '当前范围用量', field: 'usage', type: 'count', decimals: 2, desc: '水电燃气用量合计', tone: 'primary' },
      { title: '结算金额', field: 'amount', type: 'money', desc: '当前范围汇总', tone: 'money' }
    ],
    table: {
      structuralColumnCount: 2,
      groupColumnIndex: 0,
      defaultPrefs: {
        fixedColumns: { meter: 'left' },
        rowDensity: 'compact'
      }
    }
  })
}
