# AGENTS.md

# DHIC 德汇创新中心物业管理系统开发守则

本项目是 DHIC 德汇创新中心物业管理系统，已经不是 Demo 项目，而是正在向“企业级 AI + BI 物业运营平台”演进的真实业务系统。

任何 AI Agent、Codex App、Continue、自动化编码工具，在修改本项目代码前，必须先阅读并遵守本文件。

---

# 一、项目位置

Windows 开发路径：

```text
D:\projects\dehui-property-vscode
```

项目根目录下包含：

```text
dehui-property-management     # 后端 Spring Boot 项目
dehui-property-admin          # 前端 Vue3 项目
```

AGENTS.md 文件必须放在：

```text
D:\projects\dehui-property-vscode\AGENTS.md
```

---

# 二、项目总览

项目名称：

DHIC 德汇创新中心物业管理系统

业务定位：

面向写字楼/园区的物业运营管理平台，覆盖：

- 楼宇管理
- 楼层管理
- 房间管理
- 租户管理
- 租约管理
- 合同管理
- 账单管理
- 收费规则
- 停车管理
- 能耗管理
- 工单管理
- 巡检管理
- 访客管理
- 公告管理
- 用户与角色权限
- Dashboard BI 驾驶舱
- AI 运营分析

当前系统已经具备：

- 真实 RBAC 权限体系
- 真实用户系统
- 真实角色系统
- 真实后端权限控制
- 真实合同/账单/停车/楼宇数据
- Dashboard BI 基础版
- AI 运营摘要与分析入口

---

# 三、技术栈

## 后端

```text
Spring Boot 3
Java 21
Maven
JPA
H2 Database（当前开发环境）
```

后端核心模块：

```text
building
room
tenant
lease
contract
bill
feerule
parking
energy
system
aiassistant
workorder
visitor
inspection
announcement
equipment
```

---

## 前端

```text
Vue 3
Vite
Element Plus
axios
vue-router
pinia 已安装但不要随意引入新状态管理逻辑
```

前端核心约定：

```text
所有请求统一走 src/utils/request.js
baseURL = /api
Authorization 使用原始 token，不加 Bearer
日期格式统一 YYYY-MM-DD
禁止 mock 数据
禁止虚构接口
```

---

# 四、最重要开发原则

任何修改必须遵守：

```text
先看后端接口，再写前端页面
前端不得虚构接口
不使用 mock 数据
所有请求走 request.js
日期格式统一 YYYY-MM-DD
后端修改必须谨慎
每完成稳定阶段及时 git commit
```

对于 AI Agent：

```text
不要假设接口存在
不要自己发明字段
不要自己创造 DTO
不要为了页面好看写假数据
不要绕过 request.js
不要破坏 RBAC 权限逻辑
不要把真实权限改成前端假权限
不要删除已有业务逻辑
```

---

# 五、当前权限模型

角色：

```text
ADMIN
MANAGER
FINANCE
STAFF
SECURITY
CLEANER
```

登录返回格式：

```json
{
  "token": "...",
  "userId": 1,
  "username": "admin",
  "roles": ["ADMIN"]
}
```

前端角色来源：

```js
localStorage.getItem('roles')
```

其中 `roles` 是 JSON 字符串数组，例如：

```json
["ADMIN"]
["FINANCE"]
["MANAGER"]
```

不要假设存在 `userStore`。

---

# 六、request.js 约定

前端请求必须使用：

```js
import request from '../../utils/request'
```

或：

```js
import request from '@/utils/request'
```

具体以 Vite alias 配置为准。

所有 Dashboard、BI、权限敏感接口建议使用：

```js
{ silent: true }
```

例如：

```js
await request.get('/bills', { silent: true })
```

---

# 七、Dashboard 当前状态

Dashboard 已进入 BI 基础版。

当前已接入真实接口：

```text
GET /bills
GET /contracts
GET /buildings
GET /buildings/{id}/stats
GET /parking/spaces/stats
GET /parking/bills/stats
GET /ai/daily-report
GET /ai/analysis
GET /ai/workorders/analysis
```

当前 Dashboard 已具备：

```text
财务 BI
楼宇租赁 BI
停车 BI
合同到期预警
最近账单
租户财务汇总
AI 运营摘要
AI 工单分析
```

---

# 八、开发规则

必须：

- 使用真实接口
- 使用 Promise.allSettled
- 失败接口不能影响整体 Dashboard
- FINANCE 不请求 AI 接口
- AI 接口仅 ADMIN、MANAGER 请求
- 不引入 mock
- 不虚构图表数据

---

# 九、后端修改规则

默认优先修改前端。

后端修改必须谨慎，因为当前后端已经支撑完整业务闭环。

后端编译命令：

```bash
cd dehui-property-management
mvn -q -DskipTests compile
```

不要随意：

- 删除字段
- 重命名接口
- 修改返回结构
- 修改权限拦截器
- 修改登录返回格式
- 修改 token 规则

---

# 十、前端修改规则

前端修改必须遵守：

```text
所有接口走 request.js
不使用 axios 直连
不使用 fetch
不写 mock
不硬编码 token
不硬编码 role
不虚构接口
日期 value-format="YYYY-MM-DD"
```

前端构建命令：

```bash
cd dehui-property-admin
npm run build
```

---

# 十一、Git 仓库

当前远程仓库：

```text
https://github.com/jwgit2467/dehui-property-vscode
```

稳定阶段必须提交：

```bash
git status
git add .
git commit -m "feat: 描述本次稳定功能"
git push
```

---

# 十二、禁止事项

严禁：

- 使用 mock 数据
- 虚构后端接口
- 绕过 request.js
- 直接写死角色
- 直接写死 token
- 删除 RBAC 逻辑
- 删除 AuthInterceptor
- 修改登录返回结构
- 让 FINANCE 访问 AI 接口
- 将 Dashboard 改回 Demo 页面

---

# 十三、验证清单

前端：

```bash
cd dehui-property-admin
npm run build
```

后端：

```bash
cd dehui-property-management
mvn -q -DskipTests compile
```

Dashboard 检查：

```bash
grep -R "parking/spaces/stats\|parking/bills/stats\|buildings/.*/stats\|ai/analysis\|workorders/analysis" -n src/views/dashboard
```

---

# 十四、开发完成标准

一个任务只有同时满足以下条件，才算完成：

- 代码已修改
- 没有 mock
- 没有虚构接口
- npm run build 通过
- 如改后端，mvn compile 通过
- 权限逻辑没有破坏
- 页面数据来自真实后端
- 失败接口不会拖垮整个页面
- 已 git commit

---

# 十五、项目方向

本项目的发展方向是：

```text
AI + BI 企业级物业运营平台
```

未来重点：

- 运营驾驶舱
- 财务分析
- 租赁分析
- 停车分析
- 能耗分析
- AI 风险预警
- AI 运营日报
- 智能工单分析
- 生产级 MySQL 部署
