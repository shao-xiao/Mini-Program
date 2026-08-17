# Parking Billing V36 Deployment

## Scope

V36 adds a generated active billing month and a unique key for forward-only parking bill uniqueness.
It does not backfill, delete, void, or otherwise change historical parking bills.

Historical rows with `billing_month IS NULL` remain valid legacy rows and do not occupy the new key.
New manual and synchronized bill paths must continue to write a legal `YYYY-MM` billing month.

## Production Safety Boundary

- Never copy local test amounts or monthly fees to production.
- Never run `scripts/dev/reset-local-parking-test-data.sql` outside `dehui_property_dev`.
- Do not start a build containing V36 until the production precheck has been reviewed as `READY`.
- A `BLOCKED` result requires a separate, approved production data-governance plan.
- Do not let Flyway become the mechanism for cleaning production history.

## Deployment Order

1. Back up the production database and verify that the backup can be restored.
2. Check `flyway_schema_history`. If production is below V35, first deploy the V35-only `a9d40a5` build in a maintenance window and verify that only V35 was applied. Do not start the V36 build yet.
3. Run the V35 prerequisite section of `scripts/audit/parking-v36-production-precheck.sql` using a read-only account. Stop when it reports `BLOCKED_V35_FIELDS_MISSING`.
4. Once the V35 prerequisite is ready, run the complete production precheck using the same read-only account.
5. Review every result set, especially billing month format, duplicate groups, active assignment fees, statuses, and partial V36 objects.
6. Confirm that the final result is `READY`.
7. Schedule a maintenance window and deploy the build containing V36.
8. Verify `flyway_schema_history` reports V36 as successful.
9. Re-run the production precheck as a read-only post-check; generated column and unique index should now both exist.
10. Test manual bill creation, synchronized generation, duplicate synchronization, payment, duplicate payment, voiding, and regeneration after voiding.
11. Observe application and database logs for duplicate-key handling or migration failures.

## Stop Conditions

Do not deploy when any of the following is true:

- V35 fields are missing.
- A non-null `billing_month` is malformed.
- Two effective bills share the same assignment and non-null billing month.
- An effective assignment has no confirmed positive `monthly_fee`.
- Unexpected parking bill status or payment status values exist.
- Only one of the generated column and unique index already exists.
- The read-only precheck returns `BLOCKED` or does not complete.

## Rollback

If V36 migration fails, stop the deployment and restore the prior application version. Do not manually drop or rewrite partially applied production objects until the database state and Flyway history have been reviewed. Production data corrections require a separate approved script and backup.

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
