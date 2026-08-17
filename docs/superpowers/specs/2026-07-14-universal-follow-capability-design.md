# 通用数据关注能力设计

## 1. 目标与边界

为设备、合同、客户/租户、工单提供同一套“当前用户关注业务记录”的持久化能力。

本文件只定义后续实现契约。本阶段不创建数据库迁移、不增加接口、不修改 RBAC，也不在前端使用 localStorage 模拟关注状态。

## 2. 数据模型建议

建议后续通过独立 Flyway 迁移新增 `sys_user_follow`：

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 当前后台用户 ID，由登录 token 解析 |
| `entity_type` | 受控枚举：`EQUIPMENT`、`CONTRACT`、`TENANT`、`WORKORDER` |
| `entity_id` | 对应业务记录 ID |
| `created_at` | 关注时间 |

约束与索引：

- 唯一约束：`(user_id, entity_type, entity_id)`，保证重复关注幂等。
- 查询索引：`(user_id, entity_type, created_at)`，支持“我的关注”视图。
- 不保存业务记录快照，避免名称、状态等字段过期。
- 业务记录删除时允许服务层清理关联；不得由前端直接操作关联表。

## 3. 后端契约建议

后续接口统一放在当前用户语义下，例如：

- `GET /system/user/follows?entityType=EQUIPMENT&entityIds=1,2,3`：返回当前页记录的关注状态。
- `GET /system/user/follows?entityType=EQUIPMENT&page=1&size=20`：返回当前用户关注的实体 ID 分页。
- `POST /system/user/follows`：请求体只包含 `entityType`、`entityId`。
- `DELETE /system/user/follows/{entityType}/{entityId}`：取消当前用户关注。

安全规则：

1. `user_id` 必须由 token 对应的当前用户获取，禁止接收前端提交的用户 ID。
2. `entity_type` 使用后端白名单，不接受任意表名或类名。
3. 关注或读取实体前校验实体存在，并校验对应查看权限，例如设备使用 `asset:view`、合同使用合同查看权限、工单使用 `workorder:view`。
4. 关注不会扩大数据访问范围；失去实体查看权限后，即使关联仍存在也不能返回业务数据。
5. 接口继续由现有 `AuthInterceptor` 和 permission code 保护，不引入角色名称判断。

## 4. 列表与视图集成

业务列表后续可通过已有查询接口增加明确的可选条件 `followedByCurrentUser=true`，或由服务层根据关注实体 ID 做分页关联查询。不能只过滤浏览器当前页，否则“我的关注”视图数量和分页会失真。

返回记录可增加派生字段 `followed`，用于 `CellRenderer` 或关注按钮展示。该字段只描述当前登录用户，不进入业务实体核心字段。

前端接入时：

- 关注按钮仍通过对应业务查看权限控制。
- “我的关注”只有在真实接口和全量分页查询可用后才显示。
- 视图中只保存筛选条件 `followedByCurrentUser=true`，不保存关注记录 ID 列表。
- 切换用户、退出登录或刷新页面时从接口重新读取，不把 localStorage 作为关注真值。

## 5. 分阶段实施

1. 审核表名、实体类型白名单和各模块查看 permission code。
2. 单独新增 Flyway 迁移、Repository/Service、接口和后端权限测试。
3. 为设备列表增加真实的关注状态与“我的关注”分页查询，验证后再推广合同、租户和工单。
4. 最后在工作台增加通用关注单元格/按钮，并补充刷新、越权、记录删除和重复关注测试。

本阶段没有执行上述迁移和接口开发，现有数据库、API、RBAC 与设备业务流程保持不变。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
