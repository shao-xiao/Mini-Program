# 资产与财务业务数据升级保留规则

## 目标

资产管理五个二级模块和财务账单保存的是正式业务数据。应用升级、后端重启、
Flyway 执行或默认模板变化，不得自动清空、覆盖、重建、回填或重命名这些数据。

该规则保护的是数据库中的真实业务记录，不是浏览器缓存，也不是页面中的
临时展示状态。

## 受保护表

楼宇空间与设备台账：

- `building`
- `building_floor`
- `building_room`
- `equipment`

能耗数据：

- `energy_meter`
- `energy_meter_binding`
- `energy_reading`
- `energy_reading_audit`
- `energy_price_setting`
- `energy_bill_operation_log`

停车数据：

- `parking_space`
- `parking_space_type_option`
- `parking_assignment`
- `parking_bill`

会议数据：

- `meeting_room`
- `meeting_rate_rule`
- `meeting_booking`
- `meeting_bill`

计费依据与财务数据：

- `asset_charge_basis`
- `fee_rule`
- `fee_rule_version`
- `billing_plan`
- `billing_plan_item`
- `bill`
- `bill_line_item`
- `bill_payment_record`
- `bill_invoice`
- `bill_invoice_attachment`
- `bill_audit_log`

资产图片、附件与统一字段共享记录：

- `file_upload`
- `custom_field_definition`
- `custom_field_definition_history`
- `custom_field_option`
- `custom_field_value`
- `module_field_setting`

完整且可执行的 35 张表清单统一维护在
`dehui-property-management/scripts/protected_asset_business_tables.txt`。共享表按整表
保护，因为自动迁移无法安全区分某一条共享记录是否属于资产模块；系统升级如需
治理这些记录，必须走单独的数据治理流程。

## V61 起的迁移约束

自动 Flyway 迁移不得对受保护表执行：

- `INSERT` 或 `INSERT IGNORE`
- `UPDATE`
- `DELETE`
- `REPLACE`
- `TRUNCATE`
- `DROP TABLE`
- `ALTER TABLE ... DROP ...`
- `ALTER TABLE ... TRUNCATE/RENAME/EXCHANGE ...`
- `RENAME TABLE`
- `DROP INDEX ... ON ...`
- `LOAD DATA ... INTO TABLE ...`

允许增加可兼容字段、索引或约束等纯结构演进，但不能借迁移脚本写入默认
业务数据。历史业务数据转换必须作为单独的数据治理任务进行：先备份、先在
副本验证、明确行数和内容指纹、人工确认后执行，并提供可审计的回滚方案。

## 自动守卫

`BusinessDataUpgradePreservationPolicyTest` 会读取统一表清单、确认五个资产
模块、财务历史和共享字段/附件均已覆盖，扫描 V61 及后续迁移，并对每张受保护
表验证不安全 SQL 都会被拦截。

`verify_energy_finance_upgrade_preservation.ps1` 会在本地开发库中：

1. 在停止服务或执行 Flyway 前先运行 `BusinessDataUpgradePreservationPolicyTest`；
2. 核对统一清单中的 35 张受保护表全部存在；
3. 记录每张表的精确行数；
4. 在一致性事务中按升级前已有列导出业务记录并计算 SHA-256，使未来兼容性新增列
   不会造成误报，同时原有列和值必须完全不变；
5. 对配置的本地上传目录生成物理文件路径、长度和 SHA-256 清单；
6. 重新打包、重启后端并执行 Flyway；
7. 再次比较逐表行数、数据库内容指纹、上传文件清单和 Flyway 版本。

只有所有表行数、原有列完整内容指纹和上传文件清单均未变化，升级保留验证才通过。
脚本仅用于本地开发环境，不连接或操作生产数据库。

根目录 `build-release.bat` 即使后续使用 `mvn package -DskipTests`，也必须先单独运行
资产数据和 RBAC 升级保护守卫；任一守卫失败时停止生成发布包。
