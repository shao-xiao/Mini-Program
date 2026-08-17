export const workspaceRoutedTables = [
  entry('资产管理', '楼宇空间管理－房间工作台', '/assets/spaces', 'src/views/building/RoomList.vue', 'roomWorkspaceConfig', '/rooms'),
  entry('资产管理', '设备台账', '/assets/equipment', 'src/views/building/EquipmentList.vue', 'equipmentWorkspaceConfig', '/equipment'),
  entry('招商公告', '招商线索', '/investment/leads', 'src/views/investment/InvestmentLeadList.vue', 'investmentLeadWorkspaceConfig'),
  entry('招商公告', '招商管理－招商展示', '/investment/manage', 'src/views/investment/InvestmentContentList.vue', 'investmentContentWorkspaceConfig', '/investment/contents'),
  entry('招商公告', '招商管理－招商房源', '/investment/manage', 'src/views/investment/InvestmentRoomListingList.vue', 'investmentListingWorkspaceConfig', '/investment/listings'),
  entry('财务管理', '账单管理', '/bills', 'src/views/tenant/BillList.vue', 'billWorkspaceConfig'),
  entry('资产管理', '停车位管理－车位台账', '/assets/parking', 'src/views/parking/ParkingSpaceList.vue', 'parkingSpaceWorkspaceConfig', '/parking/spaces'),
  entry('资产管理', '停车位管理－绑定记录', '/assets/parking', 'src/views/assets/AssetParkingView.vue', 'parkingAssignmentWorkspaceConfig'),
  entry('资产管理', '会议管理－预约工作台', '/assets/meetings', 'src/views/meeting/MeetingBookingList.vue', 'meetingBookingWorkspaceConfig', '/meetings/bookings'),
  entry('运营管理', '工单管理', '/workorders', 'src/views/operation/WorkOrderList.vue', 'workOrderWorkspaceConfig'),
  entry('运营管理', '访客管理', '/visitors', 'src/views/operation/VisitorList.vue', 'visitorWorkspaceConfig'),
  entry('运营管理', '巡检管理', '/inspections', 'src/views/operation/InspectionList.vue', 'inspectionWorkspaceConfig'),
  entry('招商公告', '公告管理', '/investment/announcements', 'src/views/operation/AnnouncementList.vue', 'announcementWorkspaceConfig', '/announcements'),
  entry('资产管理', '能耗管理－表具', '/assets/energy', 'src/views/energy/EnergyMeterList.vue', 'energyMeterWorkspaceConfig', '/energy/meters'),
  entry('资产管理', '能耗管理－抄表记录', '/assets/energy', 'src/views/energy/EnergyRecordList.vue', 'energyReadingWorkspaceConfig', '/energy/records'),
  entry('系统管理', '用户管理', '/system/users', 'src/views/system/UserList.vue', 'systemUserWorkspaceConfig'),
  entry('系统管理', '小程序用户', '/system/mobile-users', 'src/views/system/MobileUserList.vue', 'mobileUserWorkspaceConfig'),
  entry('系统管理', '角色管理', '/system/roles', 'src/views/system/RoleList.vue', 'systemRoleWorkspaceConfig')
]

export const routedWorkspacePages = [
  routedPage('/assets/spaces', 'src/views/assets/AssetSpacesView.vue'),
  routedPage('/assets/equipment', 'src/views/building/EquipmentList.vue'),
  routedPage('/assets/energy', 'src/views/assets/AssetEnergyView.vue'),
  routedPage('/assets/parking', 'src/views/assets/AssetParkingView.vue'),
  routedPage('/assets/meetings', 'src/views/assets/AssetMeetingsView.vue'),
  routedPage('/investment/manage', 'src/views/investment/InvestmentManageView.vue'),
  routedPage('/investment/leads', 'src/views/investment/InvestmentLeadList.vue'),
  routedPage('/bills', 'src/views/tenant/BillList.vue'),
  routedPage('/workorders', 'src/views/operation/WorkOrderList.vue'),
  routedPage('/visitors', 'src/views/operation/VisitorList.vue'),
  routedPage('/inspections', 'src/views/operation/InspectionList.vue'),
  routedPage('/investment/announcements', 'src/views/operation/AnnouncementList.vue'),
  routedPage('/system/users', 'src/views/system/UserList.vue'),
  routedPage('/system/mobile-users', 'src/views/system/MobileUserList.vue'),
  routedPage('/system/roles', 'src/views/system/RoleList.vue')
]

