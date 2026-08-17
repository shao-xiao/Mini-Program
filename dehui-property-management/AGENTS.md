

文件位置：

```text
prod-architecture-20260522
└── dehui-property-management
    └── AGENTS.md
```

将下面内容完整复制进去。

````md
# DHIC 德汇创新中心物业管理系统后端开发规范

本目录：

```text
dehui-property-management
````

是 DHIC 德汇创新中心物业管理系统的 Spring Boot 后端工程。

本项目不是 Demo，也不是可以随意推翻重构的练习项目。当前后端已经承载真实用户、角色权限、楼宇、租赁、合同、账单、停车、能耗、工单等业务闭环。

任何 AI Agent、Codex、自动化编码工具，在修改本目录代码前，必须先阅读并遵守：

```text
根目录 AGENTS.md
根目录 ARCHITECTURE.md
根目录 CONTEXT_SUMMARY.md
当前目录 AGENTS.md
```

---

# 一、后端技术栈

当前技术栈：

```text
Spring Boot 3
Java 21
Maven
MySQL 8
Redis
Flyway
JdbcTemplate
Spring Data JPA
```

数据库访问方式按现有模块实际结构使用。

禁止为了统一技术栈而：

* 将现有 JdbcTemplate 模块强制迁移到 JPA
* 将现有 JPA 模块强制改为 JdbcTemplate
* 大规模替换现有 Repository、DAO 或 SQL 实现
* 在无业务必要时引入新的 ORM 或数据库框架

---

# 二、后端目录与模块

当前主要业务模块包括：

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

修改业务前，必须先定位对应的：

```text
Controller
Service
Repository / DAO
Entity / Model
DTO / VO
Flyway migration
权限拦截规则
测试文件
```

禁止只修改 Controller 而忽略业务层、数据库层和权限层。

---

# 三、后端修改决策原则

后端修改必须遵循以下顺序：

1. 先确认现有接口是否已经满足需求
2. 先确认现有字段和状态是否可以复用
3. 先确认现有 Service 是否已有业务能力
4. 先确认现有 permission code 是否已经存在
5. 确实无法满足时，才设计最小后端变更
6. 修改后必须验证前后端兼容性

禁止两个极端：

* 为了前端开发方便随意修改后端
* 为了避免修改后端而在前端制造错误补丁或伪造业务逻辑

应选择对整体架构风险最低、业务语义最正确的方案。

---

# 四、API 设计规则

## 1. 不得虚构接口

前端需求涉及接口时，必须先搜索真实代码。

禁止：

* 根据页面需要猜测接口
* 自行发明不存在的路径
* 自行假设请求参数
* 自行假设返回字段
* 为了通过页面编译创建空接口

---

## 2. 保持接口兼容

未经确认，禁止：

* 重命名已有接口
* 删除已有接口
* 修改已有请求方法
* 修改已有返回结构
* 修改状态码语义
* 修改分页格式
* 修改登录接口返回格式
* 修改 token 传递规则

确需调整时，应优先采用：

* 新增可选字段
* 新增兼容接口
* 保留旧接口
* 提供迁移说明

---

## 3. 路径约定

前端统一以：

```text
/api
```

作为代理前缀。

后端 Controller 路径必须与现有前端 `request.js` 调用保持一致。

禁止因个人偏好随意改变路径命名。

---

## 4. 请求与返回模型

修改接口时，必须明确：

* 请求方法
* 路径参数
* Query 参数
* Request Body
* 响应结构
* 权限 code
* 错误状态
* 是否分页
* 是否涉及事务

禁止自行创建与现有业务重复的 DTO。

新增 DTO 前必须先搜索：

```text
DTO
Request
Response
VO
Projection
```

确认没有可复用结构。

---

# 五、Controller 规则

Controller 负责：

* 接收参数
* 参数校验
* 身份和权限边界
* 调用业务服务
* 返回统一响应

Controller 不应承载：

* 复杂业务计算
* 多表事务编排
* 重复 SQL
* 状态机核心逻辑
* 大量权限分支
* 可复用业务规则

禁止在 Controller 中重新实现 Service 已有逻辑。

对于现有遗留 Controller，采用最小风险方式逐步改善，不进行无必要的大规模重写。

---

# 六、Service 与事务规则

涉及以下操作时，必须评估事务：

* 合同生成账单
* 账单审核
* 确认收款
* 撤回账单
* 租户入驻
* 租赁状态流转
* 工单分配、完成、关闭
* 抄表生成账单
* 发票上传记录
* 角色菜单与权限同步保存
* 批量更新
* 批量删除

多张表必须同时成功或失败的操作，应使用事务。

禁止出现：

```text
前一张表成功
后一张表失败
业务状态不一致
```

事务中应避免：

* 长时间外部调用
* 无界循环
* 大文件处理
* 不必要的远程请求

---

# 七、RBAC 权限规则

当前权限链路：

```text
sys_user_role
↓
sys_role_menu
sys_role_permission
↓
GET /api/system/user/access
↓
前端菜单与按钮
↓
AuthInterceptor 后端接口校验
```

权限真实来源是数据库，不是前端缓存。

---

## 1. SUPER_ADMIN 边界

只有：

```text
SUPER_ADMIN
```

可以拥有平台全权限。

`ADMIN` 不得被自动当作超级管理员。

禁止出现：

```java
ADMIN || SUPER_ADMIN
```

直接绕过全部 RBAC 校验的逻辑。

---

## 2. 业务权限必须使用 permission code

例如：

```text
workorder:view
workorder:create
workorder:update
workorder:assign
workorder:complete
workorder:close
```

禁止继续使用固定角色编码控制业务接口，例如：

```java
if (role.equals("ENGINEER"))
```

业务接口应判断实际 permission code。

---

## 3. 菜单、按钮、接口必须一致

权限必须形成闭环：

```text
菜单权限
+
页面 view 权限
+
操作权限
+
后端接口权限
```

禁止出现：

* 前端按钮隐藏但接口可直接调用
* 菜单存在但 view 接口返回 403
* 有操作权限但没有 view 权限
* 有 view 权限但未同步菜单
* Controller 固定角色校验覆盖 AuthInterceptor 结果

---

## 4. 通配权限规则

除 `SUPER_ADMIN` 外，普通业务角色原则上不得保存：

```text
xxx:*
```

应展开为明确的操作权限。

例如：

```text
workorder:view
workorder:create
workorder:update
workorder:assign
workorder:complete
workorder:close
```

避免通配权限破坏三级操作权限矩阵。

---

## 5. 数据权限

当前 RBAC 主要解决功能权限。

未来数据权限应单独设计，例如：

* 全部数据
* 部门数据
* 所负责楼宇
* 所负责工单
* 所属租户
* 本人数据

禁止用前端筛选代替后端数据权限。

禁止只隐藏页面数据但让接口返回全部业务记录。

---

# 八、AuthInterceptor 规则

`AuthInterceptor` 是系统安全核心，不得随意删除或绕过。

修改前必须确认：

* 现有路径映射
* permission code
* HTTP 方法
* 公共接口范围
* 微信小程序公共接口范围
* 登录接口范围
* 静态资源范围

禁止：

* 将业务接口错误加入公共白名单
* 因测试失败直接放开路径
* 信任前端传来的角色或权限
* 只依赖 token 中的旧权限
* 使用固定角色替代数据库实时权限

后端接口校验应读取当前数据库权限或经过正确失效机制的权限缓存。

---

# 九、登录与身份规则

禁止随意修改：

* 登录接口路径
* token 格式
* token Header 规则
* 登录返回结构
* Redis 登录状态结构
* 验证码流程
* 用户角色加载逻辑

前端当前传递：

```text
Authorization: 原始 token
```

不加：

```text
Bearer
```

不得擅自更改。

当前用户权限刷新接口：

```text
GET /api/system/user/access
```

返回当前用户最新的：

```text
roles
menus
permissions
```

该接口必须基于数据库中的真实用户角色和权限关系。

---

# 十、数据库和 Flyway 规则

所有数据库结构变更必须通过 Flyway migration。

迁移目录以项目现有配置为准。

迁移文件命名：

```text
V<版本号>__<清晰描述>.sql
```

例如：

```text
V34__add_workspace_view_table.sql
```

---

## 严禁

* 手工修改生产数据库结构后不留 migration
* 修改已经执行过的历史 migration
* 删除历史 migration
* 复用已有 migration 版本号
* 在应用启动代码中偷偷修改表结构
* 自动删除历史数据
* 自动清空异常数据
* 未经确认修改字段类型或非空约束

---

## 数据迁移原则

涉及历史数据时：

* 优先兼容旧数据
* 先补充字段，再逐步迁移
* 为旧数据提供明确默认值
* 保留已有角色和用户绑定
* 不因新矩阵删除旧角色
* 不自动删除异常记录

历史异常数据处理：

```text
只提示
不自动删除
不自动清空
```

确需清洗时：

* 单独编写清洗脚本
* 明确影响范围
* 执行前确认
* 提供回滚或备份方案

---

# 十一、MySQL 规则

新增查询或列表接口时，必须关注：

* 是否命中索引
* 是否产生 N+1 查询
* 是否全表扫描
* 是否存在无界查询
* 是否分页
* 是否稳定排序
* 是否存在重复数据
* 是否可能返回过量字段

禁止为了方便直接：

```sql
SELECT *
```

用于高频或大数据量接口，除非现有模块已经明确采用且风险可控。

列表查询应评估：

```text
过滤字段
排序字段
关联字段
分页字段
唯一索引
普通索引
组合索引
```

---

# 十二、Redis 规则

Redis 当前用于：

* 登录状态
* 验证码
* 权限缓存
* 统计缓存
* 限流
* 临时锁

使用 Redis 时必须定义：

* key 前缀
* 数据结构
* TTL
* 失效机制
* 缓存更新时机
* 数据库与缓存一致性

权限修改后，应确保相关权限缓存正确失效。

禁止创建：

* 无 TTL 的临时缓存
* 无命名空间的 key
* 无失效策略的权限缓存
* 以 Redis 代替永久业务数据库

---

# 十三、并发与幂等规则

以下业务必须评估重复提交：

* 生成账单
* 确认收款
* 撤回账单
* 工单完成
* 工单关闭
* 批量生成账单
* 抄表入账
* 发票上传
* 微信支付回调
* 租户入驻
* 角色权限保存

必须避免：

* 重复账单
* 重复支付
* 重复状态流转
* 重复插入
* 重复回调处理

可按现有架构使用：

* 唯一约束
* 状态校验
* 事务
* Redis 锁
* 幂等业务号

不得为了幂等随意引入复杂分布式组件。

---

# 十四、状态流转规则

业务状态不能被任意字段编辑绕过。

例如工单：

```text
创建
分配
处理中
完成
关闭
```

不同状态操作必须使用对应 permission code。

拥有：

```text
workorder:update
```

不代表可以绕过：

```text
workorder:assign
workorder:complete
workorder:close
```

合同、租赁、账单、抄表、停车账单等同样适用。

状态流转必须：

* 校验当前状态
* 校验目标状态
* 校验操作权限
* 保证事务一致性
* 记录必要操作日志

---

# 十五、文件与附件规则

涉及：

* 发票
* 合同附件
* 图片
* 设备照片
* 工单照片
* 巡检照片

必须检查：

* 文件类型
* 文件大小
* 文件名安全
* 路径安全
* 访问权限
* 下载权限
* 删除权限

禁止信任客户端文件名。

禁止通过路径参数读取服务器任意文件。

---

# 十六、日志规则

日志应记录：

* 关键业务操作
* 权限拒绝
* 状态流转
* 异常信息
* 外部接口失败
* 数据迁移结果

禁止记录：

* 明文密码
* 完整 token
* 数据库密码
* 微信支付密钥
* 用户隐私明文
* 大文件内容

异常日志应包含足够上下文，但不得泄露敏感信息。

---

# 十七、异常处理规则

禁止：

* 捕获异常后静默忽略
* 所有异常统一返回 200
* 将数据库堆栈直接返回前端
* 因单个辅助接口失败拖垮整个 Dashboard
* 通过空数组掩盖关键业务错误

Dashboard 和 BI 聚合接口应使用局部降级策略。

普通 CRUD 接口应明确返回业务错误。

---

# 十八、AI 与 BI 接口规则

AI 与 BI 接口必须使用真实业务数据。

禁止：

* mock 图表数据
* 虚构趋势
* 虚构同比环比
* 用固定文本伪装 AI 分析
* 未经权限校验访问 AI 接口

权限要求：

* AI 接口仅允许授权角色访问
* FINANCE 不应自动获得 AI 接口权限
* AI 结果不得越过租户或数据范围权限

聚合接口部分失败时，应采用明确降级，不应拖垮整体页面。

---

# 十九、测试与验证规则

后端修改后至少执行：

```bash
mvn -q -DskipTests compile
```

涉及已有测试时，应运行相关定向测试。

涉及以下内容时，必须增加或更新测试：

* 权限映射
* 角色访问
* 登录
* 数据迁移
* 状态流转
* 账单生成
* 支付回调
* 批量操作
* 事务一致性

---

## 权限测试至少覆盖

* `SUPER_ADMIN`
* 普通有权限角色
* 无权限角色

结果至少覆盖：

```text
200
403
业务异常
```

---

## 数据库迁移测试

必须验证：

* migration 可执行
* 旧数据可兼容
* 不覆盖已有权限
* 不删除已有角色
* 应用可以正常启动或编译

---

# 二十、构建命令

后端编译：

```bash
mvn -q -DskipTests compile
```

执行测试时，优先运行与本次修改相关的定向测试。

禁止因无关旧测试失败而直接修改业务代码。

应先判断：

* 是否由本次变更引起
* 是否为旧测试编码问题
* 是否为环境问题
* 是否为真实业务回归

---

# 二十一、Git 和记录规则

完成稳定后端功能后，必须更新：

```text
CONTEXT_SUMMARY.md
CHANGELOG.md
相关 docs 文档
```

涉及以下内容必须记录：

* 新接口
* 接口参数变化
* 返回结构变化
* permission code
* Flyway migration
* 状态流转
* 数据清洗脚本
* 测试结果
* 运行端口

稳定阶段再执行：

```bash
git status
git add .
git commit -m "feat: 描述本次稳定功能"
```

禁止把无关未提交文件混入本次提交。

---

# 二十二、AI Agent 工作流程

修改前必须：

1. 阅读根目录 `AGENTS.md`
2. 阅读根目录 `ARCHITECTURE.md`
3. 阅读根目录 `CONTEXT_SUMMARY.md`
4. 阅读当前目录 `AGENTS.md`
5. 搜索真实接口和业务实现
6. 确认数据库结构
7. 确认 permission code
8. 输出修改计划

修改中必须：

* 只修改相关文件
* 不清理无关代码
* 不删除历史文件
* 不回滚无关改动
* 不修改未确认的 API
* 不修改未确认的数据库字段
* 不绕过权限

修改后必须输出：

* 修改文件清单
* 修改原因
* API 影响
* 数据库影响
* RBAC 影响
* 测试命令
* 测试结果
* 风险和未完成项

---

# 二十三、禁止事项

严禁：

* mock 数据
* 虚构接口
* 虚构字段
* 虚构 DTO
* 修改已执行 Flyway migration
* 删除历史 migration
* 删除 AuthInterceptor
* 绕过 RBAC
* 硬编码业务角色
* 将 ADMIN 当作 SUPER_ADMIN
* 信任前端权限
* 修改 token 规则
* 修改登录返回结构
* 自动删除历史异常数据
* 未经确认修改生产数据
* 因 UI 需求随意改变业务状态机
* 为通过测试放宽接口权限
* 创建与现有能力重复的 Service 或 Repository

---

# 二十四、完成标准

后端任务只有同时满足以下条件才算完成：

* 已确认真实接口和业务结构
* 没有 mock
* 没有虚构字段或 DTO
* API 兼容性已说明
* 数据库变更通过 Flyway
* RBAC 没有被破坏
* 事务边界正确
* 状态流转没有被绕过
* 编译通过
* 相关测试通过
* 文档已更新
* 稳定阶段已提交 Git

````

保存后，目录应为：

```text
dehui-property-management
├── AGENTS.md
├── pom.xml
└── src
````

完成这一步后，用和前端相同的只读确认方式验证：

```text
请不要修改任何代码。

请读取当前后端目录适用的所有 AGENTS.md、ARCHITECTURE.md 和 CONTEXT_SUMMARY.md。

仅输出：
1. 已读取的文件路径
2. 后端修改必须遵守的核心规则
3. 数据库与 Flyway 规则
4. RBAC 与 AuthInterceptor 规则
5. 后端任务完成标准

不要执行命令，不要修改文件。
```


## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
