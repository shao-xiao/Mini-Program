import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverOrder = param => ({ mode: 'server', param })

export const equipmentTypeOptions = [
  { label: '电梯', value: 'ELEVATOR' },
  { label: '空调', value: 'HVAC' },
  { label: '消防', value: 'FIRE' },
  { label: '配电', value: 'POWER' },
  { label: '给排水', value: 'WATER' },
  { label: '安防', value: 'SECURITY' },
  { label: '其他', value: 'OTHER' }
]

export const equipmentStatusOptions = [
  { label: '正常', value: 'NORMAL', tone: 'success' },
  { label: '故障', value: 'FAULT', tone: 'danger' },
  { label: '维修中', value: 'MAINTENANCE', tone: 'warning' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const equipmentImportCustomFields = [
  { key: 'custom_quantity', label: '数量', custom: true, editable: true, type: 'number', sortable: true, width: 100, valueGetter: row => row.customFields?.custom_quantity },
  { key: 'custom_manufacturer_contact', label: '厂家联系方式', custom: true, editable: true, type: 'phone', sortable: true, minWidth: 160, valueGetter: row => row.customFields?.custom_manufacturer_contact },
  { key: 'custom_warranty_period', label: '质保期', custom: true, editable: true, type: 'text', sortable: true, minWidth: 190, valueGetter: row => row.customFields?.custom_warranty_period },
  { key: 'custom_source_sequence', label: '原表序号', custom: true, editable: true, type: 'text', sortable: true, width: 100, valueGetter: row => row.customFields?.custom_source_sequence }
]

export function createEquipmentWorkspaceConfig({ typeOptions = equipmentTypeOptions } = {}) {
  return defineWorkspaceConfig({
    id: 'equipment',
    title: '设备台账',
    pageKey: '/equipment',
    storageKey: 'dhic-equipment-table-view-v1',
    defaultViewName: '默认视图',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: {
      view: 'asset:view',
      create: 'asset:create',
      update: 'asset:update',
      delete: 'asset:delete',
      import: 'asset:create',
      export: 'asset:view',
      exportTemplate: 'asset:create'
    },
    customFields: {
      enabled: true,
      workspaceKey: 'equipment',
      managePermission: 'custom-field:manage',
      valueUpdatePermission: 'asset:update'
    },
    primaryAction: { id: 'create', label: '新增设备', permission: 'create' },
    importExport: [
      {
        id: 'import',
        kind: 'import',
        label: '导入设备',
        permission: 'import',
        accept: '.xlsx,.csv,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      },
      { id: 'export-current', kind: 'export', label: '导出当前查询', permission: 'export', group: 'export', groupLabel: '导出' },
      { id: 'export-template', kind: 'template', label: '导出模板', permission: 'exportTemplate', group: 'export', groupLabel: '导出' }
    ],
    batchActions: [
      { id: 'edit', label: '批量编辑', permission: 'update', icon: 'edit' },
      { id: 'export', label: '批量导出', permission: 'export', icon: 'download' },
      { id: 'delete', label: '批量删除', permission: 'delete', icon: 'delete', tone: 'danger' }
    ],
    statuses: equipmentStatusOptions,
    fields: [
      {
        key: 'keyword',
        label: '关键词',
        type: 'text',
        filterOnly: true,
        fixedOnly: true,
        query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'], serialize: value => String(value || '').trim() },
        valueGetter: row => [row.equipmentName, row.equipmentCode, row.location, row.manufacturer, row.model].filter(Boolean).join(' ')
      },
      { key: 'equipmentName', label: '设备名称', required: true, editable: true, sortable: true, minWidth: 160, query: { sort: serverOrder('equipmentName') } },
      { key: 'equipmentCode', label: '设备编号', editable: true, sortable: true, minWidth: 140, query: { sort: serverOrder('equipmentCode') } },
      {
        key: 'equipmentType',
        label: '设备类型',
        type: 'select',
        options: typeOptions,
        editable: true,
        sortable: true,
        groupable: true,
        width: 140,
        query: {
          mode: 'server',
          param: 'equipmentType',
          operators: ['equals'],
          sort: serverOrder('equipmentType'),
          group: serverOrder('equipmentType')
        },
        valueGetter: row => row.equipmentType
      },
      { key: 'location', label: '位置', editable: true, sortable: true, groupable: true, minWidth: 160, query: { sort: serverOrder('location'), group: serverOrder('location') } },
      { key: 'buildingName', label: '所属楼宇', minWidth: 130 },
      { key: 'floorName', label: '所属楼层', width: 110 },
      { key: 'roomCode', label: '所属房间', width: 110 },
      {
        key: 'status',
        label: '状态',
        type: 'status',
        options: equipmentStatusOptions,
        editable: true,
        sortable: true,
        groupable: true,
        width: 120,
        query: {
          mode: 'server',
          param: 'status',
          operators: ['equals'],
          sort: serverOrder('status'),
          group: serverOrder('status')
        },
        valueGetter: row => row.status
      },
      { key: 'manufacturer', label: '厂家', editable: true, sortable: true, groupable: true, minWidth: 130, query: { sort: serverOrder('manufacturer'), group: serverOrder('manufacturer') } },
      { key: 'model', label: '型号', editable: true, sortable: true, minWidth: 130, query: { sort: serverOrder('model') } },
      { key: 'serialNo', label: '序列号', minWidth: 135 },
      { key: 'responsibleDepartment', label: '责任部门', minWidth: 130 },
      { key: 'responsibleUserName', label: '责任人', width: 110 },
      { key: 'serviceLifeYears', label: '使用年限', type: 'number', width: 105 },
      { key: 'installDate', label: '安装日期', type: 'date', editable: true, sortable: true, width: 120, query: { sort: serverOrder('installDate') } },
      { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('updatedAt') } },
      { key: 'remark', label: '备注', editable: true, minWidth: 180 },
      ...equipmentImportCustomFields
    ],
    views: [
      {
        id: 'maintenance-equipment',
        name: '维修设备',
        system: true,
        prefs: { filters: [{ key: 'status', operator: 'equals', value: 'MAINTENANCE' }] }
      },
      {
        id: 'fault-equipment',
        name: '故障设备',
        system: true,
        prefs: { filters: [{ key: 'status', operator: 'equals', value: 'FAULT' }] }
      }
    ],
    metrics: [
      { title: '设备总数', field: 'total', type: 'count', desc: '全部设备档案', tone: 'neutral' },
      { title: '筛选结果', field: 'filtered', type: 'count', desc: '当前条件命中', tone: 'primary' },
      { title: '正常设备', field: 'normal', type: 'count', desc: '可正常使用', tone: 'success' },
      { title: '维修中', field: 'maintenance', type: 'count', desc: '正在处理', tone: context => context.maintenance > 0 ? 'warning' : 'neutral' },
      { title: '故障设备', field: 'fault', type: 'count', desc: '需优先处理', tone: context => context.fault > 0 ? 'danger' : 'success' },
      { title: '停用设备', field: 'disabled', type: 'count', desc: '当前不可用', tone: context => context.disabled > 0 ? 'warning' : 'neutral' }
    ],
    table: {
      structuralColumnCount: 3,
      groupColumnIndex: 1,
      defaultPrefs: {
        fixedColumns: { equipmentName: 'left' },
        rowDensity: 'compact'
      }
    }
  })
}
