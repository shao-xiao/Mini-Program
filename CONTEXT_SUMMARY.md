# 项目交接摘要

## 1. 项目目标

本项目是“德汇创新中心物业管理平台”，目标是形成面向写字楼/园区的物业运营管理系统，覆盖后台管理、Spring Boot 后端服务、微信小程序三端。

当前业务方向不是 Demo，而是逐步演进为“AI + BI 物业运营平台”：

- 后台管理端：给管理员、物业运营、财务、员工使用，管理楼宇、楼层、房间、租户、合同、账单、停车、能耗、工单、访客、会议室、公告、招商、AI 分析等。
- 后端服务：统一提供后台接口和小程序移动端接口，负责权限、业务校验、账单生成/审核、数据持久化。
- 微信小程序：给租户、内部员工、访客使用，支持公告、账单提醒、会议预约、访客预约、工单报修、招商咨询、员工签到等。

近期重点任务是把原“设备台账”升级为新的“空间资产模型”，但旧 `Equipment` 接口和页面必须保留，新增 `Asset` 与 `AssetOperationLog` 作为新资产体系。

## 2. 当前技术栈

后端：

- 路径：`dehui-property-vscode/dehui-property-management`
- Java 21
- Spring Boot 3.2.5
- Spring Web
- Spring Data JPA
- Spring Validation
- Lombok
- Maven
- H2 Database，当前开发环境使用文件数据库
- 统一接口前缀：`/api`
- 本地端口：`8080`

数据库：

- 配置文件：`dehui-property-vscode/dehui-property-management/src/main/resources/application-dev.yml`
- JDBC URL：`jdbc:h2:file:./data/property_dev;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- 用户名：`sa`
- 密码：空
- H2 Console：`http://localhost:8080/api/h2-console`
- JPA：`ddl-auto: update`
- 当前没有独立 SQL/Flyway/Liquibase 迁移目录，表结构主要由 JPA 实体自动更新。

后台前端：

- 路径：`dehui-property-vscode/dehui-property-admin`
- Vue 3
- Vite
- Element Plus
- axios
- vue-router
- pinia 已安装，但现有业务不依赖全局 store
- 本地端口：`5173`
- Vite 代理：`/api -> http://localhost:8080`
- 所有请求必须走 `src/utils/request.js`

微信小程序：

- 推荐工作目录：`dehui-property-vscode/dehui-property-miniprogram`
- 根目录另有一份独立小程序：`dehui-property-miniprogram`，内容结构相似，后续修改前需确认使用哪一份。
- 小程序请求封装：`utils/request.js`
- 小程序接口配置：`config/env.js`
- 当前 activeEnv：`dev`
- dev baseURL：`http://localhost:8080/api`
- prod baseURL：`https://wuye.mingda.com.cn/api`

本地工具：

- Workspace 根目录：`dehui_`
- 本地 Maven：`tools/apache-maven-3.9.9`
- 本地 JDK：`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`

部署/运行：

- 后端本地运行：在 `dehui-property-vscode/dehui-property-management` 执行 `mvn spring-boot:run`
- 后台前端本地运行：在 `dehui-property-vscode/dehui-property-admin` 执行 `npm run dev`
- 后台访问：`http://localhost:5173`
- 后端访问：`http://localhost:8080/api`
- 生产小程序合法域名已按 `https://wuye.mingda.com.cn` 设计。

## 3. 目录结构说明

根目录：

- `CONTEXT_SUMMARY.md`：长期交接文件，后续新会话优先读取。
- `dehui-property.code-workspace`：VS Code 工作区配置。
- `打开项目到VSCode.cmd`：打开项目辅助脚本。
- `tools/apache-maven-3.9.9`：本地 Maven。
- `dehui-property-vscode`：当前主要代码仓库。
- `dehui-property-miniprogram`：根目录独立小程序副本，需与 `dehui-property-vscode/dehui-property-miniprogram` 区分。

主仓库：

