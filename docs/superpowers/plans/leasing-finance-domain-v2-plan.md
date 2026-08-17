# DHIC 租赁履约、计费与财务核心域 V2 实施计划

## 1. 基线与范围

- 基线提交：`ffb78b2 feat: enforce parking billing month uniqueness`
- 基线工作树：任务开始时无未提交文件。
- Flyway 最高版本：`V36__enforce_parking_bill_active_month_uniqueness.sql`。
- 缺失但任务要求读取的文件：根目录 `ARCHITECTURE.md`、`dehui-property-admin/AGENTS.md`、`dehui-property-management/AGENTS.md`。本计划不臆造其内容，执行时以根目录 `AGENTS.md`、现有代码和测试为准。
- 当前部署形态保持一个 Spring Boot 模块化单体；不拆微服务、不自动部署生产、不自动修改或清理生产数据。
- 旧表、旧字段、旧路由先保留兼容；任何删除或强约束收口放到新链路稳定后的独立任务。

## 2. 当前模型审计

### 2.1 Contract / Lease

- `contract` 同时承载商务合同、入住状态和押金金额；`room_id` 是旧单房间字段。
- `V23` 已增加 `contract_room` 并回填旧 `room_id`，可作为多房间合同关系的兼容基础。
- `ContractController` 直接使用 `JdbcTemplate`，创建、房间明细写入、合同状态和房态更新未形成一个完整事务。
- `activate/check-in/terminate/cancel/reactivate` 没有完整前置状态校验和状态历史。
- 旧租户入驻页面仍把 `/contracts` 当作 Lease 列表，并直接调用房间 lease/checkout 接口；没有 Lease 真值、占用历史和并发保护。

### 2.2 FeeRule / BillingPlan

- `/feerules` 由 legacy Controller 返回空壳或 501，没有真实表、版本、计划或金额计算服务。
- 合同页面存在 `propertyFeeAmount/paymentCycle/billingLeadDays` 等后端未持久化字段，属于假写入风险。

### 2.3 Bill / Payment / Invoice

- `bill` 只有账单头，没有 `bill_item`；`pay_status` 还被投影成通用 `status`。
- `/bills` 已有真实服务端分页和 `/bills/summary`，应保留并迁移到新查询服务。
- `/bills/{id}/pay` 先插旧支付记录、再直接把账单改为全额已付，缺少幂等、分配模型、锁和完整事务。
- 旧 `bill_payment_record` 是一笔付款只属于一张账单，不能表达部分收款或一付多账单。
- 旧开票会先软删当前发票再新建，`bill_invoice.bill_id` 固定为单账单，PDF 附件和发票事实没有独立生命周期。
- `/payments`、`/invoices` 是不分页 `SELECT *`，且只受 `bill:view` 控制。

### 2.4 Deposit / Shared

- 押金仅由 `contract.deposit_amount` 表达，没有应收、实收、扣除、退还和冲正账本。
- 没有 `idempotency_record`、`outbox_event`、`domain_audit_log`。
- 现有 `log_operation` 是 HTTP 操作日志，不能替代领域审计。

### 2.5 RBAC / 前端 / 移动端

- Contract 的所有 POST 当前近似映射为 `contract:create`；账单 approve/reject 可能被映射为 `bill:add`，存在权限绕过和合法用户 403 的双重风险。
- Contract、Lease、FeeRule 仍是固定表格；Bill 仅使用部分工具栏。BillingPlan、Payment、Invoice、Deposit 页面和路由不存在。
- 小程序依赖 `GET /mobile/bills -> { profile, summary, bills }` 和 `/mobile/bills/{id}/invoice/download`，必须保持租户隔离和旧字段投影。
- 新多发票模型不能直接 join 成多行旧账单；兼容 facade 必须显式选取最新有效附件并聚合开票事实。

## 3. 目标模块与聚合边界