export const routedPageWorkspaceExceptions = [
  exception('/login', 'src/views/Login.vue', '认证入口，不包含业务数据表格'),
  exception('/dashboard', 'src/views/dashboard/DashboardView.vue', '驾驶舱 BI 页面，仅保留少量固定摘要表格'),
  exception('/403', 'src/views/error/ForbiddenView.vue', '权限错误页，不包含业务数据表格'),
  exception('/tenant/workspace', 'src/views/tenant/TenantWorkspaceView.vue', '租户、合同和详情三栏业务工作台，关联明细统一使用 WorkspaceDataGrid'),
  exception('/finance/dashboard', 'src/views/finance/FinanceDashboard.vue', '财务 BI 页面，表格为固定欠费排行摘要'),
  exception('/ai/daily-report', 'src/views/ai/DailyReport.vue', 'AI 日报工作页，表格仅用于选择有限历史快照')
]

export const tableWorkspaceLegacyAllowlist = [
  legacy('src/views/building/AssetList.vue', 1, '遗留空间资产主表', '后端模块明确未实现且无菜单入口', false, '确认无外部链接后删除路由和页面'),
  legacy('src/views/dashboard/DashboardView.vue', 3, '最近账单、合同预警和租户排行', '驾驶舱少量固定 BI 摘要，无独立分页或 CRUD', true, '保留摘要表格'),
  legacy('src/views/finance/FinanceDashboard.vue', 1, '租户欠费排行', '财务驾驶舱固定 BI 摘要，无独立 CRUD', true, '保留摘要表格'),
  legacy('src/views/ai/DailyReport.vue', 1, '历史日报快照选择', '有限历史记录用于切换日报详情，不是主业务台账', true, '数据量或交互增长后再评估轻量 Grid'),
  legacy('src/views/investment/InvestmentRoomListingList.vue', 1, '房间选择弹窗', '弹窗内临时选择器，主招商房源表已使用 DataWorkspace', false, '后续统一弹窗选择器时迁移'),
  legacy('src/views/tenant/TenantList.vue', 4, '联系人和租户详情抽屉明细', '主租户台账已迁移；这些表格属于弹窗维护和只读详情摘要', false, '详情工作台独立产品化时迁移'),
  legacy('src/views/tenant/BillList.vue', 1, '资产计费依据选择器', '财务账单主台账已使用 DataWorkspace；该表只用于一次批量选择待生成账单的依据', false, '统一业务选择器时迁移'),
  legacy('src/views/tenant/finance/BillDetailDrawer.vue', 2, '账单费用明细与发票版本', '只读详情抽屉内展示单张账单的少量费用行和发票历史，不是独立台账', false, '明细独立产品化时迁移'),
  legacy('src/views/operation/InspectionList.vue', 1, '巡检类型维护弹窗', '巡检主台账已使用 DataWorkspace；该表是小规模字典维护器', false, '字典维护器组件统一时迁移'),
  legacy('src/views/energy/EnergyMeterList.vue', 1, '计量主体绑定历史抽屉', '表具主台账已使用 DataWorkspace；该表只展示单个表具的少量绑定历史', false, '绑定历史独立产品化时迁移'),
  legacy('src/views/assets/AssetMeetingsView.vue', 1, '会议收费规则历史抽屉', '会议主台账已使用 DataWorkspace；该表只展示单个会议室的收费规则历史', false, '收费规则历史独立产品化时迁移')
]

export const workspaceCapabilityBlockers = []

function entry(module, page, route, component, config, pageKey = route) {
  return Object.freeze({ module, page, route, component, config, pageKey })
}

function routedPage(route, component) {
  return Object.freeze({ route, component })
}

function exception(route, component, reason) {
  return Object.freeze({ route, component, reason })
}

function legacy(file, occurrences, purpose, reason, isSummary, followUp) {
  return Object.freeze({ file, occurrences, purpose, reason, isSummary, followUp })
}
