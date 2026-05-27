# 合同、租户、联系人联系方式校验实施记录

日期：2026-05-26

## 背景

合同台账、租户管理和租户联系人账号维护中，联系电话和邮箱字段曾允许保存明显错误的数据，例如短数字、超长数字、含中文的电话、无 `@` 的邮箱等。

这类数据会影响后续租户联系、账单通知、小程序展示、合同管理和财务流程。因此本次按“方案 A：前后端共用规则 + 补齐租户联系方式字段”实施。

核心原则：

- 历史异常数据不自动删除、不自动清空。
- 列表和详情页只提示“格式异常”。
- 新增和编辑保存时必须修正为合法格式。
- 前端校验用于用户体验，后端校验作为兜底规则。

## 变更范围

WEB 管理端：

- 合同台账
- 租户管理
- 租户联系人账号维护

后端：

- 合同接口
- 租户接口
- 租户联系人接口
- 统一联系方式校验工具

数据库：

- 租户主档联系人字段
- 租户主档邮箱字段
- 租户联系人邮箱字段

## 修改文件

前端：

```text
dehui-property-admin/src/utils/contactValidation.js
dehui-property-admin/src/views/tenant/ContractList.vue
dehui-property-admin/src/views/tenant/TenantList.vue
```

后端：

```text
dehui-property-management/src/main/java/com/dehui/property/common/ContactValidators.java
dehui-property-management/src/main/java/com/dehui/property/modules/contract/controller/ContractController.java
dehui-property-management/src/main/java/com/dehui/property/modules/tenant/controller/TenantController.java
```

数据库迁移：

```text
dehui-property-management/src/main/resources/db/migration/V5__tenant_contact_fields.sql
```

测试：

```text
dehui-property-management/src/test/java/com/dehui/property/modules/contract/ContractControllerTest.java
dehui-property-management/src/test/java/com/dehui/property/modules/tenant/TenantControllerContactValidationTest.java
dehui-property-management/src/test/java/com/dehui/property/schema/TenantContactFieldsMigrationTest.java
dehui-property-management/src/test/java/com/dehui/property/modules/dashboard/DashboardSupportEndpointsTest.java
```

记录文件：

```text
AGENTS.md
CONTEXT_SUMMARY.md
README.md
CHANGELOG.md
docs/superpowers/plans/2026-05-26-contract-tenant-contact-validation.md
```

## 数据库迁移

迁移文件：

```text
V5__tenant_contact_fields.sql
```

新增字段：

- `tenant.contact_person`：租户联系人。
- `tenant.contact_email`：租户邮箱。
- `tenant_contact.email`：租户联系人邮箱。

用途：

- 支持租户主档独立维护联系人和邮箱。
- 支持租户联系人账号独立维护邮箱。
- 避免继续把联系人或邮箱混写到 `remark` 字段。
- 为前端表单校验、后端接口校验和后续小程序展示提供明确字段。

## 接口校验逻辑

统一工具：

```text
ContactValidators.java
```

联系电话规则：

```text
^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$
```

支持：

- 中国大陆手机号，例如 `13800000000`
- 座机号，例如 `021-88888888`

邮箱规则：

```text
^[^\s@]+@[^\s@]+\.[^\s@]+$
```

后端行为：

- 可选电话、邮箱允许为空。
- 租户联系人手机号必填。
- 只要填写，就必须符合格式。
- 错误联系电话返回 HTTP 400，消息为 `联系电话格式不正确`。
- 错误邮箱返回 HTTP 400，消息为 `邮箱格式不正确`。

覆盖接口：

- `POST /api/contracts`
- `PUT /api/contracts/{id}`
- `POST /api/tenant/save`
- `POST /api/tenant/{tenantId}/contacts`

合同接口额外变更：

- 合同列表返回租户联系人、联系电话、邮箱。
- 新增合同可以按 `tenantName` 自动创建或复用租户。
- 新增合同会回写租户联系人、联系电话、邮箱。
- 新增 `PUT /api/contracts/{id}` 编辑合同接口。
- 新增 `POST /api/contracts/{id}/reactivate` 恢复履约接口。

## 前端校验逻辑

统一工具：

```text
dehui-property-admin/src/utils/contactValidation.js
```

合同台账：

- 新增/编辑合同时校验 `contactPhone`、`contactEmail`。
- 联系电话错误时提示：`请输入正确的联系电话，例如 13800000000 或 021-88888888`。
- 邮箱错误时提示：`请输入正确的邮箱地址，例如 name@example.com`。
- 合同列表展示联系人、联系电话、邮箱。
- 历史异常值展示为 `xxx（格式异常）`。
- 增加合同编辑入口。
- 增加“恢复”按钮，终止/作废后的合同可以恢复履约。

租户管理：

- 租户新增/编辑时校验 `contactPhone`、`contactEmail`。
- 租户联系人账号维护中，`phone` 必填且必须合法。
- 租户联系人账号维护中，`email` 可为空；填写时必须合法。
- 租户列表、联系人列表、租户详情中，历史异常联系方式只提示、不清空。

## 测试结果

后端全量测试：

```powershell
cd dehui-property-management
mvn test
```

结果：

```text
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

前端构建：

```powershell
cd dehui-property-admin
npm run build
```

结果：

```text
✓ built
```

本地服务：

- 后端本地启动正常：`8080`
- 前端本地启动正常：`5173`
- 浏览器已回到 `/contracts`

接口冒烟：

- 合同接口提交错误手机号/邮箱时返回 HTTP 400。
- 租户接口提交错误手机号/邮箱时返回 HTTP 400。
- 租户联系人接口提交错误手机号/邮箱时返回 HTTP 400。

## 后续风险和注意事项

- 历史脏数据仍会存在于数据库中，本次只在页面提示，不做批量清洗。
- 如果后续要清洗历史数据，应单独写数据清洗脚本，并先备份数据库。
- 后续新增任何联系人、电话、邮箱字段时，应复用 `ContactValidators.java` 和 `contactValidation.js`。
- 不要把租户联系人、租户邮箱继续塞进 `remark` 字段。
- 如果小程序端未来展示租户联系人或招商联系人，也应只读取后端返回字段，不在前端绕过校验保存数据。
- 涉及数据库字段、接口返回、前端表单校验的后续改动，必须同步更新 `CONTEXT_SUMMARY.md` 和相关 docs。
