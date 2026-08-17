# DHIC 德汇创新中心物业管理系统架构说明


## 一、项目定位

本系统不是普通后台管理系统。

目标：

AI + BI 企业级物业运营平台。


服务对象：

- 园区运营人员
- 物业管理人员
- 财务人员
- 工程人员
- 租户


---

# 二、整体架构


## 后端

Spring Boot 3

Java 21

MySQL 8

Redis

Flyway


## 前端

Vue3

Vite

Element Plus

TypeScript



---

# 三、核心设计思想


## 1. RBAC权限体系


权限由：

菜单权限

+

页面权限

+

按钮权限

+

接口权限

+

未来数据权限


组成。


禁止：

仅通过前端隐藏实现权限。



---

# 四、DataWorkspace架构


系统列表型业务逐步统一到：

DataWorkspace


结构：


DataWorkspace

↓

WorkspaceConfig

↓

业务模块


例如：


设备管理

equipmentWorkspaceConfig


工单管理

workorderWorkspaceConfig


能耗管理

energyWorkspaceConfig



---

# 五、WorkspaceConfig负责


业务差异通过配置实现。


包括：


fields

views

metrics

importExport

batchActions

permissions



禁止：

复制页面实现相同能力。



---

# 六、当前已迁移模块


完成：

设备台账

工单管理

能耗管理
当前平台化能力：

已完成：

- RBAC权限体系
- DataWorkspace基础框架
- WorkspaceConfig配置体系
- MetricPanel指标体系
- ImportExport统一能力
- BatchToolbar批量操作能力



---

# 七、未来迁移方向


计划：


资产管理

停车管理

财务管理

租赁管理

合同管理

招商管理



---

# 八、AI能力方向


未来：

运营日报

风险预警

合同分析

财务分析

能耗分析

智能工单分析



---

# 九、开发原则


稳定优先。

禁止破坏：

数据库

API

RBAC

业务闭环
# 十、架构禁止方向

新增能力优先考虑：

公共组件

配置化

模块复用

禁止：

- 新增业务页面重新开发独立表格框架
- 每个模块重复实现筛选、排序、导入导出
- 使用页面硬编码代替配置
- 使用mock数据模拟业务闭环

新增模块必须优先评估：

是否接入DataWorkspace。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