- `dehui-property-vscode/README.md`：项目总体说明，当前文件存在编码显示问题，但内容包含三端职责、部署路径、接口约定等。
- `dehui-property-vscode/AGENTS.md`：开发守则，要求使用真实接口、不写 mock、不绕过 `request.js`、谨慎改后端和权限。
- `dehui-property-vscode/MINIPROGRAM_PLAN.md`：小程序建设方向和阶段计划。
- `dehui-property-vscode/dehui-property-management`：Spring Boot 后端。
- `dehui-property-vscode/dehui-property-admin`：Vue 后台管理端。
- `dehui-property-vscode/dehui-property-miniprogram`：推荐使用的微信小程序目录。

后端关键目录：

- `src/main/java/com/dehui/property/PropertyManagementApplication.java`：后端启动类。
- `src/main/java/com/dehui/property/common`：统一返回、基础实体、健康检查、全局异常处理。
- `src/main/java/com/dehui/property/config`：拦截器、Web 配置、初始化数据。
- `src/main/java/com/dehui/property/modules`：业务模块目录。
- `src/main/resources/application.yml`：激活 `dev` profile。
- `src/main/resources/application-dev.yml`：本地 H2、端口、JPA、日志配置。
- `pom.xml`：后端 Maven 配置。

后端主要模块：

- `building`：楼宇、楼层、房间。
- `asset`：新增空间资产模型，当前处于部分实现状态。
- `equipment`：旧设备台账，必须保留。
- `tenant`：租户和租户联系人后台管理。
- `mobile`：微信小程序移动端接口、微信用户、租户联系人绑定、账单、小程序工单/访客/会议/签到。
- `contract`：合同及合同账单生成。
- `bill`：财务账单、审核、收款、小程序可见账单数据来源。
- `feerule`：收费规则和周期出账。
- `parking`：车位和停车账单。
- `energy`：能耗抄表和能耗账单。
- `meeting`：会议室和会议预约。
- `workorder`：工单。
- `visitor`：访客。
- `inspection`：巡检。
- `announcement`：公告。
- `investment`：招商内容和招商线索。
- `system`：后台用户、角色、登录、权限。
- `aiassistant`：AI 日报、问答、提醒、工单分析。

后台前端关键目录：

- `src/router/index.js`：后台路由。
- `src/config/access.js`：菜单和前端角色访问控制。
- `src/utils/request.js`：后台统一 axios 请求封装。
- `src/layout/MainLayout.vue`：后台主布局。
- `src/views/building`：楼宇、楼层、房间、旧设备台账页面。
- `src/views/tenant`：租户、租约、合同、账单、收费规则页面。
- `src/views/finance`：财务看板。
- `src/views/parking`：车位和停车账单。
- `src/views/meeting`：会议室和会议预约。
- `src/views/operation`：工单、访客、巡检、公告。
- `src/views/energy`：能耗抄表和统计。
- `src/views/system`：用户、角色。
- `src/views/ai`：AI 运营日报。

微信小程序关键目录：

- `app.json`：页面注册、窗口、定位权限。
- `app.js`：全局 token/userInfo/identity 初始化。
- `config/env.js`：接口环境配置，可在小程序“我的”页动态保存 baseURL。
- `utils/request.js`：统一 `wx.request` 封装，自动带 `Authorization`。
- `utils/auth.js`：小程序 session 存储/清除。
- `pages/home`：首页入口。
- `pages/me`：登录、身份绑定、接口地址配置、后端联通检查。
- `pages/bills`：租户账单中心。
- `pages/announcements`：公告列表和详情。
- `pages/meeting`：会议预约。
- `pages/workorders`：工单。
- `pages/visitors`：访客。
- `pages/investment`：招商。
- `pages/checkin`：员工签到。

## 4. 已完成功能

后台/权限：

- 后台登录接口：`POST /api/system/login`
- 角色：`ADMIN`、`MANAGER`、`STAFF`、`SECURITY`、`CLEANER`、`FINANCE`
- 前端从 `localStorage.roles` 读取角色数组。
- 后端 `AuthInterceptor` 做后台与小程序接口鉴权。
- `/api/ping` 健康检查已放行。

基础物业：

- 楼宇、楼层、房间基础 CRUD 已有。
- 当前旧接口仍是嵌套路由：
  - `/api/buildings`
  - `/api/buildings/{buildingId}/floors`
  - `/api/buildings/{buildingId}/floors/{floorId}/rooms`
