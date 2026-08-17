import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverOrder = param => ({ mode: 'server', param })

export const buildingStatusOptions = [
  { label: '启用', value: 'ACTIVE', tone: 'success' },
  { label: '启用', value: 'ENABLED', tone: 'success' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const floorStatusOptions = [
  { label: '启用', value: 'ACTIVE', tone: 'success' },
  { label: '启用', value: 'ENABLED', tone: 'success' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const roomTypeOptions = [
  { label: '办公', value: 'OFFICE', tone: 'primary' },
  { label: '仓储', value: 'WAREHOUSE', tone: 'warning' },
  { label: '设备间', value: 'EQUIPMENT', tone: 'info' },
  { label: '车位', value: 'PARKING', tone: 'success' },
  { label: '公区', value: 'PUBLIC_AREA', tone: 'info' }
]

export const roomStatusOptions = [
  { label: '空置', value: 'VACANT', tone: 'success' },
  { label: '已出租', value: 'RENTED', tone: 'primary' },
  { label: '预留', value: 'RESERVED', tone: 'warning' },
  { label: '装修中', value: 'RENOVATING', tone: 'warning' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const roomContractStageOptions = [
  { label: '合同已生效', value: 'ACTIVE_CURRENT', tone: 'success' },
  { label: '合同待生效', value: 'ACTIVE_FUTURE', tone: 'warning' },
  { label: '合同已暂停', value: 'SUSPENDED', tone: 'danger' },
  { label: '合同待审批', value: 'PENDING_APPROVAL', tone: 'warning' },
  { label: '合同草稿', value: 'DRAFT', tone: 'info' },
  { label: '暂无合同', value: 'NONE', tone: 'info' }
]

export const buildingWorkspaceConfig = defineWorkspaceConfig({
  id: 'buildings',
  title: '楼宇管理',
  subtitle: '楼宇基础档案与启停状态',
  pageKey: '/buildings',
  storageKey: 'dhic-building-table-view-v1',
  defaultViewName: '全部楼宇',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'building:view',
    create: 'building:create',
    update: 'building:update',
    delete: 'building:delete'
  },
  primaryAction: { id: 'create', label: '新增楼宇', permission: 'create' },
  fields: [
    { key: 'buildingCode', label: '楼宇编号', type: 'text', required: true, sortable: true, minWidth: 130 },
    { key: 'buildingName', label: '楼宇名称', type: 'text', required: true, sortable: true, minWidth: 160 },
    { key: 'address', label: '地址', type: 'text', sortable: true, minWidth: 220 },
    { key: 'totalFloors', label: '楼层数', type: 'number', decimals: 0, sortable: true, width: 105, align: 'right' },
    {
      key: 'status',
      label: '状态',
      type: 'status',
      options: buildingStatusOptions,
      sortable: true,
      groupable: true,
      width: 105,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    },
    { key: 'description', label: '说明', type: 'text', minWidth: 180 },
    { key: 'createdAt', label: '创建时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', sortable: true, width: 165 }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: {
      fixedColumns: { buildingName: 'left' },
      rowDensity: 'compact'
    }
  }
})

export const floorWorkspaceConfig = defineWorkspaceConfig({
  id: 'floors',
  title: '楼层管理',
  subtitle: '按楼宇维护楼层档案',
  pageKey: '/floors',
  storageKey: 'dhic-floor-table-view-v1',
  defaultViewName: '全部楼层',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'floor:view',
    create: 'floor:create',
    update: 'floor:update',
    delete: 'floor:delete'
  },
  primaryAction: { id: 'create', label: '新增楼层', permission: 'create' },
  dataActions: [
    { id: 'batch-generate', label: '批量生成楼层', permission: 'create' }
  ],
  fields: [
    { key: 'floorCode', label: '楼层编号', type: 'text', sortable: true, minWidth: 130 },
    { key: 'buildingId', label: '楼宇 ID', type: 'number', decimals: 0, sortable: true, width: 105 },
    { key: 'floorNumber', label: '楼层号', type: 'number', decimals: 0, required: true, sortable: true, width: 105 },
    { key: 'floorName', label: '楼层名称', type: 'text', sortable: true, minWidth: 150 },
    { key: 'totalArea', label: '总面积', type: 'number', decimals: 2, sortable: true, width: 130, align: 'right' },
    {
      key: 'status',
      label: '状态',
      type: 'status',
      options: floorStatusOptions,
      sortable: true,
      groupable: true,
      width: 105,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    },
    { key: 'description', label: '说明', type: 'text', minWidth: 190 }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: {
      visibleColumns: ['floorCode', 'floorNumber', 'floorName', 'totalArea', 'status', 'description'],
      fixedColumns: { floorNumber: 'left' },
      rowDensity: 'compact'
    }
  }
})

export const roomWorkspaceConfig = defineWorkspaceConfig({
  id: 'rooms',
  title: '房间管理',
  subtitle: '占用状态以入住/退租为准，合同阶段同步审批流程',
  pageKey: '/rooms',
  storageKey: 'dhic-room-table-view-v1',
  defaultViewName: '全部房间',
  query: { dataMode: 'server', filterLogics: ['and'] },
  permissions: {
    view: 'room:view',
    create: 'room:create',
    update: 'room:update',
    delete: 'room:delete',
    lease: 'room:lease',
    checkout: 'room:checkout'
  },
  primaryAction: { id: 'create', label: '新增房间', permission: 'create' },
  fields: [
    { key: 'roomCode', label: '房间编号', type: 'text', required: true, sortable: true, minWidth: 145, query: { sort: serverOrder('roomCode') } },
    { key: 'buildingName', label: '楼宇', type: 'text', sortable: true, groupable: true, minWidth: 135, query: { sort: serverOrder('buildingName'), group: serverOrder('buildingName') } },
    { key: 'floorName', label: '楼层', type: 'text', sortable: true, groupable: true, minWidth: 115, query: { sort: serverOrder('floorName'), group: serverOrder('floorName') } },
    { key: 'roomNumber', label: '房间号', type: 'text', required: true, sortable: true, minWidth: 120, query: { sort: serverOrder('roomNumber') } },
    { key: 'roomName', label: '房间名称', type: 'text', sortable: true, minWidth: 145, query: { sort: serverOrder('roomName') } },
    { key: 'area', label: '面积㎡', type: 'number', decimals: 2, sortable: true, width: 110, align: 'right', query: { sort: serverOrder('area') } },
    { key: 'propertyFeeArea', label: '物业费计费面积㎡', type: 'number', decimals: 2, sortable: true, width: 150, align: 'right', query: { sort: serverOrder('propertyFeeArea') } },
    { key: 'roomType', label: '类型', type: 'select', options: roomTypeOptions, sortable: true, groupable: true, width: 115, query: { sort: serverOrder('roomType'), group: serverOrder('roomType') } },
    {
      key: 'status',
      label: '占用状态',
      type: 'status',
      options: roomStatusOptions,
      sortable: true,
      groupable: true,
      width: 115,
      query: {
        mode: 'server',
        param: 'status',
        operators: ['equals'],
        sort: serverOrder('status'),
        group: serverOrder('status')
      }
    },
    {
      key: 'contractStage',
      label: '合同阶段',
      type: 'status',
      options: roomContractStageOptions,
      required: true,
      width: 145
    },
    { key: 'description', label: '说明', type: 'text', minWidth: 190 }
  ],
  metrics: [
    { title: '房间总数', field: 'total', type: 'count', desc: '当前楼宇全部房间', tone: 'neutral' },
    { title: '当前筛选结果', field: 'filtered', type: 'count', desc: '服务端筛选命中', tone: 'primary' },
    { title: '未入住', field: 'available', type: 'count', desc: '房态为空置', tone: 'success' },
    { title: '已入住', field: 'rented', type: 'count', desc: '房态为已出租', tone: 'money' },
    {
      title: '预留/不可用',
      field: 'unavailable',
      type: 'count',
      desc: '预留、装修或停用',
      tone: context => context.unavailable > 0 ? 'warning' : 'neutral'
    }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: {
      fixedColumns: { roomNumber: 'left' },
      rowDensity: 'compact'
    }
  }
})
