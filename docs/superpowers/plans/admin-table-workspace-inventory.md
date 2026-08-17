# DHIC 管理后台表格 DataWorkspace 治理清单

- 核验日期：2026-07-20
- Git 分支：`feature/prod-architecture-20260522`
- 基线提交：`248fdd3`
- 清单口径：以 `src/router/index.js`、`src/config/access.js`、页面真实 `request.js` 调用和后端 Controller 为准
- 数据模式：`client` 仅用于接口返回完整数组；`server` 用于真实分页接口

## 1. 覆盖统计

| 类别 | 数量 | 说明 |
| --- | ---: | --- |
| 受保护业务路由 | 31 | 不含 `/login` 和 `/403` |
| 主业务列表 | 26 | 必须接入 DataWorkspace、WorkspaceDataGrid 和独立 WorkspaceConfig |
| 已接入主业务列表 | 10 | 楼宇、楼层、房间、设备、租户、合同、工单、停车账单、表具、抄表 |
| 待迁移主业务列表 | 16 | 本轮治理范围 |
| 分析或报表页面 | 4 | 驾驶舱、财务看板、能耗统计、AI 运营日报 |
| 废弃路由候选 | 1 | `/assets` 后端为 legacy 空列表和 501 mutation，且不在菜单中 |

## 2. 路由与表格清单

| 模块 | 页面 | 路由 | 当前表格 | 数据接口 | 数据模式 | 治理结论 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 驾驶舱 | DashboardView | `/dashboard` | 原生摘要表 | `/bills`、`/contracts`、BI/AI 接口 | 摘要 | 白名单 | 少量固定摘要，无独立筛选、分页或 mutation |
| 资产管理 | BuildingList | `/buildings` | DataWorkspace | `GET /buildings` | client | 复核 | 完整数组；保留 CRUD |
| 资产管理 | FloorList | `/floors` | DataWorkspace | `GET /floors` | client | 复核 | 完整数组；批量生成楼层为页面业务动作 |
| 资产管理 | RoomList | `/rooms` | DataWorkspace | `GET /rooms` | server | 复核并补齐能力 | 必须由服务端执行筛选、排序和分组顺序 |
| 资产管理 | EquipmentList | `/equipment` | DataWorkspace | `GET /equipments` | server | 复核并补齐能力 | 保留导入、导出、状态和批量业务 |
| 资产管理 | AssetList | `/assets` | 原生表 | legacy `/assets` | server 空壳 | 清理死入口 | Controller 只返回空页，写接口返回 501；不在菜单中 |
| 租赁管理 | TenantList | `/tenants` | DataWorkspace + 原生明细表 | `/tenant/list`、tenant overview/contacts | client | 复核 + 明细轻量 Grid | 主表完整数组；联系人有独立 mutation |
| 租赁管理 | LeaseList | `/leases` | 原生表 | `/leases/search`、`/leases/summary` | server | 迁移 | 保留入住、退租和租约状态机 |
| 租赁管理 | FeeRuleList | `/fee-rules` | 原生主表 + 版本明细 | `/fee-rules/search`、`/fee-rules/summary` | server | 迁移 + 明细轻量 Grid | 保留版本查看、生效、停用 |
| 租赁管理 | BillingPlanList | `/billing-plans` | 原生表 | `/billing-plans/search`、`/billing-plans/summary` | server | 迁移 | 保留规则项、激活和终止 |
| 租赁管理 | InvestmentContentList | `/investment/contents` | 原生表 | `/investment/contents` | server | 迁移 | 保留预览、发布、停用、删除 |
| 租赁管理 | InvestmentRoomListingList | `/investment/listings` | 原生主表 + 房间选择表 | `/investment/listings` | server | 迁移 | 房间选择弹窗进入白名单；保留图片管理 |
| 租赁管理 | InvestmentLeadList | `/investment/leads` | 原生表 | `/investment/leads` | server | 迁移 | 保留跟进状态与删除 |
| 租赁管理 | ContractList | `/contracts` | DataWorkspace | `GET /contracts` | client | 复核 | 当前页面使用兼容数组接口；保留完整合同 mutation |
| 财务管理 | BillList | `/bills` | DhicDataToolbar + 原生表 | `/bills`、`/bills/summary` | server | 迁移 | 保留审核、收款、发票、导出和删除保护 |
| 财务管理 | FinanceDashboard | `/finance/dashboard` | 原生摘要表 | `/bills` | 摘要 | 白名单 | 财务 BI 汇总，不是主业务 CRUD 列表 |
| 停车管理 | ParkingSpaceList | `/parking/spaces` | DhicDataToolbar + 原生表 | `/parking/spaces` | server | 迁移 | 保留分配、解绑、月费和状态业务 |
| 停车管理 | ParkingBillList | `/parking/bills` | DataWorkspace | `/parking/bills` | client | 复核 | 当前接口返回完整数组；保留同步、收款、作废 |
| 会议经营 | MeetingRoomList | `/meetings/rooms` | DhicDataToolbar + 原生表 | `/meeting-rooms` | client | 迁移 | 完整数组；保留启停和 CRUD |
| 会议经营 | MeetingBookingList | `/meetings/bookings` | 原生表 | `/meeting-bookings`、`/meeting-bookings/stats` | client | 迁移 | 完整数组；保留确认、取消、完成和计费 |
| 运营管理 | WorkOrderList | `/workorders` | DataWorkspace | `/workorders` | server | 复核并补齐能力 | 保留创建、分配、开始、完成、关闭和批量业务 |
| 运营管理 | VisitorList | `/visitors` | 原生表 | `/visitors` | client | 迁移 | 完整数组；保留入场、离场和取消 |
| 运营管理 | InspectionList | `/inspections` | 原生表 | `/inspections` | server | 迁移 | 保留新增、编辑、关闭和删除 |
| 运营管理 | AnnouncementList | `/announcements` | 原生表 | `/announcements` | client | 迁移 | 完整数组；保留发布和删除 |
| 能耗管理 | EnergyMeterList | `/energy/meters` | DataWorkspace | `/energy/meters` | server | 复核并补齐能力 | 保留表具 CRUD 和真实筛选 |
| 能耗管理 | EnergyRecordList | `/energy/records` | DataWorkspace | `/energy/readings` | server | 复核并补齐能力 | 保留抄表、任务、导入、生成账单和入账 |
| 能耗管理 | EnergyStats | `/energy/stats` | BI + 原生异常明细 | `/energy/stats` | 分析 | 明细轻量 Grid | 指标和图表保留；异常记录统一 Grid，不增加伪分页 |
| AI 分析 | DailyReport | `/ai/daily-report` | 报告 + 原生历史表 | `/ai/daily-report` | 报表 | 历史轻量 Grid | 历史记录用于选择报告，不作为主业务 CRUD 列表 |
| 系统管理 | UserList | `/system/users` | 原生表 | `/system/users` | server | 迁移 | 敏感字段不得进入字段管理；保留角色、状态和密码操作 |
| 系统管理 | MobileUserList | `/system/mobile-users` | 原生表 | `/mobile-users` | server | 迁移 | 保留租户绑定和解绑 |
| 系统管理 | RoleList | `/system/roles` | 原生表 | `/system/roles` | server | 迁移 | 权限树弹窗不属于数据主表；保留系统角色保护 |