- 后台有楼宇、楼层、房间页面。

旧设备台账：

- 后端旧接口：`/api/equipments`
- 前端旧页面：`dehui-property-vscode/dehui-property-admin/src/views/building/EquipmentList.vue`
- 用户明确要求：不要删除、不要重命名、不要影响旧功能。

租户联系人与小程序绑定：

- 已新增租户联系人机制。
- `TenantContact` 已扩展密码、是否需要重置、最近绑定时间。
- 后台租户页已支持联系人管理。
- 合同创建时，如果填写 `contactPerson/contactPhone/contactEmail`，会同步租户联系人。
- 小程序正式绑定逻辑为手机号 + 初始密码。
- 保留开发模式按租户 ID 直绑。

账单：

- 账单支持创建、查询、审核通过、驳回、确认收款。
- 账单创建时进入 `PENDING` 待审核。
- 审核通过后小程序可见。
- 已修复“账单编号留空保存失败”：
  - `BillCreateRequest.billNumber` 不再强制 `@NotBlank`
  - `BillService.normalizeBillNumber()` 自动生成 `BILL-yyyyMMdd-XXXXXXXX`
  - 全局 validation 异常改为较短中文提示。

小程序：

- 支持开发态登录：`POST /api/mobile/auth/dev-login`
- 支持内部账号绑定：`POST /api/mobile/auth/bind-internal`
- 支持租户绑定：`POST /api/mobile/auth/bind-tenant`
- 支持查看当前身份：`GET /api/mobile/auth/me`
- 支持租户账单：`GET /api/mobile/bills`
- 支持公告、会议、工单、访客、招商、签到等移动端接口。

初始化数据：

- `DataInitializer` 初始化角色、admin 用户、德汇创新中心、楼层、房间、默认能耗规则、开发联调数据。
- 默认 admin：
  - 用户名：`admin`
  - 密码：`123456`
  - 角色：`ADMIN`

## 5. 后端逻辑

统一约定：

- 所有后台和移动端接口都挂在 `server.servlet.context-path=/api` 下。
- 后端统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

鉴权：

- 文件：`dehui-property-vscode/dehui-property-management/src/main/java/com/dehui/property/config/AuthInterceptor.java`
- 后台请求使用 `Authorization` header，前端也兼容发送 `token` header。
- 移动端 token 形如 `MOBILE-uuid`，当前存放在 `MobileAuthService` 的内存 `ConcurrentHashMap`，服务重启后会失效。
- 放行接口包括：
  - `/api/ping`
  - `/api/system/login`
  - `/api/mobile/auth/dev-login`
  - `/api/mobile/dev/fixtures`
  - `/api/mobile/announcements` 的 GET
  - `/api/mobile/investment`
  - `/api/uploads` 的 GET

后台主要控制器：

- `HealthController`：`GET /api/ping`
- `SystemUserController`：登录、用户、角色、修改密码。
- `BuildingController`：`/api/buildings`
- `FloorController`：`/api/buildings/{buildingId}/floors`
- `RoomController`：`/api/buildings/{buildingId}/floors/{floorId}/rooms`
- `EquipmentController`：`/api/equipments`
- `AssetController`：`/api/assets`，新增但尚未完成整体联调。
- `TenantController`：`/api/tenant`，包含租户联系人接口。
- `ContractController`：`/api/contracts`
- `BillController`：`/api/bills`
- `FeeRuleController`：`/api/feerules`
- `ParkingSpaceController`：`/api/parking/spaces`
- `ParkingBillController`：`/api/parking/bills`
- `EnergyController`：`/api/energy`
- `MeetingRoomController`：`/api/meetings/rooms`
- `MeetingBookingController`：`/api/meetings/bookings`
- `WorkOrderController`：`/api/workorders`
- `VisitorRecordController`：`/api/visitors`
- `InspectionController`：`/api/inspections`
- `AnnouncementController`：`/api/announcements`
- `InvestmentContentController`：`/api/investment/contents`
- `InvestmentLeadController`：`/api/investment/leads`
- `AIAssistantController`、`OperationQaController`、`ReminderController`、`WorkOrderAnalysisController`：`/api/ai/**`

