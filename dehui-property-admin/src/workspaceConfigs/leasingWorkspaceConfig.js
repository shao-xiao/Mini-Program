import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverFilter = (param, operators = ['equals']) => ({ mode: 'server', param, operators })
const serverOrder = param => ({ mode: 'server', param })

export const leaseStatusOptions = [
  { label: '待入住', value: 'PENDING_CHECKIN', tone: 'warning' },
  { label: '部分入住', value: 'PARTIALLY_ACTIVE', tone: 'warning' },
  { label: '履约中', value: 'ACTIVE', tone: 'success' },
  { label: '部分退租', value: 'PARTIALLY_TERMINATED', tone: 'danger' },
  { label: '已终止', value: 'TERMINATED', tone: 'info' },
  { label: '已取消', value: 'CANCELLED', tone: 'info' }
]
export const feeTypeOptions = [
  { label: '租金', value: 'RENT' }, { label: '物业费', value: 'PROPERTY' },
  { label: '水费', value: 'WATER' }, { label: '电费', value: 'ELECTRICITY' },
  { label: '燃气费', value: 'GAS' }, { label: '停车费', value: 'PARKING' },
  { label: '会议室', value: 'MEETING_ROOM' }, { label: '工单服务', value: 'WORK_ORDER' },
  { label: '保洁费', value: 'CLEANING' }, { label: '其他', value: 'OTHER' }
]
export const calculationTypeOptions = [
  { label: '固定金额', value: 'FIXED' }, { label: '面积 × 单价', value: 'AREA_UNIT_PRICE' },
  { label: '数量 × 单价', value: 'QUANTITY_UNIT_PRICE' }, { label: '一次性费用', value: 'ONE_TIME' },
  { label: '比例计费', value: 'PERCENTAGE', disabled: true },
  { label: '滞纳金', value: 'LATE_FEE', disabled: true },
  { label: '公式计费', value: 'FORMULA', disabled: true }
]
export const billingCycleOptions = [
  { label: '每月', value: 'MONTHLY' }, { label: '每季度', value: 'QUARTERLY' },
  { label: '每半年', value: 'SEMI_ANNUAL' }, { label: '每年', value: 'ANNUAL' },
  { label: '一次性', value: 'ONE_TIME' }
]
export const feeRuleStatusOptions = [
  { label: '草稿', value: 'DRAFT', tone: 'warning' },
  { label: '生效', value: 'ACTIVE', tone: 'success' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]
export const billingPlanStatusOptions = [
  { label: '草稿', value: 'DRAFT', tone: 'warning' },
  { label: '生效', value: 'ACTIVE', tone: 'success' },
  { label: '已终止', value: 'TERMINATED', tone: 'info' }
]
export const dueRuleOptions = [
  { label: '周期开始日到期', value: 'PERIOD_START' },
  { label: '周期结束日到期', value: 'PERIOD_END' },
  { label: '下一周期开始日到期', value: 'NEXT_PERIOD_START' }
]

export function createLeaseWorkspaceConfig({ tenantOptions = [] } = {}) {
  return defineWorkspaceConfig({
    id: 'leases', title: '租约履约', subtitle: '以租约和房间占用记录为准办理入住与退租',
    pageKey: '/leases', storageKey: 'dhic-lease-table-view-v1', defaultViewName: '全部租约',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: {
      view: 'lease:view', create: 'lease:create', update: 'lease:update',
      checkin: 'lease:checkin', checkout: 'lease:checkout', renew: 'lease:renew',
      terminate: 'lease:terminate', delete: 'lease:delete'
    },
    primaryAction: { id: 'create', label: '新建租约', permission: 'create' },
    fields: [
      { key: 'keyword', label: '关键词', type: 'text', filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'] } },
      { key: 'tenantId', label: '租户', type: 'select', options: tenantOptions, filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'tenantId', operators: ['equals'] } },
      { key: 'leaseNo', label: '租约编号', required: true, sortable: true, minWidth: 150, query: { sort: serverOrder('leaseNo') } },
      { key: 'contractNo', label: '合同编号', sortable: true, minWidth: 145, query: { sort: serverOrder('contractNo') } },
      { key: 'tenantName', label: '租户', sortable: true, groupable: true, minWidth: 160, query: { sort: serverOrder('tenantName'), group: serverOrder('tenantName') } },
      { key: 'roomNames', label: '房间', type: 'multi-select', minWidth: 210 },
      { key: 'roomCount', label: '房间数', type: 'number', decimals: 0, width: 100, align: 'right' },
      { key: 'activeRoomCount', label: '在租房间', type: 'number', decimals: 0, width: 110, align: 'right' },
      { key: 'plannedStartDate', label: '计划开始', type: 'date', width: 120, sortable: true, query: { sort: serverOrder('plannedStartDate') } },
      { key: 'plannedEndDate', label: '计划结束', type: 'date', width: 120, sortable: true, query: { sort: serverOrder('plannedEndDate') } },
      { key: 'actualCheckinAt', label: '实际入住', type: 'date', format: 'datetime', width: 165 },
      { key: 'actualCheckoutAt', label: '实际退租', type: 'date', format: 'datetime', width: 165 },
      { key: 'status', label: '状态', type: 'status', options: leaseStatusOptions, width: 125, sortable: true, groupable: true,
        query: { filter: serverFilter('status'), sort: serverOrder('status'), group: serverOrder('status') } },
      { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', width: 165, sortable: true, query: { sort: serverOrder('updatedAt') } }
    ],
    views: [
      { id: 'active-leases', name: '履约中', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'ACTIVE' }] } },
      { id: 'pending-leases', name: '待入住', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'PENDING_CHECKIN' }] } }
    ],
    metrics: [
      { title: '租约总数', field: 'total', type: 'count', desc: '当前筛选范围', tone: 'neutral' },
      { title: '待入住', field: 'pendingCheckin', type: 'count', desc: '尚未建立房间占用', tone: 'warning' },
      { title: '履约中', field: 'activeTotal', type: 'count', desc: '包含部分入住', tone: 'success' },
      { title: '退租处理中', field: 'partiallyTerminated', type: 'count', desc: '部分房间已退租', tone: 'warning' }
    ],
    table: { structuralColumnCount: 2, groupColumnIndex: 0, defaultPrefs: { fixedColumns: { leaseNo: 'left' }, rowDensity: 'compact' } }
  })
}

