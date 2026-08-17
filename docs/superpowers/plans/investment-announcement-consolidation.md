# 招商公告模块迁移清单

| 旧菜单 | 旧路由 | 新菜单 | 新路由 | 处理方式 |
| --- | --- | --- | --- | --- |
| 招商内容 | `/investment/contents` | 招商管理 | `/investment/manage?tab=content` | 合并为招商管理的招商展示页签，旧路由保留重定向 |
| 招商房源 | `/investment/listings` | 招商管理 | `/investment/manage?tab=listings` | 合并为招商管理的招商房源页签，旧路由保留重定向 |
| 招商线索 | `/investment/leads` | 招商线索 | `/investment/leads` | 页面与路由保留，仅移动菜单归属 |
| 公告管理 | `/announcements` | 公告管理 | `/investment/announcements` | 页面与接口保留，旧路由保留重定向 |

## 边界

- 合同台账、租户管理、租户入驻、收费规则和计费计划继续属于租赁管理。
- 工单、巡检和访客继续属于运营管理。
- 招商内容、房源、线索和公告继续使用现有 DataWorkspace、WorkspaceConfig、API 与 permission code。
- 菜单迁移只调整 `sys_menu` 和 `sys_role_menu`，不删除业务数据，不扩大角色权限。
- 小程序继续使用现有招商和公告接口，不修改身份体系。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