移动端主要控制器：

- `MobileAuthController`：`/api/mobile/auth`
- `MobileBillController`：`/api/mobile/bills`
- `MobileAnnouncementController`：`/api/mobile/announcements`
- `MobileMeetingController`：`/api/mobile/meetings`
- `MobileWorkOrderController`：`/api/mobile/workorders`
- `MobileVisitorController`：`/api/mobile/visitors`
- `MobileCheckinController`：`/api/mobile/checkins`
- `MobileInvestmentController`：`/api/mobile/investment`
- `MobileDevController`：`/api/mobile/dev/fixtures`

账单业务流程：

1. 后台创建账单：`POST /api/bills`
2. 账单编号为空时由后端自动生成。
3. 新账单默认：
   - `status=UNPAID`
   - `auditStatus=PENDING`
   - `sourceType=MANUAL`
4. 财务审核通过：`POST /api/bills/{id}/approve`
5. 审核通过后，小程序租户账单中心通过 `/api/mobile/bills` 可见。
6. 后台确认收款：`POST /api/bills/{id}/pay`
7. 小程序当前未接微信支付，只显示线下缴费说明。

租户联系人绑定流程：

1. 后台合同创建或租户联系人管理生成联系人。
2. 联系人有手机号、初始密码、状态。
3. 小程序先开发态登录拿移动端 token。
4. 小程序用手机号 + 初始密码调用 `/api/mobile/auth/bind-tenant`。
5. 成功后 `wechat_user.boundTenantId` 绑定到租户。
6. 小程序账单中心按 `boundTenantId` 查询已审核通过账单。

## 6. 微信小程序对接逻辑

小程序推荐目录：

- `dehui-property-vscode/dehui-property-miniprogram`

请求地址：

- 配置在 `config/env.js`
- `activeEnv='dev'`
- 默认 dev 地址：`http://localhost:8080/api`
- 可在“我的”页保存自定义 baseURL。
- `utils/request.js` 每次请求通过 `getBaseURL()` 读取当前 baseURL。

鉴权方式：

- 小程序登录后把 token 存到 `wxStorage` 的 `token`。
- `utils/request.js` 自动把 token 放到 `Authorization` header。
- 用户信息存储在 `userInfo`。
- 身份存储在 `identity`，包括：
  - `PUBLIC`
  - `TENANT`
  - `INTERNAL`

小程序页面和接口：

- `pages/me`
  - 检查后端：`GET /ping`
  - 获取开发联调数据：`GET /mobile/dev/fixtures`
  - 开发态登录：`POST /mobile/auth/dev-login`
  - 内部账号绑定：`POST /mobile/auth/bind-internal`
  - 租户绑定：`POST /mobile/auth/bind-tenant`
  - 当前用户：`GET /mobile/auth/me`
- `pages/bills`
  - 租户账单列表：`GET /mobile/bills`
  - 可按 `status=UNPAID/PAID/OVERDUE` 筛选。
- `pages/announcements`
  - 公告列表：`GET /mobile/announcements`
  - 公告详情：`GET /mobile/announcements/{id}`
- `pages/meeting`
  - 会议室/预约：`GET /mobile/meetings`、`POST /mobile/meetings/bookings`
  - 取消预约兼容 POST/PATCH。
- `pages/workorders`
  - 工单列表/创建/取消/评价/图片上传：`/mobile/workorders`
- `pages/visitors`
  - 访客预约/取消：`/mobile/visitors`
- `pages/investment`
  - 招商概览和线索：`/mobile/investment`
- `pages/checkin`
  - 员工签到：`/mobile/checkins`

账单提醒数据流：

1. 后台账单必须有 `tenantId`。
2. 后台账单必须审核通过，即 `auditStatus=APPROVED` 或历史账单无审核字段时按已发布兼容。
3. 小程序用户必须绑定为 `TENANT`，且 `boundTenantId` 匹配账单 `tenantId`。
4. `/mobile/bills` 返回该租户账单、汇总金额、待缴/逾期/已缴统计。

## 7. 数据库与资产管理逻辑

当前数据库维护方式：

