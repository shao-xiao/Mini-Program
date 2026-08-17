import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverFilter = (param, operators = ['equals']) => ({ mode: 'server', param, operators })
const serverOrder = param => ({ mode: 'server', param })

export const investmentSectionOptions = [
  { label: '顶部主视觉', value: 'hero' },
  { label: '园区亮点', value: 'highlight' },
  { label: '招商政策', value: 'policy' },
  { label: '园区介绍', value: 'introduction' },
  { label: '园区地址', value: 'location' },
  { label: '联系方式', value: 'contact' }
]

export const investmentContentStatusOptions = [
  { label: '草稿', value: 'DRAFT', tone: 'warning' },
  { label: '已发布', value: 'PUBLISHED', tone: 'success' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const investmentListingStatusOptions = [
  { label: '草稿', value: 'DRAFT', tone: 'warning' },
  { label: '已发布', value: 'PUBLISHED', tone: 'success' },
  { label: '已下架', value: 'OFFLINE', tone: 'info' }
]

export const investmentRoomTypeOptions = [
  { label: '办公', value: 'OFFICE' },
  { label: '仓储', value: 'WAREHOUSE' },
  { label: '公共区域', value: 'PUBLIC_AREA' },
  { label: '商业', value: 'SHOP' },
  { label: '其他', value: 'OTHER' }
]

export const investmentRoomStatusOptions = [
  { label: '空置', value: 'VACANT', tone: 'success' },
  { label: '可租', value: 'AVAILABLE', tone: 'success' },
  { label: '已出租', value: 'RENTED', tone: 'primary' },
  { label: '预留', value: 'RESERVED', tone: 'warning' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const investmentLeadStatusOptions = [
  { label: '新线索', value: 'NEW', tone: 'warning' },
  { label: '跟进中', value: 'FOLLOWING', tone: 'primary' },
  { label: '已预约看房', value: 'VIEWING_SCHEDULED', tone: 'primary' },
  { label: '已看房', value: 'VIEWED', tone: 'primary' },
  { label: '已转化', value: 'CONVERTED', tone: 'success' },
  { label: '无效', value: 'INVALID', tone: 'info' }
]

export const investmentContentWorkspaceConfig = defineWorkspaceConfig({
  id: 'investment-contents',
  title: '招商内容',
  subtitle: '按小程序招商中心展示区块维护内容、发布状态和排序',
  pageKey: '/investment/contents',
  storageKey: 'dhic-investment-content-table-view-v1',
  defaultViewName: '全部内容',
  query: { dataMode: 'server', filterLogics: ['and'] },
  permissions: {
    view: 'investment-content:view',
    create: 'investment-content:create',
    update: 'investment-content:update',
    publish: 'investment-content:publish',
    delete: 'investment-content:delete'
  },
  primaryAction: { id: 'create', label: '新增内容', permission: 'create' },
  fields: [
    { key: 'keyword', label: '关键词', type: 'text', filterOnly: true, fixedOnly: true,
      query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'] } },
    { key: 'sectionKey', label: '展示区块', type: 'select', options: investmentSectionOptions, required: true,
      sortable: true, groupable: true, minWidth: 130,
      query: { filter: serverFilter('sectionKey'), sort: serverOrder('sectionKey'), group: serverOrder('sectionKey') } },
    { key: 'title', label: '标题', required: true, sortable: true, minWidth: 170, query: { sort: serverOrder('title') } },
    { key: 'subtitle', label: '副标题', minWidth: 180 },
    { key: 'sortOrder', label: '排序', type: 'number', decimals: 0, sortable: true, width: 90, align: 'right', query: { sort: serverOrder('sortOrder') } },
    { key: 'effectiveAt', label: '生效时间', type: 'date', format: 'datetime', width: 165 },
    { key: 'expiresAt', label: '失效时间', type: 'date', format: 'datetime', width: 165 },
    { key: 'publishStatus', label: '发布状态', type: 'status', options: investmentContentStatusOptions,
      sortable: true, groupable: true, width: 115,
      query: { filter: serverFilter('publishStatus'), sort: serverOrder('publishStatus'), group: serverOrder('publishStatus') } },
    { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('updatedAt') } }
  ],
  views: [
    { id: 'published-content', name: '已发布', system: true, prefs: { filters: [{ key: 'publishStatus', operator: 'equals', value: 'PUBLISHED' }] } },
    { id: 'draft-content', name: '草稿', system: true, prefs: { filters: [{ key: 'publishStatus', operator: 'equals', value: 'DRAFT' }] } }
  ],
  table: { structuralColumnCount: 2, groupColumnIndex: 0, defaultPrefs: { fixedColumns: { title: 'left' }, rowDensity: 'compact' } }
})

export const investmentListingWorkspaceConfig = defineWorkspaceConfig({
  id: 'investment-listings',
  title: '招商房源',
  subtitle: '关联房间台账，维护小程序招商中心展示的房源',
  pageKey: '/investment/listings',
  storageKey: 'dhic-investment-listing-table-view-v1',
  defaultViewName: '全部房源',
  query: { dataMode: 'server', filterLogics: ['and'] },
  permissions: {
    view: 'investment-listing:view',
    create: 'investment-listing:create',
    update: 'investment-listing:update',
    publish: 'investment-listing:publish',
    image: 'investment-listing:image',
    delete: 'investment-listing:delete'
  },
  primaryAction: { id: 'create', label: '新增招商房源', permission: 'create' },
  fields: [
    { key: 'keyword', label: '关键词', type: 'text', filterOnly: true, fixedOnly: true,
      query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'] } },
    { key: 'coverImageUrl', label: '封面', type: 'url', width: 132 },
    { key: 'title', label: '展示标题', sortable: true, minWidth: 190, query: { sort: serverOrder('title') } },
    { key: 'buildingName', label: '楼宇', sortable: true, groupable: true, minWidth: 135,
      query: { sort: serverOrder('buildingName'), group: serverOrder('buildingName') } },
    { key: 'floorName', label: '楼层', sortable: true, groupable: true, width: 110,
      query: { sort: serverOrder('floorName'), group: serverOrder('floorName') } },
    { key: 'roomNumber', label: '房号', required: true, sortable: true, width: 110, query: { sort: serverOrder('roomNumber') } },
    { key: 'area', label: '面积', type: 'number', suffix: '㎡', sortable: true, width: 105, align: 'right', query: { sort: serverOrder('area') } },
    { key: 'roomType', label: '房间类型', type: 'select', options: investmentRoomTypeOptions, groupable: true, width: 115,
      query: { group: serverOrder('roomType') } },
    { key: 'status', label: '出租状态', type: 'status', options: investmentRoomStatusOptions, groupable: true, width: 115,
      query: { group: serverOrder('status') } },
    { key: 'publishStatus', label: '发布状态', type: 'status', options: investmentListingStatusOptions,
      sortable: true, groupable: true, width: 115,
      query: { filter: serverFilter('publishStatus'), sort: serverOrder('publishStatus'), group: serverOrder('publishStatus') } },
    { key: 'recommended', label: '推荐', type: 'checkbox', sortable: true, width: 85, query: { sort: serverOrder('recommended') } },
    { key: 'reservable', label: '可预约', type: 'checkbox', width: 90 },
    { key: 'sortOrder', label: '排序', type: 'number', decimals: 0, sortable: true, width: 85, align: 'right', query: { sort: serverOrder('sortOrder') } },
    { key: 'imageCount', label: '图片', type: 'number', decimals: 0, width: 80, align: 'right' },
    { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('updatedAt') } }
  ],
  views: [
    { id: 'published-listings', name: '已发布', system: true, prefs: { filters: [{ key: 'publishStatus', operator: 'equals', value: 'PUBLISHED' }] } },
    { id: 'draft-listings', name: '待完善', system: true, prefs: { filters: [{ key: 'publishStatus', operator: 'equals', value: 'DRAFT' }] } }
  ],
  metrics: [
    { title: '招商房源', field: 'totalCount', type: 'count', desc: '当前筛选范围', tone: 'neutral' },
    { title: '已发布', field: 'publishedCount', type: 'count', desc: '小程序可见', tone: 'success' },
    { title: '办公房源', field: 'officeCount', type: 'count', desc: '真实房间类型', tone: 'primary' },
    { title: '推荐房源', field: 'recommendedCount', type: 'count', desc: '优先展示', tone: 'primary' },
    { title: '待完善', field: 'draftCount', type: 'count', desc: '草稿或下架', tone: 'warning' }
  ],
  table: { structuralColumnCount: 2, groupColumnIndex: 0, defaultPrefs: { fixedColumns: { title: 'left' }, rowDensity: 'compact' } }
})

export const investmentLeadWorkspaceConfig = defineWorkspaceConfig({
  id: 'investment-leads',
  title: '招商线索',
  subtitle: '统一查看小程序和后台进入的招商线索及跟进状态',
  pageKey: '/investment/leads',
  storageKey: 'dhic-investment-lead-table-view-v1',
  defaultViewName: '全部线索',
  query: { dataMode: 'server', filterLogics: ['and'] },
  permissions: {
    view: 'investment-lead:view',
    follow: 'investment-lead:follow',
    convert: 'investment-lead:convert',
    delete: 'investment-lead:delete'
  },
  fields: [
    { key: 'keyword', label: '关键词', type: 'text', filterOnly: true, fixedOnly: true,
      query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'] } },
    { key: 'name', label: '联系人', required: true, sortable: true, minWidth: 135, query: { sort: serverOrder('name') } },
    { key: 'phone', label: '联系电话', type: 'phone', minWidth: 135 },
    { key: 'companyName', label: '公司名称', sortable: true, minWidth: 160, query: { sort: serverOrder('companyName') } },
    { key: 'source', label: '来源', type: 'text', groupable: true, width: 115,
      query: { filter: serverFilter('source'), group: serverOrder('source') } },
    { key: 'intendedRoom', label: '意向房源', minWidth: 130 },
    { key: 'desiredArea', label: '意向面积', type: 'number', suffix: '㎡', sortable: true, width: 115, align: 'right', query: { sort: serverOrder('desiredArea') } },
    { key: 'intendedUse', label: '用途', minWidth: 130 },
    { key: 'preferredVisitTime', label: '期望看房时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('preferredVisitTime') } },
    { key: 'remark', label: '备注', minWidth: 180 },
    { key: 'status', label: '状态', type: 'status', options: investmentLeadStatusOptions,
      sortable: true, groupable: true, width: 120,
      query: { filter: serverFilter('status'), sort: serverOrder('status'), group: serverOrder('status') } },
    { key: 'createdTime', label: '提交时间', type: 'date', format: 'datetime', sortable: true, width: 165, query: { sort: serverOrder('createdTime') } }
  ],
  views: [
    { id: 'new-leads', name: '新线索', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'NEW' }] } },
    { id: 'following-leads', name: '跟进中', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'FOLLOWING' }] } }
  ],
  metrics: [
    { title: '线索总数', field: 'totalCount', type: 'count', desc: '当前筛选范围', tone: 'neutral' },
    { title: '新线索', field: 'newCount', type: 'count', desc: '待首次跟进', tone: 'warning' },
    { title: '跟进中', field: 'followingCount', type: 'count', desc: '正在推进', tone: 'primary' },
    { title: '已转化', field: 'convertedCount', type: 'count', desc: '已形成租赁机会', tone: 'success' }
  ],
  table: { structuralColumnCount: 2, groupColumnIndex: 0, defaultPrefs: { fixedColumns: { name: 'left' }, rowDensity: 'compact' } }
})
