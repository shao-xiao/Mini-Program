# DHIC 全局自定义字段平台实施计划

## 1. 基线与目标

- 基线提交：`a5e3814 refactor: finalize data workspace governance baseline`
- 实施分支：`codex/global-custom-fields`
- 当前已有 V18 `custom_field_definition` / `custom_field_value`，以及少量页面级接线。
- 本项目在 V18 上原位升级，不创建第二套并行动态字段系统。
- 用户创建字段时绝不执行运行时 `ALTER TABLE` 业务表。
- 字段定义属于 Workspace 共享结构；字段显示、顺序、固定和行高仍属于个人视图偏好。

完成后，正式 DataWorkspace 主表可以创建、管理、显示、编辑、筛选、排序、分组和导出自定义字段。原生业务字段、状态机、金额和权限真值仍由原业务模型负责。

## 2. 现状缺口

V18 能保存简单定义和值，但存在以下边界：

1. Controller 直接持有 JdbcTemplate SQL，没有应用服务和仓储边界。
2. 字段值全部放在 `value_json`，数字、金额和日期无法可靠索引或按类型排序。
3. 定义没有选项实体、版本、变更历史、必填和类型配置。
4. 允许模块硬编码为 10 个，未覆盖正式 Workspace 清单。
5. 字段创建依赖模块 create 权限，没有独立字段管理权限，也没有“双权限”校验。
6. server 分页页面无法按自定义字段做全量筛选、排序和分组。
7. 前端只有少量页面自己加载定义和值，存在重复接线风险。
8. 旧页面创建字段时先写本地视图，再异步请求后端，失败时状态可能不一致。

## 3. 数据模型

### 3.1 custom_field_definition

保留 V18 主键、`module`、`field_key`、`label` 等兼容列，新增并回填：

- `workspace_key`
- `entity_type`
- `description`
- `required`
- `unique_value`
- `config_json`
- `version`
- `created_by` / `updated_by` 沿用
- `status` / `deleted` 沿用

`field_key` 继续作为稳定 fieldCode。创建后不允许修改；显示名称可以修改。

### 3.2 custom_field_option

单选、多选使用稳定 optionCode：

- `field_id`
- `option_code`
- `option_label`
- `color`
- `display_order`
- `status`
- 审计字段与逻辑删除

已使用选项只能停用，不能物理删除。

### 3.3 custom_field_value

保留 V18 `module`、`record_id`、`field_key`、`value_json` 兼容列，新增：

- `field_id`
- `workspace_key`
- `entity_type`
- `value_text`
- `value_long_text`
- `value_number`
- `value_decimal DECIMAL(18,2)`
- `value_date`
- `value_datetime`
- `value_boolean`
- `version`

一个字段与一个实体最多一条有效值。应用层按字段类型只写一个类型列，同时维护 `value_json` 供旧调用兼容。金额使用 BigDecimal。

### 3.4 custom_field_definition_history

记录：

- fieldId
- operation
- beforeSnapshot
- afterSnapshot
- operatorId
- occurredAt
- requestId

历史只保存定义和配置，不记录业务敏感值。

## 4. 字段类型

第一期真实开放：

- TEXT
- LONG_TEXT
- NUMBER
- CURRENCY
- SINGLE_SELECT
- MULTI_SELECT
- DATE
- DATETIME
- BOOLEAN
- PHONE
- EMAIL
- URL

预留但不在创建 UI 暴露：FORMULA、RELATION、LOOKUP、ATTACHMENT、USER、AUTO_NUMBER。

已有小写/驼峰类型由兼容映射升级，不要求一次性重写历史记录。

## 5. Workspace 注册表

后端 `CustomFieldWorkspaceRegistry` 是 workspaceKey 真值，至少包含当前守门测试中的 26 个正式主表：

- buildings
- floors
- rooms
- equipment
- tenants
- leases
- fee-rules
- billing-plans
- investment-leads
- investment-contents
- investment-listings
- contracts
- bills
- parking-spaces
- parking-bills
- meeting-rooms
- meeting-bookings
- workorders
- visitors
- inspections
- announcements
- energy-meters
- energy-records
- system-users
- mobile-users
- system-roles

每项声明：workspaceKey、entityType、真实业务表、ID 列、viewPermission、updatePermission、queryMode、最大字段数和是否支持服务端自定义字段查询。客户端不能提交表名或 SQL 表达式。

## 6. 权限

新增：

- `custom-field:view`
- `custom-field:manage`

读取定义和值必须拥有对应 Workspace 的 view 权限。字段管理必须同时拥有 `custom-field:manage` 和 Workspace update/manage 权限。字段值编辑使用 Workspace update 权限。

权限判断只使用 permission code，不使用角色名称。SUPER_ADMIN 的 `*` 兼容现有全权限语义；ADMIN 只能使用数据库实际授予的权限。

## 7. 后端分层与 API

### 7.1 分层

- `CustomFieldDefinitionController`
- `CustomFieldValueController`
- `CustomFieldApplicationService`
- `CustomFieldQueryService`
- `CustomFieldRepository`
- `CustomFieldWorkspaceRegistry`
- `CustomFieldQueryCompiler`

Controller 不直接写 JdbcTemplate SQL。旧 `/custom-fields/{module}` 路径保留兼容并委托服务。

### 7.2 新 API