export const feeRuleWorkspaceConfig = defineWorkspaceConfig({
  id: 'fee-rules', title: '收费规则', subtitle: '维护费用类型、计价方式和规则版本',
  pageKey: '/fee-rules', storageKey: 'dhic-fee-rule-table-view-v1', defaultViewName: '全部规则',
  query: { dataMode: 'server', filterLogics: ['and'] },
  permissions: { view: 'fee-rule:view', create: 'fee-rule:create', update: 'fee-rule:update', activate: 'fee-rule:activate' },
  primaryAction: { id: 'create', label: '新增收费规则', permission: 'create' },
  fields: [
    { key: 'keyword', label: '关键词', filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'] } },
    { key: 'ruleCode', label: '规则编号', required: true, sortable: true, minWidth: 135, query: { sort: serverOrder('ruleCode') } },
    { key: 'ruleName', label: '规则名称', required: true, sortable: true, minWidth: 170, query: { sort: serverOrder('ruleName') } },
    { key: 'feeType', label: '费用类型', type: 'select', options: feeTypeOptions, width: 120, sortable: true, groupable: true,
      query: { filter: serverFilter('feeType'), sort: serverOrder('feeType'), group: serverOrder('feeType') } },
    { key: 'calculationType', label: '计价方式', type: 'select', options: calculationTypeOptions, minWidth: 145, query: { filter: serverFilter('calculationType') } },
    { key: 'billingCycle', label: '收费周期', type: 'select', options: billingCycleOptions, width: 115 },
    { key: 'fixedAmount', label: '固定金额', type: 'money', width: 125, align: 'right' },
    { key: 'unitPrice', label: '单价', type: 'money', width: 120, align: 'right' },
    { key: 'effectiveFrom', label: '生效日期', type: 'date', width: 120, sortable: true, query: { sort: serverOrder('effectiveFrom') } },
    { key: 'effectiveTo', label: '失效日期', type: 'date', width: 120 },
    { key: 'currentVersionNo', label: '当前版本', type: 'number', decimals: 0, width: 105, align: 'center' },
    { key: 'status', label: '状态', type: 'status', options: feeRuleStatusOptions, width: 105, sortable: true, groupable: true,
      query: { filter: serverFilter('status'), sort: serverOrder('status'), group: serverOrder('status') } },
    { key: 'description', label: '说明', minWidth: 180 },
    { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', width: 165, sortable: true, query: { sort: serverOrder('updatedAt') } }
  ],
  views: [
    { id: 'active-fee-rules', name: '生效规则', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'ACTIVE' }] } },
    { id: 'draft-fee-rules', name: '草稿规则', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'DRAFT' }] } }
  ],
  metrics: [
    { title: '规则总数', field: 'totalCount', type: 'count', desc: '当前筛选范围', tone: 'neutral' },
    { title: '草稿', field: 'draftCount', type: 'count', desc: '待配置或复核', tone: 'warning' },
    { title: '已生效', field: 'activeCount', type: 'count', desc: '当前可用于计费', tone: 'success' },
    { title: '已停用', field: 'disabledCount', type: 'count', desc: '不再参与计费', tone: 'neutral' }
  ],
  table: { structuralColumnCount: 2, groupColumnIndex: 0, defaultPrefs: { fixedColumns: { ruleCode: 'left' }, rowDensity: 'compact' } }
})