- 没有独立迁移文件。
- 使用 JPA 实体 + H2 `ddl-auto: update` 自动建表/补字段。
- `BaseEntity` 统一字段：
  - `id`
  - `created_time`
  - `updated_time`
- 部分新空间资产相关实体另行添加 `deletedAt`，尚未完全在服务层打通。

旧资产/设备：

- 旧表：`equipment`
- 实体：`modules/equipment/entity/Equipment.java`
- 字段包括：
  - `equipmentName`
  - `equipmentCode`
  - `equipmentType`
  - `location`
  - `status`
  - `manufacturer`
  - `model`
  - `installDate`
  - `remark`
- 旧接口：`/api/equipments`
- 旧前端页面：`src/views/building/EquipmentList.vue`
- 旧模块必须保留。

新空间资产模型目标关系：

```text
Building -> Floor -> Room -> Asset -> AssetOperationLog
```

Building：

- 表：`building`
- 实体：`modules/building/entity/Building.java`
- 当前已有字段：
  - `buildingName`
  - `buildingCode`
  - `address`
  - `totalFloors`
  - `description`
  - `status`
  - `deletedAt`，已加实体字段但服务层尚未完全按软删除过滤。

Floor：

- 表：`floor`
- 实体：`modules/building/entity/Floor.java`
- 当前字段：
  - `building`
  - `floorNumber`
  - `floorName`
  - `sortOrder`
  - `totalArea`
  - `description`
  - `status`
  - `deletedAt`
- 目标要求：楼层必须绑定楼宇；支持平铺查询 `/api/floors?building_id=1`；支持批量生成 B2、B1、1F 至 9F。

Room：

- 表：`room`
- 实体：`modules/building/entity/Room.java`
- 当前字段：
  - `building`
  - `floor`
  - `roomNumber`
  - `roomName`
  - `area`
  - `roomType`
  - `status`
  - `description`
  - `deletedAt`
- 目标要求：房间必须绑定楼宇和楼层；支持按 buildingId/floorId/status 查询；删除使用软删除。

Room 状态：

- `AVAILABLE`：空置
- `RENTED`：已出租
- `RESERVED`：预留
- `RENOVATING`：装修中
- `DISABLED`：停用

Room 类型：

- `OFFICE`：办公
- `WAREHOUSE`：仓储
- `EQUIPMENT`：设备间
- `PARKING`：车位
- `PUBLIC_AREA`：公区

Asset：

- 表：`asset`
- 实体：`modules/asset/entity/Asset.java`
- 当前已新增实体、Repository、DTO、Service、Controller，但尚未编译验证。
- 字段包括：
  - `assetCode`，唯一
  - `assetName`
  - `assetCategory`
  - `assetType`
  - `manufacturer`
  - `model`
  - `serialNo`
  - `building`
  - `floor`
  - `room`，可选
  - `locationDesc`
  - `status`
  - `installDate`
  - `warrantyStartDate`
  - `warrantyEndDate`
  - `responsiblePerson`
  - `maintenanceCycleDays`
  - `lastMaintenanceDate`
  - `nextMaintenanceDate`
  - `remark`
  - `deletedAt`

Asset 状态：

- `IN_USE`：使用中
- `IDLE`：闲置
- `MAINTENANCE`：维修中
- `DISABLED`：停用
- `SCRAPPED`：报废

AssetOperationLog：

- 表：`asset_operation_log`
- 实体：`modules/asset/entity/AssetOperationLog.java`
- 字段包括：
  - `asset`
  - `operationType`
  - `oldStatus`
  - `newStatus`
  - `oldLocation`
  - `newLocation`
  - `operator`
  - `operationTime`
  - `description`
  - `attachmentUrl`

操作类型：

- `CREATE`：新增资产
- `UPDATE`：修改信息
- `STATUS_CHANGE`：状态变更
- `TRANSFER`：位置转移
- `MAINTENANCE`：维修/维保
- `INSPECTION`：巡检
- `SCRAP`：报废

新 Asset 接口当前已写入代码：