- `GET /workspaces/{workspaceKey}/custom-fields`
- `POST /workspaces/{workspaceKey}/custom-fields`
- `PUT /workspaces/{workspaceKey}/custom-fields/{fieldId}`
- `POST /workspaces/{workspaceKey}/custom-fields/{fieldId}/enable`
- `POST /workspaces/{workspaceKey}/custom-fields/{fieldId}/disable`
- `DELETE /workspaces/{workspaceKey}/custom-fields/{fieldId}`（逻辑删除）
- `POST /workspaces/{workspaceKey}/custom-fields/reorder`
- `GET /workspaces/{workspaceKey}/records/{entityId}/custom-fields`
- `PUT /workspaces/{workspaceKey}/records/{entityId}/custom-fields`
- `POST /workspaces/{workspaceKey}/custom-field-values/batch-read`
- `PUT /workspaces/{workspaceKey}/custom-field-values/batch-update`

批量读取一次覆盖当前页，禁止逐行请求。批量更新限制条数并逐项返回结果。

## 8. 查询语义

### client 页面

完整加载原生数组后，按当前页 ID 集合批量加载字段值并合并到 `record.customFields`。公共 QueryPlan 使用预编译访问器进行类型化筛选、排序和分组。

### server 页面

QueryPlan 将自定义字段条件序列化到安全结构，例如 `customFilters`、`customSorts`、`customGroupBy`。后端先按 workspaceKey 和 fieldCode 读取定义，再使用 fieldId、固定类型列和参数化 SQL 编译 EXISTS/JOIN。fieldCode、操作符、方向和类型都不能直接拼接 SQL。

`total` 与列表使用同一 WHERE；分组表示排序分段，不做 SQL 聚合；分组字段优先排序，显式排序随后，同字段去重。

## 9. 前端公共能力

WorkspaceConfig 增加：

```js
customFields: {
  enabled: true,
  workspaceKey: 'equipment'
}
```

DataWorkspace 局部 controller 负责：

- 加载/刷新定义
- 当前页值批量加载
- 将共享字段并入 columns
- 字段创建、更新、停用和重排
- 值保存与错误恢复

FieldManager 只负责展示和触发管理动作。创建成功后再修改当前视图，失败时不留下本地假字段。字段定义不会保存在 localStorage；视图只保存稳定 fieldCode。

公共组件：

- CustomFieldDefinitionDialog
- CustomFieldOptionEditor
- CustomFieldCellRenderer
- CustomFieldCellEditor
- CustomFieldRecordDrawer / CustomFieldFormSection

## 10. 视图兼容

- 旧 `customColumns` 视图结构继续读取，但服务器定义优先。
- 已删除或停用字段从可配置列中过滤，不使旧视图白屏。
- 新字段默认追加到创建者当前视图末尾，不覆盖其他用户已保存视图。
- 重命名只改变 label，视图 key 不变。
- 重新启用后字段可重新加入视图。

## 11. 导入导出

导出按当前视图字段顺序包含可见自定义字段；server 页面必须使用完整查询结果，不能只导出当前页。只有已有导入能力的 Workspace 扩展字段映射，不给其他页面虚构导入入口。

## 12. 性能与安全

- 定义按 workspaceKey 缓存，修改后失效。
- 当前页字段值一次批量查询，无 N+1。
- 每 Workspace 默认最多 50 个启用字段。
- 选项、长文本、批量读写均设后端上限。
- entityId 必须在注册的业务表中真实存在。
- 所有动态操作符、类型、方向和 Workspace 都使用白名单。
- 配置 DTO 严格校验，禁止脚本、HTML、SQL 和可执行公式。
- 乐观锁保护定义和值并发更新。

## 13. 实施阶段与提交

1. `feat: add custom field storage foundation`
   - V39 增量 migration
   - 类型、注册表、迁移/注册合同测试
2. `feat: add custom field definition and value APIs`
   - repository/service/controller
   - 权限、审计、值校验和兼容 API
3. `feat: support custom fields in workspace queries`
   - QueryPlan 安全序列化
   - server 查询编译与 total 一致性
4. `feat: add custom field management UI`
   - FieldManager 真实定义管理
5. `feat: add custom field cell editing`
   - 公共 renderer/editor/drawer 与值批量加载
6. `feat: integrate custom fields across admin workspaces`
   - 26 个正式主表显式启用并登记查询/导出状态
7. `test: enforce global custom field coverage`
   - 全局守门、性能和浏览器验收

每阶段只暂存本阶段文件，不使用 `git add .`。

## 14. 测试矩阵

后端覆盖字段定义生命周期、类型校验、选项、最大数量、权限、审计、批量值、实体越权、乐观锁、软删除、server 筛选/排序/分组、total、非法字段/操作符和 SQL 注入输入。

前端覆盖创建弹窗、失败保留表单、重复提交、字段即时出现、视图隔离、重命名/停用、类型化编辑、server/client 查询、401/403、无权限只读和全局 Workspace 注册守门。

全量命令：

```text
dehui-property-management: mvn -q test; mvn -q -DskipTests compile; mvn -q package
dehui-property-admin: node --test; npm run test:components; npm run benchmark:workspace; npm run build
root: git diff --check
```

## 15. 上线与回滚

- V39 只做向前兼容增量，不删除 V18 列和数据。
- 部署前备份四张自定义字段表及 `flyway_schema_history`。
- 先部署 migration 与兼容 API，再启用新 UI；旧 API 在过渡期可继续工作。
- 回滚应用版本时，V39 新列和表保留，不做生产 DROP。
- 字段定义和值只软删除；生产问题通过停用功能入口和回滚应用处理。

## 16. 完成判定

只有数据库持久化、权限、审计、真实值编辑、client/server 查询、视图兼容、导出、全局守门、性能测试和代表页面浏览器验收均有证据时，才能声明平台完成。未实现的字段类型不得出现在创建 UI。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