```text
modules.contract
  Contract aggregate
  ContractRevision / ContractRoom / ContractAttachment / ContractStatusHistory

modules.lease
  Lease aggregate
  LeaseRoom / LeaseEvent / LeaseStatusHistory

modules.billing
  FeeRule aggregate -> immutable FeeRuleVersion
  BillingPlan aggregate -> BillingPlanItem
  Bill aggregate -> BillItem / BillStatusHistory
  BillGenerationBatch

modules.payment
  Payment aggregate -> PaymentAllocation / PaymentReversal / PaymentStatusHistory

modules.invoice
  InvoiceApplication aggregate
  Invoice aggregate -> InvoiceBill / InvoiceAttachment / InvoiceEvent

modules.deposit
  DepositAccount aggregate -> DepositTransaction

modules.shared.domain
  IdempotencyRecord / OutboxEvent / DomainAuditLog
```

边界规则：

1. Controller 只做 HTTP、DTO 校验和响应转换。
2. 跨表事务放在 application service；SQL 由模块 repository 隔离。
3. JdbcTemplate 继续使用，不为形式统一强制 JPA。
4. 模块只能通过应用服务接口或领域事件请求跨域动作。
5. `Room.rent_status` 只做当前占用缓存，LeaseRoom 才是历史真值。
6. `contract.room_id`、`contract.deposit_amount`、`bill.pay_status`、`bill_payment_record`、`bill_invoice` 均保留为 legacy 兼容字段或表，不再作为新链路唯一真值。

## 4. 状态机

### 4.1 Contract

```text
DRAFT -> PENDING_APPROVAL -> ACTIVE -> SUSPENDED -> ACTIVE
  |             |             |          |
  +-> CANCELLED +-> CANCELLED +-> TERMINATED
                              +-> EXPIRED
```

- 创建/编辑只允许在 `DRAFT` 或被驳回后进入新的 revision。
- approve、activate、terminate、cancel 使用专用应用服务，禁止通用更新绕过。
- 每次转换使用 `version` 条件更新并写 `contract_status_history`、审计和 Outbox。

### 4.2 Lease

```text
PENDING_CHECKIN -> PARTIALLY_ACTIVE -> ACTIVE -> RENEWING
       |                  |              |
       +-> CANCELLED      +-> PARTIALLY_TERMINATED -> TERMINATED
                                      +-> CHECKOUT_PENDING
```

- 入住和退租按 LeaseRoom 子集执行；状态由有效房间占用数量推导。
- 同一房间的有效占用区间不能重叠；写入前锁定目标房间和活跃 LeaseRoom。
- 续租创建新 ContractRevision 和新 Lease，保留 `renewal_of_*` 关系。

### 4.3 Bill

- `record_status`: `ACTIVE | VOID`
- `audit_status`: `DRAFT | PENDING | APPROVED | REJECTED`
- `payment_status`: `UNPAID | PARTIAL | PAID | REFUNDED`
- 账单头金额始终由 BillItem 汇总；收款金额始终由成功 Allocation 汇总。

### 4.4 Payment

```text
PENDING -> SUCCESS -> PARTIALLY_ALLOCATED -> ALLOCATED
                   \-> FAILED
PARTIALLY_ALLOCATED/ALLOCATED -> REVERSED（通过冲正事实派生）
```

- 付款金额、已分配金额和未分配金额必须同币种且守恒。
- 冲正新增 reversal 和反向分配事实，不删除原付款或原分配。

### 4.5 Invoice

```text
DRAFT -> APPLIED -> PROCESSING -> ISSUED
                     |            |
                     +-> FAILED   +-> VOIDED -> RED_FLUSHED
```

- 附件不可变；替换会新建附件版本并保留历史。
- 作废、红冲建立事件和原发票关系，不普通删除已开具事实。

### 4.6 Deposit

- Account 状态按余额与合同生命周期派生。
- Transaction 类型：`RECEIVABLE | RECEIVED | DEDUCTED | REFUNDED | REVERSED`。
- 所有金额操作校验上限、币种、幂等键并使用乐观锁。

## 5. Flyway 顺序

历史 migration 不修改，从 V37 递增：