## 3. 原生表格例外原则

迁移完成后仅允许以下类型保留原生 `el-table`：

1. Dashboard 或 BI 页面中的少量固定摘要记录。
2. 不带独立筛选、分页和业务 mutation 的小型摘要表。
3. 弹窗中的临时选择表，且不会被误认为完整业务列表。
4. `WorkspaceDataGrid.vue` 内部对 Element Plus 表格的封装实现。

所有具体例外由 `tableWorkspaceLegacyAllowlist` 登记并由自动化测试核对。具有独立 mutation、分页或查询能力的明细表不得通过白名单规避迁移。

## 4. 实施顺序

1. 复核现有 10 个 DataWorkspace 页面，修正五项能力和服务端查询范围。
2. 迁移租约、收费规则、计费计划及招商三页。
3. 迁移访客、巡检和公告。
4. 迁移车位、会议室和会议预约。
5. 迁移财务账单，复核能耗统计明细。
6. 迁移系统用户、小程序用户和角色。
7. 转换租户联系人、收费规则版本、能耗异常和 AI 历史等独立明细表。
8. 建立路由覆盖、Config 完整性、原生表格白名单、权限和服务端语义守门测试。

## 5. 不变量

- 页面仍负责真实 API、弹窗、表单校验和业务 mutation。
- WorkspaceConfig 只声明字段、查询能力、显示元数据和基础权限映射。
- 不修改 permission code，不按角色名称判断权限。
- 不修改已执行 Flyway migration，不写生产数据库。
- server 页面不允许当前页伪筛选、伪排序或伪分组。
- 不为统一外观增加不存在的导入、导出、批量操作或指标。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