export function createBillingPlanWorkspaceConfig({ tenantOptions = [] } = {}) {
  return defineWorkspaceConfig({
    id: 'billing-plans', title: '计费计划', subtitle: '关联租户、合同、租约和收费规则',
    pageKey: '/billing-plans', storageKey: 'dhic-billing-plan-table-view-v1', defaultViewName: '全部计划',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: { view: 'billing-plan:view', create: 'billing-plan:create', update: 'billing-plan:update', terminate: 'billing-plan:terminate' },
    primaryAction: { id: 'create', label: '新增计费计划', permission: 'create' },
    fields: [
      { key: 'keyword', label: '关键词', filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'keyword', operators: ['contains', 'equals'] } },
      { key: 'tenantId', label: '租户', type: 'select', options: tenantOptions, filterOnly: true, fixedOnly: true, query: { mode: 'server', param: 'tenantId', operators: ['equals'] } },
      { key: 'planNo', label: '计划编号', required: true, sortable: true, minWidth: 140, query: { sort: serverOrder('planNo') } },
      { key: 'planName', label: '计划名称', required: true, sortable: true, minWidth: 175, query: { sort: serverOrder('planName') } },
      { key: 'tenantName', label: '租户', sortable: true, groupable: true, minWidth: 160, query: { sort: serverOrder('tenantName'), group: serverOrder('tenantName') } },
      { key: 'contractNo', label: '合同编号', sortable: true, minWidth: 145, query: { sort: serverOrder('contractNo') } },
      { key: 'leaseNo', label: '租约编号', minWidth: 145 },
      { key: 'billingCycle', label: '计费周期', type: 'select', options: billingCycleOptions, width: 115 },
      { key: 'dueRule', label: '到期规则', type: 'select', options: dueRuleOptions, minWidth: 155 },
      { key: 'effectiveFrom', label: '生效日期', type: 'date', width: 120, sortable: true, query: { sort: serverOrder('effectiveFrom') } },
      { key: 'effectiveTo', label: '失效日期', type: 'date', width: 120, sortable: true, query: { sort: serverOrder('effectiveTo') } },
      { key: 'itemCount', label: '规则项', type: 'number', decimals: 0, width: 95, align: 'right' },
      { key: 'estimatedPeriodAmount', label: '预计每期', type: 'money', width: 135, align: 'right' },
      { key: 'status', label: '状态', type: 'status', options: billingPlanStatusOptions, width: 105, sortable: true, groupable: true,
        query: { filter: serverFilter('status'), sort: serverOrder('status'), group: serverOrder('status') } },
      { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', width: 165, sortable: true, query: { sort: serverOrder('updatedAt') } }
    ],
    views: [
      { id: 'active-billing-plans', name: '生效计划', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'ACTIVE' }] } },
      { id: 'draft-billing-plans', name: '草稿计划', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'DRAFT' }] } }
    ],
    metrics: [
      { title: '计划总数', field: 'totalCount', type: 'count', desc: '当前筛选范围', tone: 'neutral' },
      { title: '草稿', field: 'draftCount', type: 'count', desc: '待配置或复核', tone: 'warning' },
      { title: '已生效', field: 'activeCount', type: 'count', desc: '可参与账单生成', tone: 'success' },
      { title: '预计每期', field: 'estimatedPeriodAmount', type: 'money', desc: '当前筛选范围', tone: 'money' }
    ],
    table: { structuralColumnCount: 2, groupColumnIndex: 0, defaultPrefs: { fixedColumns: { planNo: 'left' }, rowDensity: 'compact' } }
  })
}