| 版本 | 阶段 | 内容 |
| --- | --- | --- |
| V37 | Contract / Lease | 给 contract 增加兼容列；新建 contract_revision、contract_status_history、lease、lease_room、lease_event、lease_status_history；前置建立 domain_audit_log/outbox_event 以满足入住事务审计要求；种入 Contract/Lease 权限 |
| V38 | FeeRule / BillingPlan | 新建 fee_rule、fee_rule_version、billing_plan、billing_plan_item；种入权限 |
| V39 | Bill | 为 bill 增加 V2 可空兼容列；新建 bill_item、bill_generation_batch、bill_generation_failure、bill_status_history；增加幂等和查询索引；种入权限 |
| V40 | Payment | 新建 payment、payment_allocation、payment_reversal、payment_status_history、idempotency_record；增加 request/external transaction 唯一约束；种入权限 |
| V41 | Invoice | 新建 invoice_application、invoice、invoice_bill、invoice_attachment、invoice_event；保留 bill_invoice legacy；种入权限 |
| V42 | Deposit / Shared | 新建 deposit_account、deposit_transaction；补齐 shared 索引、保留策略和权限 |
| V43 | 收口 | 菜单、旧权限兼容映射、跨模块索引、可安全建立的唯一约束和只读兼容视图；不得删除旧表或清理历史数据 |

说明：Lease 在阶段一就被要求同事务写审计与 Outbox，因此 `domain_audit_log` 和 `outbox_event` 必须在 V37 前置创建；阶段六负责补齐共享能力的查询、脱敏、保留策略和运维脚本。

## 6. 新旧接口兼容

```text
Legacy Controller
  -> Compatibility Adapter
  -> V2 Application Service
  -> V2 Repository / Domain
```

### 6.1 保留接口

- `/contracts`：旧 GET 继续返回数组供既有下拉和小程序兼容；新服务端分页使用 `/contracts/search`，新 Workspace 只调用分页接口。
- `/contracts/{id}/activate|check-in|terminate|cancel|reactivate`：保留路由但内部调用 Contract/Lease application service，并标记 deprecated。
- `/rooms/{id}/lease|checkout`：保留短期适配，只允许映射到唯一可确定的 Lease；歧义时返回 409，禁止直接改房态。
- `/bills`、`/bills/summary`：保持外层结构和旧字段别名；新字段并行返回。
- `/bills/{id}/pay`：转换为全额 Payment + Allocation，要求幂等键；不直接更新账单金额。
- `/bills/{id}/invoice`：转换为新 Invoice service；不删除旧发票。
- `/payments`、`/invoices`：增加分页兼容响应前，保留旧数组路由；新 Workspace 使用 `/payments/search`、`/invoices/search`。
- `/mobile/bills`：继续返回 `{profile, summary, bills}`，只返回绑定租户可见数据；兼容 facade 聚合新模型且不暴露审计字段。
- `/mobile/bills/{id}/invoice/download`：继续校验租户，投影最新有效、可下载的附件版本。

### 6.2 新 API 统一查询契约

```text
page=1
pageSize=20（最大 100）
keyword
显式字段筛选
sortField（白名单）
sortDirection=asc|desc
```

- 列表返回 `PageResponse<T>`，summary 使用独立聚合 SQL。
- 写接口使用明确 Request/Response DTO 和 Jakarta Validation。
- Idempotency-Key 优先从 header 读取；兼容旧接口可由 requestId DTO 字段提供。
- operator/userId 一律取 `AuthPrincipal`，不接受客户端传入值。

## 7. 阶段实施与提交

### 阶段一：Contract / Lease

1. 先写 migration、状态机、application service 和 API 的失败测试。
2. 实现 V37、Contract/Lease 分层、并发入住/退租、兼容路由、RBAC。
3. 保留电子合同和移动合同下载。
4. 更新 `CONTEXT_SUMMARY.md`、`CHANGELOG.md`。
5. 提交：`feat: establish contract and lease domain`

### 阶段二：FeeRule / BillingPlan

