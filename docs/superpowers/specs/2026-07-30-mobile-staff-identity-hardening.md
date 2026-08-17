# 移动端物业员工身份加固与生产处置

## 1. 问题与安全边界

历史或测试数据可能让一个微信 OpenID 通过 `mobile_user_bind` 关联到不再具有手机号依据的后台员工。若服务端只检查绑定、员工启用和 RBAC，旧绑定就可能恢复为 `STAFF`，包括错误关联到系统管理员的情况。

移动端 STAFF 身份现在必须同时满足：

1. 手机号验证来源为服务端写入的 `WECHAT_API`；
2. `phone_verified_at` 非空；
3. 小程序手机号与后台员工手机号均非空，规范化后完全一致；
4. `mobile_user` 与 `sys_user` 均启用，员工 `mobile_enabled=1`；
5. 员工仍通过实时 RBAC 拥有 `staff-mobile:access`、`staff-mobile:*` 或 `*`；
6. 当前 INTERNAL 绑定启用且属于当前 `mobile_user`。

客户端缓存、客户端声明的角色、历史 `phone_verified_at`、后台密码和角色名称均不是 STAFF 身份依据。

## 2. 服务端实现

- `/mobile/me`、所有 STAFF 路由和 STAFF 业务写入边界均复用服务端实时身份结果。旧 STAFF token 一旦不再满足条件，立即返回 401 并从 Redis 撤销。
- 微信手机号验证和候选身份选择在数据库事务提交前撤销相关 `mobile_user` 的全部旧 token；Redis 撤销失败会回滚身份变更。管理员切换默认身份同样在事务内撤销旧会话；用户接口仅在事务成功后签发一个新 token，防止验证前 PUBLIC token 自动升级为 STAFF。
- 当页面已因 fail-closed 显示 PUBLIC，但当前微信技术用户仍残留旧 STAFF 指针，且真实手机号属于另一个 canonical `mobile_user` 时，只允许“非 `WECHAT_API` 来源、全部绑定历史均为 INTERNAL、当前绑定已失效、没有任何非绑定业务记录”的技术壳账号自动收口。收口会禁用全部启用的旧 INTERNAL 绑定、清除当前身份、迁移 OpenID、停用壳账号、写三段审计并撤销双方旧会话。任一 TENANT 绑定历史、仍有效身份或业务记录都会返回 409，禁止自动迁移。
- 员工手机号、员工状态、`mobile_enabled`、员工角色、角色状态和角色权限变化后，服务端重新判断所有相关 INTERNAL 绑定。失效绑定会被禁用，当前身份降为 PUBLIC，候选身份过期，写入审计并撤销全部移动会话。
- 小程序用户停用、显式解绑和管理员解除员工移动绑定均进入同一身份生命周期服务；停用与数据库解绑处于同一事务。
- 管理后台移动用户列表使用同一组手机号来源、手机号一致性、员工状态和 RBAC 条件展示运行时状态，不能继续把已失效绑定显示为可用 STAFF。

## 3. V64 数据库约束

`V64__harden_mobile_staff_identity.sql` 只做兼容性结构演进：

- 新增 `mobile_user.phone_verification_source`，默认 `UNVERIFIED`；
- 历史 `phone_verified_at` 不自动升级为微信官方证明，既有员工必须重新完成真实微信手机号授权；
- 使用 MySQL 生成列和唯一索引，阻止同一 `tenant_contact_id` 或同一 `sys_user_id` 同时启用在多个 `mobile_user` 上；
- 不更新、删除或重建历史业务数据。

V64 遇到历史重复启用绑定会安全失败。发布前必须先执行
`2026-07-21-mobile-user-identity-diagnostics.sql` 的第 4、5 项并确认均为 0；如果不为 0，只能按明确 ID 人工确认并收敛，禁止自动选择 canonical 用户。

## 4. 生产发布与异常绑定处置

1. 先备份并只读执行完整身份诊断 SQL，保存第 1、4、5、5a、5b 项结果。
2. 重新查询生产目标行，确认用户报告的 `mobile_user / mobile_user_bind / sys_user` 三者仍为同一链路；不能仅凭旧截图或本地 ID 操作。
3. 发现系统管理员或其他错误 STAFF 绑定时，使用受 RBAC 保护的
   `DELETE /api/system/users/{sysUserId}/mobile-binding`，或在确认移动用户后使用
   `POST /api/mobile-users/{mobileUserId}/unbind`。接口会禁用绑定、降为 PUBLIC、清除验证来源、写审计并撤销 Redis 会话。
