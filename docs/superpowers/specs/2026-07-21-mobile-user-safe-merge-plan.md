# Mobile User Safe Merge Plan

This plan is for a confirmed historical split between an OpenID user and a phone-only user. It is not an automatic migration and must not run without manual ownership confirmation and a database backup.

## Canonical record

1. Keep the `mobile_user` referenced by the verified current real app/OpenID session as the canonical user. Never select a `dehui-dev-miniprogram` identity as canonical merely because it is older.
2. Confirm phone ownership, tenant contact, timestamps, and operator evidence before pairing records.
3. Stop if the two records have conflicting active tenant or internal bindings.

## Transaction order

Within one reviewed transaction:

1. Lock both `mobile_user` rows and their active `wechat_identity` and `mobile_user_bind` rows.
2. Move a verified real `wechat_identity.mobile_user_id` only when the target app/OpenID remains unique. Disable legacy fake-AppID identities after their business references have been reconciled; do not attach a fake OpenID to the canonical user.
3. Resolve active binding conflicts, then move retained `mobile_user_bind.mobile_user_id` references.
4. Move `meeting_booking.applicant_mobile_user_id` and `work_order.submitter_mobile_user_id`.
5. Move mini-program `investment_lead.created_by` values so PUBLIC-stage viewing appointments remain visible after identity upgrade.
6. Move `notice_read_record.reader_id` only for mobile reader types, deduplicating the `(notice_id, reader_type, reader_id, deleted)` unique key first.
7. Copy the verified phone and the effective binding identity to the canonical `mobile_user`. Because `mobile_user(phone, deleted)` is unique, preserve the old value in the merge audit, clear it from the duplicate row, and assign it to the canonical row within the same transaction.
8. Re-run the reference inventory and compare business row counts before and after.
9. Disable the duplicate record with an audit remark. Do not hard-delete it.

## Validation

- The canonical user has exactly one active app/OpenID identity.
- The canonical phone is unique among non-deleted users.
- Meeting bookings, work orders, investment leads, and notice reads retain their original row counts.
- The canonical user can still see PUBLIC-stage investment bookings after becoming TENANT.
- Old tokens for the disabled duplicate return 401; current tokens derive identity and tenant scope from the database.

## Current development database result

The read-only diagnostic on 2026-07-21 found:

- zero duplicate phone groups;
- zero duplicate app/OpenID groups;
- zero direct OpenID/phone split groups;
- 20 active legacy `dehui-dev-miniprogram` identity rows;
- 1 active tenant-contact binding group shared by multiple mobile users;
- 1 active internal-account binding group shared by multiple mobile users.

The 20 legacy identities include 1 user with a phone, 15 users declared as TENANT, 19 active bindings, 1 meeting booking, and 3 work orders. They cannot be mapped to a real OpenID without new login evidence. No merge, delete, disable, or migration was executed during this phase.

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
