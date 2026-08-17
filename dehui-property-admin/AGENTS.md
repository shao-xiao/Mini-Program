

文件位置：

```text
dehui-property-admin/AGENTS.md
```

作用范围：

只约束前端 Vue3 管理后台。

请复制全部内容保存。

---

````md
# DHIC 德汇创新中心物业管理系统前端开发规范

本目录：

```text
dehui-property-admin
````

为 DHIC 物业管理系统 Vue3 管理后台。

本项目不是 Demo 页面，而是正在演进中的企业级物业 SaaS 前端系统。

任何 AI Agent、Codex、自动化编码工具，在修改本目录代码前，必须遵守本文件。

---

# 一、前端技术栈

当前技术：

```text
Vue 3
Vite
TypeScript
Element Plus
Axios封装请求
Vue Router
Pinia（已安装，禁止随意新增状态逻辑）
```

---

# 二、前端总体架构

系统采用：

```text
页面层

↓

业务组件层

↓

公共能力层

↓

request.js

↓

后端API
```

禁止：

* 页面直接调用 axios
* 页面直接调用 fetch
* 创建重复请求封装
* 绕过公共组件

---

# 三、核心设计思想

## 1. DataWorkspace优先

列表型业务页面优先接入：

```text
DataWorkspace
```

结构：

```text
WorkspaceConfig

↓

DataWorkspace

↓

业务页面
```

例如：

设备：

```text
equipmentWorkspaceConfig
```

工单：

```text
workorderWorkspaceConfig
```

能耗：

```text
energyWorkspaceConfig
```

禁止：

为新模块重新开发独立表格系统。

---

# 四、WorkspaceConfig规范

业务差异通过配置实现。

配置包含：

```text
fields

views

metrics

importExport

batchActions

permissions
```

例如：

字段：

```text
设备名称
设备编号
状态
位置
厂家
```

通过配置生成：

* 表格列
* 搜索条件
* 字段显示
* 排序
* 分组

---

# 五、公共组件规范

公共组件包括：

```text
DataWorkspace

FieldManager

CellRenderer

MetricPanel

ImportExportManager

BatchToolbar

PermissionTree
```

新增功能必须优先判断：

是否应该进入公共组件。

禁止：

复制已有页面代码。

---

# 六、UI设计规范

整体风格：

参考：

```text
飞书多维表格
企业级数据工作台
```

要求：

* 高信息密度
* 减少无意义空白
* 表格优先展示业务数据
* 操作路径短
* 保持模块统一

禁止：

* 大量卡片堆叠
* 过度留白
* 每个页面独立设计风格

---

# 七、数据请求规范

所有接口必须使用：

```javascript
import request from '@/utils/request'
```

禁止：

```javascript
axios.get()

fetch()
```

请求必须：

* 使用真实后端接口
* 不使用mock数据
* 不虚构接口

---

# 八、接口开发原则

修改页面前：

必须先确认：

1. 后端接口是否存在
2. 请求参数
3. 返回结构
4. 权限要求

禁止：

为了页面展示自行创建假字段。

---

# 九、权限规范

系统采用真实RBAC。

权限包括：

```text
菜单权限

页面权限

按钮权限

接口权限

未来数据权限
```

前端只能负责：

展示控制。

例如：

```javascript
hasPermission('asset:create')
```

禁止：

通过前端假角色判断：

```javascript
if(role==='ADMIN')
```

禁止：

硬编码角色权限。

---

# 十、按钮权限规范

新增按钮必须绑定权限。

例如：

新增：

```text
xxx:create
```

编辑：

```text
xxx:update
```

删除：

```text
xxx:delete
```

导出：

```text
xxx:view
```

禁止：

所有用户默认显示按钮。

---

# 十一、表格页面规范

列表页面优先使用：

DataWorkspace。

统一支持：

* 筛选
* 排序
* 分组
* 字段管理
* 固定列
* 行高
* 导入
* 导出
* 批量操作

禁止：

每个页面重新实现：

搜索栏

表格

分页

字段配置。

---

# 十二、日期规范

所有日期格式统一：

```text
YYYY-MM-DD
```

Element Plus：

必须使用：

```html
value-format="YYYY-MM-DD"
```

禁止：

页面自行转换多种日期格式。

---

# 十三、状态管理规范

Pinia已存在。

但是：

禁止为了单页面数据新增Store。

优先：

* 页面状态
* 组件状态
* request缓存

只有跨模块共享状态才使用Pinia。

---

# 十四、修改规则

修改前：

必须：

1. 阅读当前组件
2. 阅读相关config
3. 阅读API调用
4. 确认权限

修改时：

要求：

* 小范围修改
* 保留已有功能
* 不删除业务逻辑
* 不改变接口结构

修改后：

必须：

执行：

```bash
npm run build
```

---

# 十五、测试要求

前端修改完成：

必须验证：

```bash
npm run build
```

涉及权限：

必须验证：

* 有权限账号
* 无权限账号
* 管理员账号

涉及列表：

必须验证：

* 查询
* 分页
* 导入导出
* 按钮显示

---

# 十六、禁止事项

严禁：

* mock数据
* 虚构接口
* 直接axios请求
* fetch请求
* 硬编码token
* 硬编码角色
* 删除权限判断
* 绕过request.js
* 新建重复表格框架
* 破坏DataWorkspace架构

---

# 十七、当前重点模块

核心公共能力：

```text
DataWorkspace

FieldManager

CellRenderer

MetricPanel

ImportExportManager

BatchToolbar

PermissionTree
```

未来新增列表型业务：

优先接入：

DataWorkspace。

---

# 十八、AI Agent修改流程

修改代码前：

必须：

1. 阅读根目录AGENTS.md
2. 阅读ARCHITECTURE.md
3. 阅读当前目录规则
4. 分析已有代码

修改完成后输出：

* 修改文件
* 修改原因
* 测试结果
* 风险说明





## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
