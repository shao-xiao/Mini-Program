# Parking Billing V35 Manual Review List

- Audit date: 2026-07-16
- Source: local development database, read-only queries
- Audit SQL: `docs/superpowers/specs/parking-billing-v35-audit.sql`
- This file is not a cleanup script. No row listed here was changed.

## Duplicate Candidate Periods

`due_date` month is only a candidate for historical review, not the billing-period truth.

| Assignment ID | Candidate month | Bill IDs | Count | Paid zero-amount bill |
| --- | --- | --- | ---: | --- |
| 1 | 2026-07 | 1503, 1505, 1507, 1509 | 4 | 1509 |
| 2 | 2026-07 | 1504, 1506, 1508, 1510 | 4 | 1510 |

The two groups contain six records beyond a one-record-per-assignment candidate baseline.

## Active Zero-Amount Bills

| Bill ID | Assignment ID | Pay status | Business status | Due date |
| ---: | ---: | --- | --- | --- |
| 1503 | 1 | UNPAID | ACTIVE | 2026-07-13 |
| 1505 | 1 | UNPAID | ACTIVE | 2026-07-13 |
| 1507 | 1 | UNPAID | ACTIVE | 2026-07-13 |
| 1509 | 1 | PAID | ACTIVE | 2026-07-13 |
| 1504 | 2 | UNPAID | ACTIVE | 2026-07-13 |
| 1506 | 2 | UNPAID | ACTIVE | 2026-07-13 |
| 1508 | 2 | UNPAID | ACTIVE | 2026-07-13 |
| 1510 | 2 | PAID | ACTIVE | 2026-07-13 |

## Other Audit Results

- Missing `due_date`: none.
- Missing or orphaned `assignment_id`: none.
- Unexpected status combinations: none under the current state vocabulary.
- Active assignments requiring a real monthly fee: assignment IDs 1 and 2.
- V36 remains blocked until every active bill has an approved `billing_month`, conflicts are resolved, and required assignment fees are configured.

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