- `GET /api/assets`
- `GET /api/assets/{id}`
- `POST /api/assets`
- `PUT /api/assets/{id}`
- `DELETE /api/assets/{id}`
- `PATCH /api/assets/{id}/status`
- `POST /api/assets/{id}/transfer`
- `POST /api/assets/{id}/maintenance`
- `GET /api/assets/{id}/logs`
- `GET /api/assets/statistics/overview`
- `GET /api/assets/statistics/by-type`
- `GET /api/assets/statistics/by-status`
- `GET /api/assets/statistics/by-floor`
- `GET /api/assets/statistics/warranty-expiring`
- `GET /api/assets/statistics/maintenance-due`

资产逻辑当前状态：

- `AssetService` 已实现基本校验：
  - 资产必须绑定楼宇和楼层。
  - 楼层必须属于楼宇。
  - 房间如果填写，必须属于对应楼宇/楼层。
  - 默认查询排除 `deletedAt != null`。
  - 删除资产写 `deletedAt` 并把状态改为 `DISABLED`。
  - 创建写 `CREATE` 日志。
  - 修改写 `UPDATE` 日志。
  - 状态变更写 `STATUS_CHANGE`；报废状态写 `SCRAP`。
  - 转移写 `TRANSFER`。
  - 维修写 `MAINTENANCE`。
- 尚未完成：
  - 后端编译验证。
  - 楼宇/楼层/房间服务层软删除和过滤。
  - 平铺 `/api/floors`、`/api/rooms` 接口。
  - 前端新资产页面和核心联动。

## 8. 当前问题与未完成事项

当前工作树状态：

- `dehui-property-vscode` 有未提交改动。
- 已新增 `asset` 模块文件。
- 已改动租户联系人、账单编号、楼宇/楼层/房间实体和 DTO。
- `CONTEXT_SUMMARY.md` 在仓库根目录 `dehui_` 下，不在 `dehui-property-vscode` 内。

必须注意：

- 不要回滚已有租户联系人和账单编号修复。
- 不要删除或重命名旧 `Equipment` 接口和页面。
- 不要物理删除空间资产相关数据，目标是统一使用 `deletedAt` 软删除。
- 不要绕过前端 `src/utils/request.js`。
- 不要写 mock 数据。

空间资产模型未完成：

- `BuildingService` 仍有物理删除逻辑：
  - 删除楼宇时如果有楼层直接拒绝，否则 `delete`。
  - 删除楼层时如果有房间直接拒绝，否则 `delete`。
  - 删除房间直接 `delete`。
- `BuildingService` 尚未把新增的 `Floor.sortOrder/description/status/deletedAt`、`Room.building/roomName/description/deletedAt` 全面映射到响应和保存逻辑。
- `DataInitializer` 创建房间时当前只设置了 `floor`，新模型需要同时设置 `building`。
- `FloorController` 和 `RoomController` 当前只有嵌套路由，还没有目标平铺接口。
- `AuthInterceptor` 目前只显式保护 `/api/buildings`，新平铺 `/api/floors`、`/api/rooms`、`/api/assets` 可能需要补权限逻辑。
- `AssetController` 中统计接口写在 `/{id}` 后面，Spring 通常能按精确匹配处理，但后续可考虑把 `/statistics/**` 放到 `/{id}` 前面，降低歧义。
- `AssetService.updateStatus()` 使用 `SCRAPPED` 判断报废，符合当前实体状态，但需确认前端也使用 `SCRAPPED`。
- 后端尚未运行 `mvn -DskipTests compile` 验证。

前端未完成：

- `FloorList.vue` 当前仍调用 `/buildings/{buildingId}/floors`，还未切到 `/floors?building_id=...`。
- `RoomList.vue` 当前硬编码 `BUILDING_ID=1`，还未做楼宇 -> 楼层 -> 房间联动。
- 尚未新增新的 `AssetList.vue` 或新资产路由。
- 菜单 `src/config/access.js` 中资产管理只有楼宇、楼层、房间、设备台账，尚未加入新资产入口。
- 路由 `src/router/index.js` 尚未加入 `/assets`。
- 前端尚未运行 `npm run build` 验证新改动。

小程序当前限制：