1. 先写规则版本、有效区间、金额类型和权限失败测试。
2. 实现 V38、版本不可变、计划与条目、分页/summary。
3. 从合同表单移除未持久化假字段，兼容层给出明确提示。
4. 提交：`feat: add fee rules and billing plans`

### 阶段三：Bill / BillItem

1. 先写头明细守恒、幂等生成、批次统计、审核/作废测试。
2. 实现 V39、手工账单、BillingPlan 生成、唯一约束兜底和兼容查询。
3. 禁止有付款或发票事实的物理/逻辑级联删除；改为作废。
4. 提交：`feat: rebuild receivable billing model`

### 阶段四：Payment / Allocation / Reversal

1. 先写部分收款、一付多账单、并发、超额、幂等和冲正测试。
2. 实现 V40、事务锁定账单、Allocation 汇总、旧 pay 适配。
3. 提交：`feat: add payment allocation and reversal`

### 阶段五：Invoice

1. 先写申请、多账单、拆分开票、额度、附件版本、作废/红冲测试。
2. 实现 V41、独立 Controller/service/repository、旧 invoice 适配和移动下载 facade。
3. 提交：`feat: establish invoice lifecycle`

### 阶段六：Deposit / Audit / Outbox / Idempotency

1. 先写押金守恒、超额拒绝、冲正、审计脱敏、Outbox、幂等测试。
2. 实现 V42、共享查询和运维脚本。
3. 提交：`feat: add deposit audit and outbox foundations`

### 阶段七：DataWorkspace / RBAC / 收口

1. 新建 Contract、Lease、FeeRule、BillingPlan、Bill、Payment、Invoice、Deposit 八个 WorkspaceConfig。
2. 复用 DataWorkspace/WorkspaceDataGrid/MetricPanel 等公共组件；复杂状态操作留在专用业务组件。
3. 所有列表使用真实服务端分页和 summary；不支持的过滤/排序禁用且显示 scope。
4. 完成菜单、路由、按钮、接口权限矩阵及 V43 收口。
5. 验证 1024/1440/1920，加载、空、错误、无权限和键盘操作状态。
6. 提交：`feat: migrate leasing and finance workspaces`

每阶段固定流程：

```text
失败测试 -> 实现 -> 定向测试 -> 全量测试 -> compile/package/build
-> git diff --check -> 更新文档 -> 只暂存本阶段文件 -> 独立 commit
```

## 8. RBAC 对齐

后端按“路径 + HTTP 方法 + 明确业务动作”映射，禁止仅按 POST/PUT 推导复杂动作。前端菜单、路由、按钮和权限树使用同一 permission code。旧 code 只作为兼容映射，普通角色不新增 wildcard。

重点修复：

- Contract approve/activate/terminate/cancel 分离。
- Lease checkin/checkout/renew/terminate 分离。
- Bill create/update/audit/void/export 分离。
- Payment create/allocate/reverse/export 分离。
- Invoice apply/issue/upload/void/download 分离。
- Deposit receive/deduct/refund 分离。
- SUPER_ADMIN 继续全权限；其他角色只种入明确权限且不删除现有授权。

## 9. 数据处理边界

- Flyway 只做增量 DDL、兼容字段、索引、权限元数据和无损回填。
- 不在 Flyway 中 `DELETE/TRUNCATE` 业务数据，不插入测试业务数据。
- 历史冲突先由只读预检脚本报告，不自动修复。
- 本地 reset 脚本仅允许明确 dev 数据库，错库使用 `SIGNAL`，使用事务且不删除楼宇、房间、用户、角色等基础数据。
- 生产预检脚本仅允许 `SELECT/SHOW/EXPLAIN/information_schema`。
- 旧数据无法无歧义推导 Lease、BillItem、Allocation 或 InvoiceBill 时保持 legacy，并输出冲突清单。

## 10. 测试矩阵