4. 再次执行诊断 SQL，确认异常 INTERNAL 绑定、共享业务身份和断裂 current pointer 均为 0；确认对应旧 token 返回 401。
5. 执行应用发布与 Flyway V64。所有历史 STAFF 用户必须通过真实微信 `getPhoneNumber` 重新验证，不能用直接写手机号、手工改时间戳或后台密码代替。
6. 复核员工停用、手机号变化、移动权限回收和角色停用四类场景：旧 token 均应返回 401，恢复员工条件后也不能在未重新微信验号时自动复活。

2026-07-31 已按发布窗口流程完成当前服务器处置，具体结果见下方“生产恢复记录”。后续环境仍必须先运行诊断并备份，不能把本次空测试数据处置当成可直接复用的数据清洗规则。

## 5. 自动化验收

- 本地 Flyway 已升级到 V64，新增来源字段和两项唯一索引存在；
- 本地只读扫描：共享 tenant contact、共享 sys user 和断裂 current pointer 均为 0；9 条历史 INTERNAL 绑定因来源为 `UNVERIFIED` 被 fail-closed，需重新微信验号；
- 真实 MySQL/Redis/HTTP 双 token 场景：A、B 均为验证前 PUBLIC token，A 完成候选 STAFF 身份选择后，A/B 旧 token 均返回 401、Redis 均不存在，只有新 token 返回 200/STAFF；
- 真实 MySQL/HTTP 截图复现场景：PUBLIC 页面态背后存在失效 STAFF 指针、授权手机号已有 canonical 用户；修复后快捷登录返回 200，旧绑定被禁用、OpenID 指向 canonical 用户且旧 token 不可用。加入任何非绑定业务记录或 TENANT 绑定历史后仍返回 409，数据库不发生收口；
- 真实 MySQL 故障注入场景：`TokenService.revokeUserSessions` 模拟 Redis 异常后，手机号、验证时间、验证来源、当前绑定和用户类型均保持事务前状态，未出现可被旧 token 升级的半提交身份；
- 员工停用场景：绑定被禁用、当前身份降为 PUBLIC、审计写入、全部旧 token 被删除，重新启用员工后旧绑定不会复活；
- V64 唯一索引分别拒绝共享 INTERNAL 与 TENANT 启用绑定；
- 微信开发者工具使用隔离 Mock 验证过期 STAFF 缓存遇到 401 后页面显示“公开访问”，不再显示“系统管理员”，运行异常为 0；
- 真实微信手机号授权仍需人工完成最终验收；服务端匹配、隔离、权限和会话轮换已由 Mock 与真实本地基础设施覆盖。

## 6. 2026-07-31 服务器生产恢复记录

- 根因：V64 首个 `ALTER TABLE` 已自动提交，但历史重复启用绑定导致后续唯一索引创建失败；Flyway 留下 `success=0`，服务器只能临时使用 `SPRING_FLYWAY_ENABLED=false` 启动。
- 本环境尚无有效生产移动身份数据，经用户明确授权后，先完成整库压缩备份，再停止后端写入。保留会议预约和工单业务记录，仅将其中 3 个旧 `mobile_user` 引用置空；随后清空 `mobile_user`、`wechat_identity`、`mobile_user_bind`、`mobile_identity_candidate`、`mobile_identity_audit` 及对应移动 Redis 会话。
- 回滚 V64 已自动提交的单列结构并精确移除失败的 Flyway 历史行后，撤销临时跳过变量。正常启动成功执行 V64，Flyway 记录为 `success=1`，两个生成保护列和两个唯一索引均存在。
- 在事务内分别制造同一 `sys_user_id` 的重复 INTERNAL 绑定和同一 `tenant_contact_id` 的重复 TENANT 绑定，两个写入均被唯一索引以重复键拒绝；测试事务回滚后移动身份相关表仍为 0。
- 再次重启后 Flyway 校验 64 个迁移，确认当前版本 64 且无待执行迁移。MySQL、Redis、Nginx、后端均为 `active`，后端为 `enabled`，HTTPS 首页返回 200。
- 真实浏览器退出旧后台会话后使用原管理员凭据重新登录成功；驾驶舱刷新接口返回 200，页面自身无 Console error/warn。
- 自动化回归：后端共运行 591 项（575 通过、16 项常规环境门控跳过），移动身份真实 MySQL 集成 3/3；管理后台组件 44/44、Node 167/167、生产构建；小程序 197/197。
- 真实微信 `getPhoneNumber` 仍是唯一需要人工完成的最终验收步骤。首次重新授权将创建新的 PUBLIC 身份，再按服务端手机号和权限条件恢复允许的 TENANT/STAFF 身份，不会复用本次已删除的历史绑定。