- 正式微信登录尚未接入，当前主要靠开发态登录。
- 租户绑定已有后端正式手机号 + 密码逻辑，但小程序 `pages/me/index.js` 当前数据结构仍包含开发直绑字段，需确认 WXML 是否已显示正式密码入口。
- 移动端 token 在后端内存中，服务重启会失效。
- 账单支付未接微信支付，仅线下缴费说明。

缺失/不确定信息：

- 未找到数据库迁移文件。
- 未找到生产部署脚本。
- 未运行后端编译和前端构建。
- 项目 README/AGENTS 部分中文在 PowerShell 输出中有乱码，但核心信息可从代码和已有文档推断。

## 9. 下一步开发计划

优先级 P0：

1. 先运行后端编译，拿到真实编译错误。
2. 修复 `asset` 模块与现有 `building` 模块的编译问题。
3. 更新 `BuildingService`、Repository、Controller：
   - 楼宇/楼层/房间默认查询排除 `deletedAt != null`。
   - 删除楼宇/楼层/房间改为软删除。
   - 房间保存时设置 `building` 和 `floor`。
   - 楼层保存时校验楼宇存在且未软删除。
   - 房间保存时校验楼层属于楼宇。
4. 新增平铺接口：
   - `GET /api/floors?building_id=1`
   - `POST /api/floors`
   - `PUT /api/floors/{id}`
   - `DELETE /api/floors/{id}`
   - `POST /api/floors/batch-generate`
   - `GET /api/rooms?building_id=1&floor_id=2&status=AVAILABLE`
   - `POST /api/rooms`
   - `PUT /api/rooms/{id}`
   - `DELETE /api/rooms/{id}`
5. 补 `AuthInterceptor` 对 `/api/floors`、`/api/rooms`、`/api/assets` 的访问控制。

优先级 P1：

1. 后台前端更新楼层页面：点击楼宇后调用平铺楼层接口。
2. 后台前端更新房间页面：楼宇 -> 楼层 -> 房间联动，移除 `BUILDING_ID=1` 硬编码。
3. 新增最小可用资产页面：
   - 路由 `/assets`
   - 菜单“空间资产”或“资产台账”
   - 列表查询 `/api/assets`
   - 新增资产表单使用楼宇 -> 楼层 -> 房间级联选择。
4. 保持旧 `/equipment` 页面可打开。

优先级 P2：

1. 补资产日志查看入口。
2. 补资产统计页面或看板。
3. 补资产巡检 `INSPECTION` 操作入口。
4. 补小程序侧资产报修/巡检关联能力。

验证命令：

后端：

```powershell
cd dehui-property-vscode/dehui-property-management
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;..\..\tools\apache-maven-3.9.9\bin;$env:Path"
mvn -q -DskipTests compile
```

前端：

```powershell
cd dehui-property-vscode/dehui-property-admin
npm run build
```

后端重启：

```powershell
netstat -ano | Select-String ':8080' | Select-String 'LISTENING'
Stop-Process -Id <PID> -Force

cd dehui-property-vscode/dehui-property-management
mvn spring-boot:run
```

## 10. 给下一个 Codex 会话的启动提示

可复制给新会话：

```text
请先读取 dehui_/CONTEXT_SUMMARY.md，然后继续实现德汇创新中心物业管理平台的空间资产模型。

要求：
1. 不要改业务代码以外的无关文件，不要回滚已有租户联系人和账单编号修复。
2. 保留旧 Equipment 后端接口 `/api/equipments` 和前端 `/equipment` 页面，不删除、不重命名。
3. 新增 Asset/AssetOperationLog 是新空间资产模型；当前 asset 模块部分代码已写入但尚未编译验证。
4. 先运行后端 `mvn -q -DskipTests compile`，根据真实错误修复。
5. 完成 Building/Floor/Room/Asset 的主外键关系、软删除、平铺 `/api/floors` 和 `/api/rooms` 接口。
6. 所有空间资产相关删除统一使用 `deletedAt` 软删除。
7. 新增、修改、状态变更、转移、维修、报废必须写 AssetOperationLog。
8. 前端只做核心联动：楼层按楼宇加载、房间按楼宇和楼层加载、新资产页面按楼宇->楼层->房间选择。
9. 完成后运行后端编译和前端 `npm run build`，并说明验证结果。
```