| 领域 | 单元/Repository | Controller/RBAC | MySQL/并发 | 前端/兼容 |
| --- | --- | --- | --- | --- |
| Contract | 状态机、revision、乐观锁 | DTO、401/403、专用权限 | 多房间关系、版本冲突 | Workspace、旧数组、电子合同 |
| Lease | 入住/退租推导、续租 | checkin/checkout/renew 权限 | 房间重叠、双并发、事务回滚 | 分批操作弹窗、旧路由 |
| Fee/BillingPlan | 版本不可变、四类计价、周期 | CRUD/activate 权限 | 有效区间/唯一性 | 分页、summary、禁用能力 |
| Bill | 明细汇总、状态机、批次 | 审核/作废/导出权限 | 幂等/并发生成/金额精度 | Workspace、旧字段、移动查询 |
| Payment | 分配守恒、冲正 | create/allocate/reverse 权限 | 锁、超额、交易号唯一 | 部分/多账单弹窗、旧 pay |
| Invoice | 额度、状态、附件版本 | apply/issue/upload/void/download | 幂等、累计额度并发 | 多账单/拆分、移动下载 |
| Deposit | 余额守恒、冲正 | receive/deduct/refund 权限 | 超额与乐观锁 | 账本 Workspace |
| Shared | 脱敏、payload、过期 | operator principal | Outbox/Idempotency 唯一性 | 错误/旧响应覆盖保护 |

全量命令：

```powershell
cd dehui-property-management
mvn -q test
mvn -q -DskipTests compile
mvn -q package

cd ..\dehui-property-admin
node --test
npm run test:components
npm run build

cd ..\dehui-property-miniprogram
node --test

cd ..
git diff --check
```

MySQL 特定唯一约束、生成列、`SELECT ... FOR UPDATE` 和并发行为沿用现有环境变量门控集成测试，不使用 H2 代替。

## 11. 回滚方案

- 每阶段独立 commit；代码回滚使用对应 revert，不执行破坏性 Git 重置。
- migration 采用向前修复：已经执行的 V37+ 不修改、不删除；如需修正新增后续 migration。
- 新表/nullable 字段保留不会影响旧链路；代码回滚后旧 Controller/字段仍可工作。
- 双写或兼容写失败时切回 legacy read facade，但不删除已产生的新事实。
- 生产上线前必须备份、跑只读预检、确认 Flyway pending 清单、安排维护窗口和人工回归。

## 12. 尚需用户确认但不阻塞建模的参数

- 真实租金、物业费、能源费等单价来源和默认币种；当前默认币种仅允许请求显式提交或沿用历史记录，不写死金额。
- 滞纳金比例、起算日、宽限期和封顶规则；确认前 `LATE_FEE/PERCENTAGE/FORMULA` 保持禁用。
- 税率、含税/未税口径、发票类型和允许开票金额口径；确认前不自动计算税额，不接税务平台。
- 押金应收时点、扣除审批和退款审批规则；当前只建立可审计账本与权限边界。
- 外部支付和开票平台；本任务只预留 channel/request/external number，不实现微信支付或真实税务开票。
- 历史数据冲突处理；本任务只生成只读报告，不自动清洗生产数据。

## 13. 服务化拆分建议

保持模块化单体，按以下顺序观察拆分条件：

1. Payment/Invoice 出现外部渠道、回调重试和独立 SLA 时优先拆分。
2. Billing 生成量和批处理窗口成为独立伸缩需求时拆分。
3. Lease/Contract 仍保持同库一致性，直到跨园区组织边界和调用量明确。
4. Deposit 在规则和审批稳定前不单独服务化。
5. 所有拆分通过 Outbox 事件过渡，不以数据库跨服务直接写表。

首批 Outbox 事件：

```text
ContractApproved / ContractActivated / ContractTerminated / ContractCancelled
LeaseCreated / LeaseRoomCheckedIn / LeaseRoomCheckedOut / LeaseRenewed / LeaseTerminated
FeeRuleVersionActivated / BillingPlanActivated / BillingPlanTerminated
BillGenerated / BillApproved / BillRejected / BillVoided
PaymentSucceeded / PaymentAllocated / PaymentReversed
InvoiceApplied / InvoiceIssued / InvoiceVoided / InvoiceRedFlushed
DepositReceived / DepositDeducted / DepositRefunded / DepositReversed
```

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
