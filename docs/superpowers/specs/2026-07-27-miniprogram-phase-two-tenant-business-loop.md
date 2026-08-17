# 小程序第二阶段：租户业务闭环

## 最终边界

- PUBLIC 只读取脱敏会议室、公告和招商公开数据。
- TENANT 负责人使用 `TENANT_COMPANY`，可按 capability 读取企业房间、合同、账单、能耗、车位、会议、工单和访客。
- TENANT 普通员工使用 `SELF`，只读取或操作本人车位、会议、工单和访客。
- 所有范围来自服务端 principal；客户端提交的 `tenantId`、`mobileUserId` 等关系字段不参与绑定。

## 接口与字段

- 聚合：`GET /mobile/my-business/summary`
- 房间：`GET /mobile/my-business/rooms[/{id}]`
- 账单、合同：`GET /mobile/bills[/{id}]`、`GET /mobile/contracts[/{id}]`
- 能耗：`GET /mobile/energy/summary|history|readings|details/{id}`
- 停车：`GET /mobile/parking/company|mine|{id}`
- 会议：公开 `/mobile/public/meeting-rooms`，租户 `/mobile/meetings` 与 `/mobile/meetings/bookings`
- 工单、访客沿用 `/mobile/workorders`、`/mobile/visitors`
- 列表统一返回 `records/page/pageSize/total`，最大 `pageSize=100`；小程序支持下拉刷新和上拉加载。
- 工单 canonical 字段为 `orderType/location/description/remark/reporterPhone`；访客为 `visitCompany/visitReason/remark`。
- 移动 Schema 过滤关系 ID；允许的 `customFields` 在创建后持久化并在列表/详情回显。

## 数据库

Flyway V48 增加工单 canonical 列、访客来访单位/事由及租户移动查询索引，并兼容回填历史数据；未修改 V47 及以前迁移。

## 验证

- 后端：423 项，412 通过、11 项环境门控跳过、0 失败；compile/package 通过。
- 管理后台：组件 29/29，生产 build 通过。
- 小程序：111/111；相关页面 JS 语法检查通过。
- 当前库和隔离全新 MySQL 8.4 库均执行到 V48；全新库 48 条迁移、0 失败，验证后已删除隔离库。
- HTTP/数据库验证通过 PUBLIC 403、负责人公司范围、员工 SELF、跨租户/跨员工 404、伪造关系字段无效、附件范围、自定义字段落库、撤回/评价/取消，以及同一时段并发预约仅一条成功（200/409）。
- 浏览器确认管理后台 5173 可加载，当前 ADMIN 无权路由正确显示 403，控制台无 error；本阶段未修改管理后台页面。
- 服务保持运行：MySQL 3306、Redis 6379、后端 8080、管理后台 5173。

## 人工验收边界

微信开发者工具中的真实页面编译、真机滚动/图片预览，以及真实微信容器交互仍需人工验收；这不影响已完成的服务端和小程序自动化验证。

## 2026-07-28 账单中心体验补充

- `GET /mobile/bills` 在原分页协议上兼容增加 `category=rent|energy|parking|other`；`status` 明确支持 `UNPAID/PAID/OVERDUE/CANCELLED`，筛选必须在数据库分页前执行。
- 列表兼容返回 `billingPeriod`、`billDate`、`paidAt`、`payChannel`。`paidAt/payChannel` 只取最新有效且状态为 `PAID` 的 `bill_payment_record`，不由客户端推测。
- 默认排序为逾期、未到期待缴、已结清、取消；待缴按最近到期日，已结清按最近账期。租户范围继续只由当前 principal 和有效负责人绑定派生，客户端 `tenantId` 不参与查询。
- 小程序概览仅使用服务端 `summary`；卡片根据 `payStatus/overdue/finalAmount/paidAmount/unpaidAmount` 选择展示口径，不再把已缴账单的 `unpaidAmount=0` 当作主金额。
- 本补充无 Flyway 迁移，不接入或模拟微信支付；缴费按钮仍只展示现有线下缴费说明。
