# 项目交接摘要

## Recent Change - 2026-07-14 Data Workspace Public Capabilities

Scope:
- Shared metric, import/export, and selection-action rendering for the equipment, work-order, and energy workspaces
- No new business-page migration and no backend, API, database, RBAC, or permission-code change

Completed:
1. Extended the validated `WorkspaceConfig` contract with a dedicated `importExport` action collection and duplicate-action-id validation. Metrics, import/export actions, and batch actions are now owned by each workspace schema instead of repeated Vue templates.
2. `DataWorkspace` now renders the existing config-driven `MetricPanel` directly from `workspaceConfig.metrics` plus page-provided real metric data. The equipment, work-order, meter, and reading pages no longer hardcode metric definitions in slots.
3. Added `ImportExportManager`. It filters schema actions through the existing `hasPermission` helper, supports standalone or grouped export/template actions, owns per-action file inputs and accept rules, and emits action/file events without issuing API requests.
4. Added `BatchToolbar`. It filters `workspaceConfig.batchActions` through `hasPermission`, renders selection counts and configured action tones/icons, and emits action identifiers. Existing equipment delete/edit/export and work-order assign/export/close handlers and endpoints remain page-owned.
5. Wired `DhicDataToolbar` to render `ImportExportManager` from the active workspace schema and wired `DataWorkspace` to render `BatchToolbar`. Equipment import/export, work-order export, and energy reading template/import/bill-generation workflows retain their original parsers, request functions, validation, and user feedback.
6. Equipment, work-order, and energy schemas now carry their transfer-action metadata and permission mappings. Adding another workspace can reuse all three public renderers through config; its real data loader and business mutation handlers must still bind approved existing APIs.
7. Added contract and source-wiring tests covering duplicate action IDs, permission-filtered action lookup, config-driven metrics, import file events, batch events, and the three migrated module integrations.

Verification:
- Full frontend Node suite passed: 124/124.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed apart from Windows line-ending notices.
- No backend source, API route, request/response field, database migration, role rule, or permission code was changed.

## Recent Change - 2026-07-14 Energy Data Workspace Migration

Scope:
- Energy management as the third config-driven data-workspace migration
- Meter archives and reading records migrated together without changing backend APIs, database schema, billing logic, or RBAC codes

Completed:
1. Added `dehui-property-admin/src/workspaceConfigs/energyWorkspaceConfig.js` with two validated schemas: `createEnergyMeterWorkspaceConfig` for `/energy/meters` and `createEnergyReadingWorkspaceConfig` for `/energy/records`. Both use the existing shared `WorkspaceConfig`, `DataWorkspace`, `DhicDataToolbar`, `MetricPanel`, `CellRenderer`, and `useDhicTableView` contracts.
2. Meter fields are limited to values actually returned by `GET /energy/meters`: number, name, type, location/building/room/tenant, unit, multiplier, status, update time, and remark. Current/previous readings and usage were deliberately not placed on the meter page because the meter API does not return them.
3. Reading fields use the real `GET /energy/readings` response: meter relation, type, period, building/room/tenant, reading date, previous/current reading, usage, operator, anomaly state, bill state, unit price, settlement amount, update time, and remark. Shared renderers handle relations, numbers, dates, people, status tags, and money.
4. Added config-driven views for disabled meters, recently updated meters, abnormal readings, recently updated readings, and high-consumption readings. View state remains isolated by username and route and persists filters, AND/OR logic, sorting, grouping, visible/order/fixed fields, column widths, and row density.
5. Migrated both energy pages from fixed forms/tables to the common workspace layout. Real server-supported meter/reading filters are mapped to existing query parameters; unsupported multi-condition OR and date-range queries remain client-side on the loaded page instead of inventing backend behavior.
6. Preserved existing meter CRUD, price settings, monthly task generation, remote meter search, CSV template export/import, batch bill generation, reading create/update/delete, bill generation/posting/withdrawal, pagination, and all existing request functions.
7. Permission checks now consume schema mappings while retaining the same codes: `energy:meter:view/create/update/delete`, `energy:view`, `energy:create`, `energy:update`, and `energy:reading:update/delete`. Permission coverage tests read the workspace schema as the source of page action codes.
8. Metrics use real counts, abnormal totals, usage totals, and settlement amounts. A year-on-year trend metric was deliberately omitted because the current `/energy/stats` response returns no usable trend series. No placeholder trend or mock data was introduced.

Verification:
- Energy/workspace/permission focused Node tests passed: 12/12.
- Full frontend Node suite passed: 121/121.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed apart from Windows line-ending notices.
- In-app browser visual automation remains blocked by the existing `Cannot redefine property: process` plugin initialization error; browser state and credentials were not modified.

## Recent Change - 2026-07-14 Data Workspace Schema Configuration

Scope:
- Configuration layer shared by the equipment and work-order data workspaces
- No additional business-page migration and no backend, API, database, or RBAC change

Completed:
1. Added a validated `WorkspaceConfig` contract and table-option adapter in `dehui-property-admin/src/workspaceConfigs/workspaceConfig.js`. The contract centralizes workspace identity, route/storage keys, fields, views, metrics, status metadata, permission mappings, page actions, batch actions, and table defaults.
2. Added `equipment.js` and `workorder.js` workspace schemas. Equipment and work-order field definitions, default views, status colors, metric definitions, permission codes, toolbar actions, batch actions, fixed columns, and row-density defaults no longer live inside their Vue pages.
3. `DataWorkspace` now accepts `workspaceConfig` and resolves the title, subtitle, and fallback views from it. User runtime views are still supplied by `useDhicTableView`, preserving username and route isolation plus saved filters, sorting, grouping, field layout, widths, fixed columns, and density.
4. `EquipmentList.vue` and `WorkOrderList.vue` consume their schemas through `workspaceTableOptions`. Header labels, metric definitions, data-action labels, batch-action visibility, inline-edit checks, and operation permissions use the schema permission map instead of repeating permission strings.
5. API calls, request parameters, dialogs, validation, status transitions, import/export execution, and business mutation handlers deliberately remain in the page layer. Config files contain metadata and value adapters only; they do not issue requests or invent endpoints.
6. Added configuration contract tests and migrated source-coverage tests so they verify both page wiring and schema-owned fields, views, status tones, actions, and permission codes.

Verification:
- Workspace/equipment/work-order focused Node tests passed: 18/18.
- Full frontend Node suite: 118/119 passed. The only failure is the pre-existing untouched energy-reading withdraw confirmation-text assertion.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed; generated `dist`, `node_modules`, backend `target`, and `release` remain ignored.

## Recent Change - 2026-07-14 Work Order Data Workspace Migration

Scope:
- Work-order management as the second data-workspace migration
- Existing work-order APIs, RBAC permission codes, database schema, and business status flow remain unchanged

Completed:
1. Migrated `WorkOrderList.vue` from the fixed query-card/table layout to the established `DataWorkspace` composition. It now reuses named views, type-aware filters, field visibility/order/fixed-column management, sorting, grouping, column resizing, compact/default/comfortable density, shared metrics, selection actions, and the shared cell renderer/editor.
2. Added real named views backed by existing query fields: `全部工单`, `待处理工单` (`SUBMITTED`), `我的工单` (current `userId` as handler), and `已完成工单` (`COMPLETED`). View state remains isolated by username and `/workorders` and saves filters, sort/group rules, field layout, column widths/fixed columns, and row density.
3. Configured the real list fields returned by `GET /workorders`: order number, title, order type, category, status, priority, reporter, tenant, handler, location, created time, last update time, and remark. Tenant uses the shared relation renderer; reporter and handler use the shared user renderer; work-order timestamps now use the shared minute-level datetime format.
4. Status cells now use semantic tags: pending is amber, assigned/processing is blue, completed is green, and closed/cancelled is neutral gray. The shared renderer gained the missing primary-blue status dot without changing existing field types.
5. Preserved and tightened RBAC actions. Create uses `workorder:create`; ordinary status updates/start use `workorder:update`; assignment uses `workorder:assign`; completion uses `workorder:complete`; closing uses `workorder:close`; list/current-page/selected-row CSV export uses `workorder:view`. Target status selection cannot use update permission to bypass assign, complete, or close permissions.
6. Added permission-controlled batch assignment, selected-row CSV export, and batch close. Batch assignment only submits selected pending rows, and batch close only submits selected completed rows. Both write actions use `Promise.allSettled` over the existing per-record endpoints and report partial failures without inventing batch APIs.
7. Deliberately did not add an overdue view, equipment field, completion time, or processing duration. The current work-order table/response has no equipment relation, completed-time field, SLA standard, or reliable overdue flag. Deliberately kept the work-order remark read-only because the existing generic status endpoint records a status-history remark rather than updating `work_order.remark`.
8. No backend source, API route, request/response field, database schema, Flyway migration, permission code, role template, or other business page was changed.

Verification:
- Work-order and shared-cell focused Node tests passed: 9/9.
- Full frontend Node suite: 115/116 passed. The only remaining failure is the pre-existing untouched energy-reading withdraw confirmation-text assertion.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed.
- In-app browser visual automation remains blocked by the existing `Cannot redefine property: process` plugin initialization error; the browser session and stored credentials were not reset or inspected.

## Recent Change - 2026-07-14 Equipment Workspace Unified Experience

Scope:
- Equipment-only data-workspace product refinement; no other business-page migration
- Unified view entry, table controls, typed status display, and a future relation-field contract

Completed:
1. Moved the equipment title and active view selector into one `DataWorkspace` header. The selector lists the default, maintenance, and fault system views and keeps create, copy, save, rename, and delete actions. Personal views continue to save filters, sort/group rules, field visibility/order, column widths/fixed columns, custom fields, and row density by username and route.
2. Consolidated equipment table controls into one toolbar ordered as filter, sort, group, fields, row height, import, and export. The create action stays in the page header, while quick keyword search and the current-page count stay aligned at the toolbar edge. Existing import, template export, current-page export, and selected-row export all use real loaded data and existing endpoints.
3. Removed the redundant outer Element Plus card from the equipment page so `DataWorkspace` is the single framed work surface. Existing metrics, filter tags, batch actions, grid, pagination, column resizing, compact density, and fixed columns remain intact.
4. Equipment status cells now use shared semantic tags with a green normal dot, amber maintenance dot, red fault dot, and neutral disabled dot. Dates, numbers, money, people, attachments, and other shared renderers remain unchanged.
5. Added the renderer-only `relation` field type and `normalizeCellRelations` contract. It accepts backend-provided identifiers and labels and renders related-record tags, but remains read-only and is not advertised as a backend-persisted custom field.
6. Added `docs/superpowers/specs/2026-07-14-data-workspace-relation-field.md` to define future relation configuration without inventing building, room, tenant, supplier, or work-order APIs.
7. Deliberately did not add `过保设备` because warranty is still a free-text custom field and there is no reliable warranty-end query. Deliberately did not add `我的关注` because there is no persisted follow relation/API. Users can create honest personal views from supported filters.
8. No backend source, API field, database schema, Flyway migration, permission code, RBAC rule, or non-equipment business page was changed.

Verification:
- Equipment/workspace-focused Node tests passed: 25/25.
- Full frontend Node suite: 113/116 passed. The remaining pre-existing failures are in the untouched energy-reading withdraw assertion, work-order role/status assertion, and mini-program reporter-field assertion.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- In-app browser visual automation remains blocked by the existing `Cannot redefine property: process` plugin initialization error; browser credentials/session were not reset or inspected.

## Recent Change - 2026-07-14 Equipment Workspace Standard Template

Scope:
- Equipment-only workspace refinement; no other business-list migration
- Reusable field management, AND/OR filters, typed cells, and permission-gated inline editing

Completed:
1. Extracted the field controls from `DhicDataToolbar` into reusable `FieldManager`. It supports field visibility, native drag ordering, accessible up/down ordering, fixed-left columns, custom fields, visible-field counts, and saving the current field layout through the existing explicit view-save flow.
2. Added view-level `filterLogic` with `and`/`or` options. The logic is normalized, copied, reset, saved, and isolated with the rest of each workspace view by username and page route. Active filter tags show `满足全部` or `满足任一`, and enum tags resolve configured Chinese labels instead of raw codes.
3. Extended the shared field registry and `CellRenderer` with `money` and `status`. Amounts use configured currency/precision; status remains an option-backed Element Plus tag. These renderer-only types are not exposed as backend-persisted custom-field types because the current backend contract does not support them.
4. Added reusable `CellEditor`. Equipment cells only enter edit mode when the column is explicitly editable and `hasPermission('asset:update')` is true. Text, number/money, date, select/status, multi-select, and checkbox controls are supported; formula, user, and attachment cells remain read-only.
5. Equipment inline saves reuse the existing full `PUT /equipments/{id}` contract, status `PATCH /equipments/{id}/status`, and custom-field value endpoint. No partial-update DTO or new endpoint was invented. Required equipment names are still validated, and custom-field values reload from the backend after save.
6. Existing column resizing, compact/default/comfortable row heights, sorting, grouping, pagination, import/export, and permission-controlled batch edit/export/delete remain intact.
7. Multiple OR conditions cannot be represented by the current server query contract, so they are evaluated against the loaded page without sending an incorrect server-side AND query. Full-dataset OR filtering requires a separately approved backend query contract; no such API change was made here.
8. No backend source, database schema, Flyway migration, API field, permission code, or other business page was changed.

Verification:
- `cd dehui-property-admin && node --test src/config/*.test.js src/utils/*.test.js` passed: 71/71.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed.

## Recent Change - 2026-07-14 Equipment Workspace Phase 4

Scope:
- Equipment-ledger view editing and explicit persistence
- Shared field-type, cell-rendering, and metric foundations
- Design-only contract for a future persistent follow capability

Completed:
1. Added explicit equipment-view persistence. Filters, sorts, groups, visible-field layout, field order, column widths, fixed columns, custom fields, and row density remain draft state until the user saves the current view. The view selector marks unsaved changes and prompts to save or discard before switching.
2. Added a shared field-type registry for `text`, `number`, `date`, `select`, `multi-select`, `user`, `attachment`, and `formula`, while retaining compatibility with the existing `singleSelect` and `multiSelect` custom-field values. Only types supported by the current backend contract are offered for persisted custom-field creation.
3. Added reusable `CellRenderer` handling text, numbers, dates, select tags, multi-select tags, people, attachments, formulas, checkbox, email, and URL values. Formula cells display supplied values only and do not evaluate expressions; unsafe attachment and URL protocols are rejected.
4. Upgraded `MetricPanel` definitions with `count`, `money`, `ratio`, and `trend` formatting. Optional trend metadata now renders direction and favorable/unfavorable semantics without changing existing metric callers.
5. Equipment status and type cells now use the shared renderer, while existing import/export, custom-field values, pagination, batch actions, API paths, and `asset:*` RBAC checks remain unchanged.
6. Added `docs/superpowers/specs/2026-07-14-universal-follow-capability-design.md`. It defines a future server-persisted, permission-aware follow contract for equipment, contracts, tenants/customers, and work orders. No localStorage imitation, backend endpoint, Flyway migration, database field, or placeholder “my follows” view was added.
7. No other list page was migrated. No backend source, API contract, permission code, database schema, import/export behavior, or RBAC model was changed.

Verification:
- `cd dehui-property-admin && node --test src/config/*.test.js src/utils/*.test.js` passed: 67/67.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed.
- Authenticated visual automation was not completed because the in-app browser runtime still fails during initialization with `Cannot redefine property: process`; the existing browser session and credentials were not reset or modified.

## Recent Change - 2026-07-14 Equipment Workspace Phase 3

Scope:
- Final equipment-workspace template experience before any other list-page migration
- Reusable view selector and metric panel

Completed:
1. Replaced the fixed view tabs with a compact view selector suitable for a growing number of views. Users can create, copy, rename, and delete personal views; system presets remain protected while still being copyable.
2. The default equipment view is now named `全部设备`. Copying a view clones its complete saved state: filters, sorts, groups, visible fields, field order, column widths, fixed columns, custom fields, and row density. Duplicate names receive a stable numeric suffix.
3. Added reusable `MetricPanel`, driven by definitions such as `{ title, field, desc, tone }`. Equipment metrics now consume this component rather than constructing `ListSummaryGrid` cards inside the page.
4. New and reset equipment views default to compact row density. Existing explicitly saved user density remains untouched. The equipment-name column stays fixed left, and the grid now keeps the horizontal scrollbar available for wide field sets.
5. Existing dynamic filters, filter tags, field drag ordering, column resizing, header sorting, grouping, batch edit/export/delete, import/export, pagination, custom fields, and RBAC checks remain intact.
6. No other list page was migrated. No backend, API contract, database schema, permission code, overdue placeholder, or followed-equipment placeholder was added.

Verification:
- `cd dehui-property-admin && node --test src/config/*.test.js src/utils/*.test.js` passed: 56/56.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed.

## Recent Change - 2026-07-14 Equipment Workspace Phase 2

Scope:
- Equipment-ledger workspace interaction and batch operations
- Shared named-view, field-layout, table-sort, and CSV export behavior

Completed:
1. Field management now supports drag-and-drop ordering while retaining the accessible up/down controls, field visibility, fixed-left columns, and per-view persistence.
2. Clicking a sortable equipment table header now updates the same saved sort state used by the toolbar. Column resizing, density, filters, groups, visible fields, field order, and fixed columns remain isolated by username and page route.
3. System preset views are merged into existing user workspace caches without overwriting saved preferences. Each preset stores its own reset baseline, so resetting the maintenance or fault view restores its defining status filter instead of turning it into the default view.
4. The equipment selection toolbar now provides permission-controlled batch edit (`asset:update`), batch export (`asset:view`), and batch delete (`asset:delete`). Batch edit reuses the existing equipment PUT or status PATCH endpoint for each selected row; batch export writes the selected real rows and current visible-column order to CSV.
5. Equipment metrics are declared through a configuration array and report only values backed by the existing equipment list API: total, filtered, normal, maintenance, fault, and disabled counts.
6. Existing create/edit/status/delete, import, import-template export, custom fields, server pagination, request wrapper, and RBAC checks remain unchanged. No backend, API contract, database schema, or permission code was modified.

Deliberately not implemented:
- `Overdue equipment` cannot be a reliable global view or metric yet. The current warranty period is a free-text custom field (for example a date range string), and `/equipments` has no warranty-end filter or aggregate.
- `My followed equipment` has no persisted equipment-follow relation or query endpoint. No placeholder records, stale row snapshots, or current-page-only imitation was added.

Verification:
- `cd dehui-property-admin && node --test src/config/*.test.js src/utils/*.test.js` passed: 52/52.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed.

## Recent Change - 2026-07-14 Data Workspace Platform Phase 1

Scope:
- Shared admin data-workspace components and table-view state
- Equipment ledger as the first standard workspace migration

Completed:
1. Added `DataWorkspace` and `WorkspaceViewManager` to compose named views, active filter tags, metrics, the data toolbar, selection actions, the data grid, and pagination without changing backend APIs or RBAC.
2. Upgraded `useDhicTableView` and `tableViewPrefs` with named views scoped by username and page route. Every view stores filters, multi-column sorting, grouping, visible fields, field order, column widths, fixed columns, and row density in localStorage.
3. Upgraded `DhicDataToolbar` with type-aware text, number, date, and enum filters plus field visibility, order, and fixed-column controls. Existing pages retain their previous toolbar behavior unless the new layout capabilities are explicitly enabled.
4. Migrated the equipment ledger to the shared workspace. It includes default, maintenance, and fault views, filter tags, summary metrics, field configuration, sorting, grouping, column resizing, fixed columns, density modes, row selection, and the permission-controlled batch toolbar.
5. Preserved equipment search, server pagination, import/export, custom fields, create/edit/status/delete flows, and `asset:create`, `asset:update`, and `asset:delete` checks. Existing `/equipments` query parameters are reused for keyword, equipment type, and status view filters.
6. No database schema, backend API field, permission code, RBAC model, or backend source file was changed. Detailed filters that do not have an existing server query parameter currently refine the loaded page only; this limitation avoids inventing or changing APIs.

Verification:
- `cd dehui-property-admin && node --test src/config/*.test.js src/utils/*.test.js` passed: 49/49.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- `git diff --check` passed; generated `dist`, `node_modules`, backend `target`, and `release` remain ignored.
- In-app browser automation could not initialize because the browser plugin returned `Cannot redefine property: process`; no browser session or stored credentials were reset as a fallback.

## Recent Change - 2026-07-14 Global List Display Sequence

Scope:
- Admin list views that previously displayed database primary-key columns

Completed:
1. Replaced visible primary-key columns with Element Plus index columns labeled `serial number` in user management, mini-program users, fee rules, announcements, inspections, visitors, investment leads, and leases. The role list already uses the same pattern.
2. Paginated lists calculate a continuous serial using the current page and page size; non-paginated lists start at 1. Deleting a row and reloading automatically recalculates the display order.
3. Database IDs remain unchanged in the row data and continue to be used for edit, delete, bind, status, and other API requests.

Verification:
- `cd dehui-property-admin && node --test src/config/*.test.js src/utils/*.test.js` passed: 43/43.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.

## Recent Change - 2026-07-14 Role List Display Sequence

Scope:
- Admin role list: `dehui-property-admin/src/views/system/RoleList.vue`

Completed:
1. Replaced the visible database `id` column with an Element Plus index column labeled `序号`.
2. Displayed rows are now numbered continuously from 1 in their current table order and are automatically renumbered after a role is deleted and the list reloads.
3. The real role ID remains unchanged and continues to drive edit, permission, status, and delete API requests.

Verification:
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- The browser session had returned to the login page, so no authenticated browser mutation or credential reuse was performed.

## Recent Change - 2026-07-13 RBAC Administrator Lockout Protection

Scope:
- Backend role lifecycle guards: `dehui-property-management/src/main/java/com/dehui/property/modules/system/controller/SystemAdminController.java`
- Admin-role repair migration: `dehui-property-management/src/main/resources/db/migration/V34__protect_system_admin_roles.sql`
- Admin role UI protection: `dehui-property-admin/src/views/system/RoleList.vue`, `dehui-property-admin/src/utils/systemRoleProtection.js`
- Backend, migration, and frontend tests for protected system roles

Completed:
1. Both role edit and dedicated status endpoints now load and lock the persisted role before mutation. `ADMIN` and `SUPER_ADMIN` reject every non-enabled target status, so the edit endpoint cannot bypass the status endpoint guard.
2. `ADMIN` and `SUPER_ADMIN` reject logical deletion. Additional enabled `SYSTEM` roles cannot be disabled when they are the final enabled system-administrator role.
3. Flyway V34 repairs existing non-deleted `ADMIN` and `SUPER_ADMIN` rows to `role_type = 'SYSTEM'` and `status = 'ENABLED'` without changing tables, permissions, menus, or user-role bindings.
4. The role list hides disable/delete actions for core administrator codes and every `SYSTEM` role. The edit dialog also hides the status field for those roles while preserving name, description, and permission configuration.
5. Existing RBAC permission codes, role-menu relations, role-permission relations, `AuthInterceptor`, and access refresh behavior are unchanged.

Verification:
- Backend targeted role/migration tests passed, then `mvn -q test` passed: 169/169 tests in 43 suites.
- Admin Node tests passed: 43/43. `npm run build` passed with only the existing chunk-size warnings.
- `mvn -q -DskipTests package` passed after stopping the old backend process that held the target JAR; the new JAR is running on port `8080` as PID `15804`.
- Flyway startup logs confirmed V34 was applied successfully and the local schema is now at version 34.
- Read-only browser verification confirmed `ADMIN` and `SUPER_ADMIN` are enabled, neither row exposes disable/delete actions, and the ADMIN edit dialog has no status field.

## Recent Change - 2026-07-13 Local Release Build Reliability

Scope:
- Local release script: `build-release.bat`

Completed:
1. Project paths now resolve from the script directory (`%~dp0`), with backend, frontend, and release paths derived from that root.
2. Backend packaging uses `mvn package -DskipTests` without `clean`, so a locally running backend cannot block packaging by locking the previous JAR in `target`.
3. JAR discovery excludes original artifacts, requires exactly one runnable candidate, validates the Spring Boot startup class, and writes both fixed and timestamped release files.
4. Frontend packaging validates `dist/index.html`, archives the complete `dist` directory, validates the ZIP content, and writes both fixed and timestamped release files.
5. The full build runs inside a logged subroutine so failures are always printed with a stage tag. Historical release files are retained, and no Git cleanup, checkout, reset, push, or source deletion is performed.
6. The batch file uses Windows CRLF without a BOM and supports `--no-pause` for automated verification while retaining pause behavior for normal double-click execution.
7. Fixed and timestamped backend JAR publication is strictly ordered: `app.jar` is copied and size-validated first, and the timestamped JAR is created only after that succeeds. Both files must match the source and each other, and both modification times are explicitly set to the current publication time and printed in the build log.
8. A successful full build now writes `release/version-info.txt` atomically with the build timestamp, current Git commit, current branch, actual backend artifact name, and frontend build status. Missing Git or artifact metadata fails the release instead of producing an incomplete manifest.

Verification:
- `cd dehui-property-management && mvn package -DskipTests` passed. The runnable artifact is `property-management-1.0.0-SNAPSHOT.jar` (58,763,170 bytes), with `JarLauncher`, `PropertyManagementApplication`, and the application class present.
- `cd dehui-property-admin && npm run build` passed and generated `dist/index.html`.
- `build-release.bat --no-pause` completed successfully for timestamp `20260713-202108`.
- Generated and validated: `release/app.jar`, `release/app-20260713-202108.jar`, `release/dist.zip`, `release/dist-20260713-202108.zip`, and `release/build-log-20260713-202108.txt`.
- The fixed JAR contains the Spring Boot startup class, the fixed ZIP contains `dist/index.html`, and all release artifacts remain ignored by Git.
- Follow-up run `20260713-203213` confirmed that both backend JARs are 58,763,170 bytes and both report modification time `2026-07-13 20:32:18`.
- An isolated locked-destination test confirmed that a failed fixed-file overwrite exits before the timestamped JAR is created.
- Follow-up runs `20260713-203653` and `20260713-203735` confirmed that `version-info.txt` is generated after all artifacts pass validation, uses clean UTF-8 output without a BOM, and records real repository and backend artifact values.

## Recent Change - 2026-07-13 Parking Bill Manual Creation

Scope:
- Admin parking bill page: `dehui-property-admin/src/views/parking/ParkingBillList.vue`
- Admin parking API: `dehui-property-admin/src/api/parking.js`
- Backend parking controller: `dehui-property-management/src/main/java/com/dehui/property/modules/parking/controller/ParkingController.java`
- Backend test: `dehui-property-management/src/test/java/com/dehui/property/modules/parking/ParkingControllerTest.java`

Completed:
1. Added a permission-controlled `新增停车账单` action alongside the existing bulk generation action. Both continue to use the existing `parking-bill:add` permission code.
2. Added `GET /api/parking/bills/assignments` to return active parking assignments with real space, plate, and tenant display fields.
3. Added `POST /api/parking/bills` for manual single-bill creation. The backend resolves `tenant_id` from the selected active assignment and validates a positive amount plus an ISO `YYYY-MM-DD` due date.
4. Updated the parking bill list query to return the camel-case display fields already consumed by the page and fixed paid/unpaid/void display and action eligibility to combine `pay_status` with bill status.
5. Did not add a table, migration, role, permission code, mock API, or frontend role check.

Verification:
- `cd dehui-property-management && mvn -q -Dtest=ParkingControllerTest test` passed.
- `cd dehui-property-management && mvn -q test` passed: 163/163 tests in 43 suites.
- Admin Node tests passed: 40/40.
- `cd dehui-property-admin && npm run build` passed with only the existing chunk-size warnings.
- Backend package succeeded and the latest JAR is running on port `8080`; frontend Vite remains on port `5173`.
- In-browser verification confirmed the add button, dialog, default month-end due date, and real active-assignment option. No test business bill was submitted.

## Recent Change - 2026-07-13 RBAC Accessible Home Route

Scope:
- Accessible route selection: `dehui-property-admin/src/config/access.js`
- Login redirect: `dehui-property-admin/src/views/Login.vue`
- Root route guard: `dehui-property-admin/src/router/index.js`

Completed:
1. Added a single accessible-home selector: prefer `/dashboard` only when it is visible, otherwise use the first visible leaf menu route, and use `/403` only when no page is available.
2. Login now refreshes `GET /api/system/user/access` before selecting the destination and uses `router.replace` instead of a fixed `/dashboard` push.
3. Removed the static `/ -> /dashboard` redirect. Root navigation now waits for the access refresh in the route guard before selecting the destination.
4. Direct navigation to an unauthorized URL is unchanged and still redirects to `/403`.
5. The 403 page remains available, and its return button now uses the same accessible-home selector instead of looping back to an unauthorized dashboard.

Verification:
- Admin RBAC Node tests passed: 25/25.
- `cd dehui-property-admin && npm run build` passed with only existing chunk-size warnings.
- Synthetic role with only `workorder:view` selects `/workorders`; SUPER_ADMIN selects `/dashboard`; a user with no visible page selects `/403`.
- Real `tech` access has no `dashboard:view`; its current first accessible menu route is `/contracts`, so login no longer targets `/dashboard` or `/403`.

## Recent Change - 2026-07-13 RBAC Wildcard Governance

Scope:
- Admin role templates: `dehui-property-admin/src/utils/roleAccessTemplates.js`
- Work order authorization/data scope: `dehui-property-management/src/main/java/com/dehui/property/modules/workorder/controller/WorkOrderController.java`
- Data migrations: `dehui-property-management/src/main/resources/db/migration/V32__rbac_wildcard_governance.sql`, `V33__workorder_update_permission.sql`

Completed:
1. Non-SUPER_ADMIN default role templates now select concrete permission codes only and never persist `xxx:*` entries. SUPER_ADMIN full access is unchanged.
2. Flyway V32 binds the existing `tech` account to the existing `ENGINEER` role, expands existing business-role wildcard grants to enabled concrete catalog permissions, and removes non-SUPER_ADMIN wildcard grants without changing the schema or permission names.
3. Removed ADMIN/ENGINEER/MAINTENANCE authorization branches from `WorkOrderController`. Endpoint authorization remains in `AuthInterceptor`; `workorder:assign` now represents full work-order management data scope, while users without it remain limited to assigned work orders.
4. Assignable work-order users are selected through enabled work-order permissions instead of fixed role codes.
5. V33 registers the existing `workorder:update` code already referenced by the UI/interceptor and restores it to roles with work-order management operations.

Verification:
- Flyway V32 and V33 applied successfully to local MySQL; `tech` now resolves to `ENGINEER` with 36 concrete permissions and zero wildcard permissions. Only SUPER_ADMIN retains wildcard grants.
- Runtime checks with the real `tech` login: `GET /api/workorders` returned 200 and `DELETE /api/workorders/999999999` returned 403.
- `cd dehui-property-management && mvn -q test` passed: 160/160 tests in 43 suites.
- `cd dehui-property-management && mvn -q -DskipTests package` passed; backend restarted on port 8080.
- Admin RBAC Node tests passed: 23/23. `cd dehui-property-admin && npm run build` passed with only existing chunk-size warnings.

## Recent Change - 2026-07-13 RBAC Access Refresh On Page Reload

Scope:
- Backend current access API: `dehui-property-management/src/main/java/com/dehui/property/modules/system/controller/AuthController.java`
- Backend access response DTO: `dehui-property-management/src/main/java/com/dehui/property/modules/system/dto/UserAccessResponse.java`
- Backend auth refresh: `dehui-property-management/src/main/java/com/dehui/property/modules/system/service/SystemAuthService.java`, `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`
- Admin frontend access refresh: `dehui-property-admin/src/utils/authAccess.js`
- Admin router guard: `dehui-property-admin/src/router/index.js`
- Admin login/request cache handling: `dehui-property-admin/src/views/Login.vue`, `dehui-property-admin/src/utils/request.js`
- Admin frontend test: `dehui-property-admin/src/utils/authAccess.test.js`

Completed:
1. Added `GET /api/system/user/access`, returning the current logged-in system user's latest `roles`, `permissions`, and `menus` from database role/menu/permission relations.
2. Frontend route guard now refreshes current user access once per token on first protected route entry, so browser refresh reloads the latest permission state before menu/route checks and component setup.
3. Login and refresh use the same access storage writer, and 401 cleanup now removes stale `permissions`, `menus`, and `userInfo`.
4. Backend protected API authorization now refreshes `SYSTEM` token roles/permissions from the database before checking required permission codes, so stale token permissions cannot keep authorizing removed access. Mobile tokens are left unchanged.
5. Did not change role table structure, permission codes, RBAC model, WebSocket behavior, or add a permission version mechanism.

Verification:
- `cd dehui-property-admin && node --test src/utils/authAccess.test.js src/config/access.test.js src/config/permissionRules.test.js src/utils/roleAccessTemplates.test.js src/utils/rolePermissionTree.test.js src/config/permissionLabels.test.js` passed: 20/20.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.
- `cd dehui-property-management && mvn -q "-Dtest=AuthInterceptorPublicMobileVisitorTest" test` is blocked by the pre-existing broken `SystemAdminControllerTest.java` test source encoding/syntax issue.
- Local backend restarted on port `8080` from the new jar. `GET /api/ping` returned 200.
- Runtime access API check: `admin / 123456` login succeeded; `GET /api/system/user/access` returned 200 with role `ADMIN`, 141 permissions, and 10 root menus.
- Runtime stale-token safety check: a temporary Redis token for user `xumin` with stale finance permissions was denied with 403 on `GET /api/bills` because the database role is currently `ENGINEER`.

## Recent Change - 2026-07-13 Permission Tree Chinese Labels

Scope:
- Admin permission label config: `dehui-property-admin/src/config/permissionLabels.js`
- Admin permission label test: `dehui-property-admin/src/config/permissionLabels.test.js`
- Admin role permission dialog display: `dehui-property-admin/src/views/system/RoleList.vue`

Completed:
1. Added a centralized `permission code -> Chinese label` mapping for permission tree operation labels.
2. The role permission tree now displays `permissionLabels[code]` first, then falls back to the original backend permission name/code.
3. Covered English-seeded permission names from V28, including `energy:meter:*`, `energy:reading:*`, `parking-bill:update`, `mobile:update`, `inspection:create/update`, `energy:update`, and `dashboard:view`.
4. Did not change permission codes, backend RBAC checks, role grants, `sys_role_permission`, or database migrations.

Verification:
- `cd dehui-property-admin && node --test src/config/permissionLabels.test.js` passed: 2/2.
- `cd dehui-property-admin && node --test src/config/permissionLabels.test.js src/config/permissionRules.test.js src/utils/roleAccessTemplates.test.js src/utils/rolePermissionTree.test.js src/config/access.test.js` passed: 18/18.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.

## Recent Change - 2026-07-13 RBAC Feature Permission Split

Scope:
- Admin permission rules: `dehui-property-admin/src/config/permissionRules.js`
- Admin role permission dialog: `dehui-property-admin/src/views/system/RoleList.vue`
- Admin action buttons: investment and meeting pages under `dehui-property-admin/src/views/investment/` and `dehui-property-admin/src/views/meeting/`
- Backend auth interceptor: `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`
- Backend role access save mapping: `dehui-property-management/src/main/java/com/dehui/property/modules/system/controller/SystemAdminController.java`
- Backend migration: `dehui-property-management/src/main/resources/db/migration/V31__split_investment_meeting_permissions.sql`

Completed:
1. Split duplicated menu permission groups so independent feature pages no longer share the same operation codes:
   - 招商内容: `investment-content:*`, `investment-content:view/create/update/delete/publish`
   - 招商房源: `investment-listing:*`, `investment-listing:view/create/update/delete/publish/image`
   - 招商线索: `investment-lead:*`, `investment-lead:view/follow/convert/delete`
   - 会议室管理: `meeting-room:*`, `meeting-room:view/create/update/delete`
   - 会议预约: `meeting-booking:*`, `meeting-booking:view/create/confirm/cancel/complete`
2. Kept `/payments` and `/invoices` on `bill:view`, and `/logs/operations` and `/logs/logins` on `system:user:view`, because those were explicit legacy RBAC mappings and are not separate role-tree menu feature groups.
3. Updated backend API permission mapping so investment and meeting endpoints check the new feature-specific permission codes.
4. Updated role access save normalization path mappings so menu visibility and view permissions use the new feature-specific view codes.
5. Added V31 Flyway migration to seed the new permissions and copy existing old grants to the new split permissions without deleting old codes, changing `role_code`, or touching `sys_user_role`.
6. Updated frontend action buttons on investment and meeting pages to use the new button-level permissions.

Verification:
- `cd dehui-property-admin && node --test src/config/permissionRules.test.js src/utils/roleAccessTemplates.test.js src/utils/rolePermissionTree.test.js src/config/access.test.js` passed: 16/16.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.
- Backend was restarted on port `8080`; Flyway validated 31 migrations and schema `dehui_property_dev` is at version `31`.
- `GET /api/system/permissions` confirmed the new `investment-*` and `meeting-*` permission codes are available to the role permission dialog.

Notes:
- `mvn test` / targeted JUnit test execution is currently blocked by pre-existing syntax/encoding errors in `src/test/java/com/dehui/property/modules/system/controller/SystemAdminControllerTest.java`; this was not introduced by this change.
- Existing unrelated dirty files remain untouched.

## Recent Change - 2026-07-10 RBAC Role Access Matrix Save Flow

Scope:
- Backend controller: `dehui-property-management/src/main/java/com/dehui/property/modules/system/controller/SystemAdminController.java`
- Backend auth interceptor: `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`
- Backend migration: `dehui-property-management/src/main/resources/db/migration/V28__rbac_role_matrix_roles.sql`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/system/controller/SystemAdminControllerTest.java`, `dehui-property-management/src/test/java/com/dehui/property/schema/SystemAccessSeedMigrationTest.java`
- Admin role page: `dehui-property-admin/src/views/system/RoleList.vue`
- Admin work order page: `dehui-property-admin/src/views/operation/WorkOrderList.vue`
- Admin access config: `dehui-property-admin/src/config/access.js`
- Admin permission utilities: `dehui-property-admin/src/utils/rolePermissionTree.js`, `dehui-property-admin/src/utils/roleAccessTemplates.js`
- Admin tests: `dehui-property-admin/src/utils/rolePermissionTree.test.js`, `dehui-property-admin/src/utils/roleAccessTemplates.test.js`

Completed:
1. Added unified role access endpoints:
   - `GET /api/system/roles/{id}/access`
   - `POST /api/system/roles/{id}/access`
2. Role access save now synchronizes `sys_role_menu` and `sys_role_permission` in one request, so menu visibility and operation permissions stay aligned.
3. The role permission dialog now uses one permission configuration flow instead of separate menu and permission dialogs.
4. Permission selection is normalized so operation permissions automatically include the matching view permission, and selected permissions derive the required child and parent menu ids before saving.
5. Role access save now validates requested menu and permission ids against enabled database rows, ignores unknown ids, and prevents stale frontend selections from writing orphan `sys_role_menu` or `sys_role_permission` rows.
6. Added default role access templates for the matrix roles: `SUPER_ADMIN`, `ADMIN`, `INVESTMENT_MANAGER`, `STAFF`, `FINANCE`, `ACCOUNTANT`, `CASHIER`, `ENGINEER_MANAGER`, `ENGINEER`, `SECURITY_MANAGER`, `SECURITY`.
7. Added Flyway migration `V28__rbac_role_matrix_roles.sql` to seed missing matrix roles and missing permission codes without deleting old roles or overwriting existing non-admin role assignments.
8. Kept `TENANT`, `PROPERTY_MANAGER`, and other old roles intact but outside the default matrix template list.
9. Updated `AuthInterceptor` permission mapping for role access save, inspections, energy meters/readings/price settings, and custom-field modules so route/API permission codes are closer to frontend permission rules.
10. Removed the old frontend hardcoded role restriction for `/workorders`; work order actions now rely on permission codes such as `workorder:assign`, `workorder:complete`, `workorder:close`, and finance bill permissions.

Verification:
- `cd dehui-property-admin && node --test src/utils/rolePermissionTree.test.js` passed.
- `cd dehui-property-admin && node --test src/utils/roleAccessTemplates.test.js` passed.
- `cd dehui-property-admin && node --test src/utils/rolePermissionTree.test.js src/utils/roleAccessTemplates.test.js src/config/access.test.js` passed.
- `cd dehui-property-management && mvn -q -Dtest=SystemAdminControllerTest test` passed.
- `cd dehui-property-management && mvn -q -Dtest=SystemAccessSeedMigrationTest test` passed.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.
- `cd dehui-property-admin && npm run build` passed. Vite only reported large chunk warnings.

Notes:
- This change does not implement data-scope permissions.
- No production database was operated directly; the Flyway migration file will run only when the target backend applies migrations.
- Existing unrelated dirty files remain untouched.

## Recent Change - 2026-07-06 Admin Login Background Photo

Scope:
- Admin frontend login page: `dehui-property-admin/src/views/Login.vue`
- Admin frontend image asset: `dehui-property-admin/src/assets/login-background.jpg`
- Admin frontend test: `dehui-property-admin/tests/loginBackground.test.mjs`

Completed:
1. Converted the provided DHIC exterior BMP photo into a web-friendly JPG asset.
2. Applied the photo as the full-screen login page background using a CSS variable and cover layout.
3. Kept the login card centered and improved readability with a subtle dark image overlay, translucent card background, blur, and shadow.
4. Added a static test to catch accidental removal of the login background asset wiring.
5. Refined the login card with the DHIC logo, clearer brand hierarchy, subtitle, compact footer, improved input focus states, and a stronger enterprise-style primary button.
6. Added a more polished enterprise card treatment: top brand accent, inner highlight, compact `DHIC ADMIN` tag, refined logo container, and concise security status line.
7. Upgraded the login page into a two-panel enterprise shell with a branded DHIC visual panel and a dedicated login form panel, improving depth, hierarchy, and desktop/mobile composition without changing login behavior.
8. Further refined the login entry with a persistent page brand mark, structural card frame, form-side logo mark, tighter glass treatment, and clearer visual hierarchy while keeping login API behavior unchanged.
9. Added explicit username/password field labels, a subtle form divider, a bottom-right platform footnote, and refined background/card layering to improve the login screen's professional visual hierarchy without changing authentication behavior.

Verification:
- `cd dehui-property-admin && node tests/loginBackground.test.mjs` passed.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.

## Recent Change - 2026-07-06 RBAC Permission Tree Three-Level Display

Scope:
- Admin frontend role page: `dehui-property-admin/src/views/system/RoleList.vue`
- Admin menu permission rules: `dehui-property-admin/src/config/permissionRules.js`
- Admin menu access filtering: `dehui-property-admin/src/config/access.js`
- Admin permission helpers: `dehui-property-admin/src/utils/permission.js`
- Admin permission tree utility: `dehui-property-admin/src/utils/rolePermissionTree.js`
- Backend Flyway migration: `dehui-property-management/src/main/resources/db/migration/V27__dashboard_view_permission.sql`
- Admin tests:
  - `dehui-property-admin/src/config/access.test.js`
  - `dehui-property-admin/tests/rolePermissionTree.test.mjs`
  - `dehui-property-admin/tests/permissionWildcard.test.mjs`

Completed:
1. Role permission assignment dialog now displays permissions as Level 1 business module -> Level 2 menu/page -> Level 3 operation permission.
2. The permission tree is built from current backend menus when available and falls back to the local left-menu definition when backend menus are stale.
3. Existing permission codes are not renamed; saving still posts the same selected permission id array to `/system/roles/{id}/permissions`.
4. Added module/feature bulk select, indeterminate state, expand/collapse all, search, selected/total counts, and fixed dialog footer.
5. Operation permissions automatically select the corresponding view permission; removing view clears operations under that feature.
6. Left menu filtering now requires the feature page view permission for non-admin roles; ADMIN/SUPER_ADMIN still see all permitted menus.
7. Wildcard permissions such as `system:*`, `tenant:*`, `workorder:*`, and `bill:*` are supported by frontend permission checks.
8. Added `dashboard:view` through Flyway so the dashboard can participate in the same view-permission rule.
9. Existing seeded permission codes map to menu pages except `feerule:view`, `feerule:add`, and `feerule:generate`; those are intentionally shown under unmatched permissions because the current left menu has no fee-rule page.

Verification:
- `cd dehui-property-admin && node src/config/access.test.js` passed: 3 tests.
- `cd dehui-property-admin && node tests/rolePermissionTree.test.mjs` passed: 3 tests.
- `cd dehui-property-admin && node tests/permissionWildcard.test.mjs` passed: 1 test.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.

## Recent Change - 2026-06-30 Equipment Import Duplicate Code Final Verification

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/building/controller/EquipmentController.java`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/building/EquipmentControllerTest.java`
- Admin frontend: `dehui-property-admin/src/views/building/EquipmentList.vue`
- Admin tests: `dehui-property-admin/tests/equipmentImport.test.mjs`

Database Evidence:
- Table: `equipment`
- Business unique field: `equipment_code`
- Soft delete field: `deleted`
- Unique key: `uk_equipment_equipment_code (equipment_code)`
- SQL checked:
  - `SELECT COUNT(*) total, SUM(CASE WHEN deleted=0 THEN 1 ELSE 0 END) active_count, SUM(CASE WHEN deleted<>0 THEN 1 ELSE 0 END) deleted_count FROM equipment`
  - `SHOW INDEX FROM equipment WHERE Key_name='uk_equipment_equipment_code'`
  - `SELECT id, equipment_code, deleted FROM equipment WHERE equipment_code IN ('CXZXGP001001','CXZXGP001002','CXZXTF001007') ORDER BY equipment_code, id`
  - `SELECT COUNT(*) FROM equipment e WHERE e.deleted = 0`
- Before runtime smoke test, local dev DB had `total=764`, `active_count=0`, `deleted_count=764`; sample codes `CXZXGP001001`, `CXZXGP001002`, and `CXZXTF001007` existed with `deleted=true`, explaining why the equipment list showed `Total 0`.

Completed:
1. Import now treats Excel fixed asset code as `equipment.equipment_code`.
2. Each row first queries `equipment_code` including soft-deleted records using `SELECT id, deleted FROM equipment WHERE equipment_code = ? ORDER BY deleted ASC, id DESC LIMIT 1`.
3. Active duplicate codes are updated and counted in `updatedCount`.
4. Soft-deleted duplicate codes are restored with `deleted = 0`, updated, and counted in `restoredCount`.
5. Missing or blank fixed asset code generates a unique `EQP-IMP-*` code and inserts the row.
6. `DuplicateKeyException` now re-queries including soft-deleted rows and updates/restores when possible; only unresolved duplicates become `failureCount` with an entry in `errors`.
7. Frontend import completion message now displays `insertedCount`, `updatedCount`, `restoredCount`, `skippedCount`, and `failureCount`.

Verification:
- `cd dehui-property-management && mvn -q -Dtest=EquipmentControllerTest test` passed.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.
- `cd dehui-property-management && mvn -q test` passed.
- `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs` passed: 7 tests.
- `cd dehui-property-admin && npm run build` passed; Vite only reported large chunk warnings.
- Full admin Node tests: `cd dehui-property-admin && node --test tests/*.test.mjs` reported 32 passed and 1 unrelated failure in `tests/workOrderPermissionsAndStatus.test.mjs`, where the test still expects old mini-program fields `contactName/contactPhone` while current code uses `reporterName/contactPhone`.
- Runtime smoke test against `http://127.0.0.1:8080/api/equipments/import`:
  - First import of `CXZXGP001001` returned `insertedCount=0`, `updatedCount=0`, `restoredCount=1`, `skippedCount=0`, `failureCount=0`.
  - Repeating the same import returned `insertedCount=0`, `updatedCount=1`, `restoredCount=0`, `skippedCount=0`, `failureCount=0`.
  - `GET /api/equipments?page=1&pageSize=20` then returned `total=1`, proving restored data is visible through the normal list query.

## Recent Change - 2026-06-30 Equipment Import Soft-Deleted Code Restore

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/building/controller/EquipmentController.java`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/building/EquipmentControllerTest.java`

Root Cause:
- The user's second import returned `successCount=0`, `updatedCount=0`, `skippedCount=278`, and `failureCount=0`.
- This means the frontend parsed the workbook successfully and the backend did not reject row content.
- Backend `skippedCount` was only incremented when MySQL raised a duplicate key but the import code could not find an active existing equipment row by `equipment_code`.
- Equipment deletion is soft delete (`deleted = 1`), while `equipment.equipment_code` is globally unique. Re-importing the same fixed asset codes after deleting visible rows hit the unique key, but the old lookup filtered `deleted = 0`, so the import skipped every row.

Completed:
1. `existingEquipmentId()` now finds matching `equipment_code` rows even when they are soft-deleted.
2. Import supplement logic now restores matched rows by setting `deleted = 0`.
3. Duplicate-key fallback also supplements/restores the matched row instead of only saving custom fields or skipping.
4. Added regression coverage for importing a fixed asset code that already exists as a soft-deleted equipment row.

Verification:
- TDD red: `cd dehui-property-management && mvn -q -Dtest=EquipmentControllerTest#importEquipmentsRestoresSoftDeletedEquipmentCode test` failed with `updatedCount=0`.
- TDD green: `cd dehui-property-management && mvn -q -Dtest=EquipmentControllerTest test` passed.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.
- `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs tests/equipmentListEnhancements.test.mjs` passed: 8 tests.

## Recent Change - 2026-06-30 Equipment Import Full-Width Header Compatibility

Scope:
- Admin frontend utility: `dehui-property-admin/src/utils/equipmentImportMapping.js`
- Admin tests: `dehui-property-admin/tests/equipmentImport.test.mjs`

Root Cause:
- The user's real equipment handover workbook uses a header like `设备／材料名称` with a full-width slash.
- The import mapper normalized ASCII `/` but did not strip the full-width `／`, so `设备／材料名称` did not match the expected equipment-name aliases.
- The frontend still sent rows to `/equipments/import`, but each row had an empty `equipmentName`, causing backend failures such as `首条失败：设备名称为空`.

Completed:
1. Added a regression test for `设备／材料名称` mapping to `equipmentName`.
2. Extended `normalizeImportHeader()` to strip full-width slash and common punctuation separators before alias matching.
3. Kept existing backend import endpoint and equipment ledger page flow unchanged.

Verification:
- TDD red: `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs` failed on full-width slash header, with `equipmentName` empty.
- TDD green: `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs tests/equipmentListEnhancements.test.mjs` passed: 8 tests.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.

## Recent Change - 2026-06-30 Equipment Ledger Route Import Fix

Scope:
- Admin frontend: `dehui-property-admin/src/views/building/EquipmentList.vue`
- Admin tests: `dehui-property-admin/tests/equipmentImport.test.mjs`

Root Cause:
- Clicking the sidebar `设备台账` menu triggered navigation to `/equipment`, but Vue Router could not load the dynamic page module.
- Browser console showed `Failed to fetch dynamically imported module: /src/views/building/EquipmentList.vue?...`.
- The failing module chain was caused by Vite returning `504 Outdated Optimize Dep` for `/node_modules/.vite/deps/jszip.js?...`; `jszip` was imported at the top of `EquipmentList.vue`, so a stale optimized dependency blocked the entire page from opening.

Completed:
1. Removed the top-level `import JSZip from 'jszip'` from `EquipmentList.vue`.
2. Added `loadJsZip()` and lazy-loaded `jszip` only when parsing `.xlsx` files.
3. Kept Excel import support unchanged for the actual import flow, while preventing the optional Excel dependency from blocking normal equipment ledger navigation.
4. Updated the equipment import test to require lazy `jszip` loading instead of top-level import.

Verification:
- Browser check on local Vite `5174`: after refresh, navigating to `/equipment` shows the `设备台账` page.
- `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs tests/equipmentListEnhancements.test.mjs` passed: 5 tests.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.

Follow-up Import Value Mapping:
- Added `dehui-property-admin/src/utils/equipmentImportMapping.js` to normalize handover spreadsheet headers before mapping rows to `/equipments/import`.
- The import mapper now tolerates spaces, slashes, newlines, BOM, and Chinese parentheses in headers such as equipment/material name, fixed asset code, manufacturer contact, warranty period, and system name.
- `EquipmentList.vue` imports the shared mapper and lazy-loads `jszip` only during `.xlsx` parsing, so equipment ledger navigation is not blocked by stale Vite optimized dependencies.
- Backend `EquipmentControllerTest` now asserts imported quantity is saved as JSON string `"10"` and existing-equipment import saves source sequence `"1"`.

Follow-up Verification:
- `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs tests/equipmentListEnhancements.test.mjs` passed: 6 tests.
- `cd dehui-property-management && mvn -q -Dtest=EquipmentControllerTest test` passed.
- Browser check on local Vite `5174`: `/equipment` renders `设备台账`, shows `导入设备`, and table rows include imported quantity/contact/warranty values.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.

Follow-up Excel Loader Fix:
- Clicking import with an `.xlsx` file still exposed Vite dev cache staleness through `Failed to fetch dynamically imported module: /node_modules/.vite/deps/jszip.js`.
- Replaced `await import('jszip')` with `jszip/dist/jszip.min.js?url` and a browser script loader that reads `window.JSZip`, so Excel import no longer depends on Vite optimized dependency cache.
- Verification: `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs tests/equipmentListEnhancements.test.mjs` passed: 6 tests.
- Verification: `cd dehui-property-admin && npm run build` passed and emitted `dist/assets/jszip.min-*.js` as a normal bundled asset.

Follow-up Handover Workbook Detection Fix:
- The import warning `没有找到可导入的设备数据` was caused by assuming the first non-empty row was the header row.
- Added `buildEquipmentImportRows()` in `dehui-property-admin/src/utils/equipmentImportMapping.js` to detect the real equipment header row after title/blank/metadata rows.
- The Excel parser now reads all worksheet XML files instead of only `sheet1`, so equipment data can be imported even when the workbook has a cover/instruction sheet before the actual ledger.
- `EquipmentList.vue` now sends rows through `buildEquipmentImportRows(rows)` before posting to `/equipments/import`.
- Verification: `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs tests/equipmentListEnhancements.test.mjs` passed: 7 tests.
- Verification: `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.

## Recent Change - 2026-06-30 Contract Floor Room Options Dynamic Building Load

Scope:
- Admin frontend: `dehui-property-admin/src/views/tenant/ContractList.vue`
- Admin tests: `dehui-property-admin/tests/contractMultiRoom.test.mjs`

Root Cause:
- The contract dialog floor selector showed `No data` because the frontend still hardcoded `BUILDING_ID = 1`.
- Current local database buildings are IDs `2`, `8`, and `9`; `/api/buildings/1/floors` correctly returned an empty list, while real floors exist under the actual building IDs.

Completed:
1. Removed the hardcoded `BUILDING_ID = 1` from the contract page.
2. `loadRooms()` now calls `/buildings`, then loads floors and rooms for each returned building with `/buildings/{building.id}/floors` and `/buildings/{building.id}/floors/{floor.id}/rooms`.
3. Floor and room labels include building/floor context so duplicate floor numbers across buildings are distinguishable.
4. Added a regression test ensuring the contract page does not use fixed building ID lookup.

Verification:
- API check: `/api/buildings` returned building IDs `2`, `8`, `9`; `/api/buildings/1/floors` returned `[]`, confirming the root cause.
- `cd dehui-property-admin && node --test tests/contractMultiRoom.test.mjs tests/contractElectronicFile.test.mjs` passed: 5 tests.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.
- `git diff --check -- dehui-property-admin/src/views/tenant/ContractList.vue dehui-property-admin/tests/contractMultiRoom.test.mjs` passed with only CRLF normalization warning.
- Local Vite server `5174` served `ContractList.vue` without `BUILDING_ID = 1` and with dynamic `/buildings/${building.id}/floors` and `/rooms` loading.

Follow-up UI Fix:
- Added a separate `楼宇` multi-select in the contract dialog before `楼层` and `房间`.
- `楼层` now only displays floor text such as `7F`; `房间` now only displays room number and area. Building names are no longer concatenated into floor/room option labels.
- Selecting buildings filters the available floor and room options, and changing buildings removes incompatible selected floors/rooms.
- Verification: `cd dehui-property-admin && node --test tests/contractMultiRoom.test.mjs tests/contractElectronicFile.test.mjs` passed: 6 tests; `cd dehui-property-admin && npm run build` passed; local Vite `5174` served the updated page with `form.buildingIds`, `buildingLabel`, and no `buildingName` in `floorLabel`/`roomLabel`.

Follow-up Layout Polish:
- Contract dialog room/period area now uses separate layout grids: building/floor/room in a `location-grid`, start/end dates in a `period-grid`, and fee fields in a `fee-grid`.
- Dialog body has a bounded scroll area and footer border so the save/cancel actions stay visually separated on shorter screens.
- Added a regression test for the contract dialog layout grid classes.
- Verification: `cd dehui-property-admin && node --test tests/contractMultiRoom.test.mjs tests/contractElectronicFile.test.mjs` passed: 7 tests; `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.

## Recent Change - 2026-06-30 Contract Multi-Room Selection

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/contract/controller/ContractController.java`
- Backend migration: `dehui-property-management/src/main/resources/db/migration/V23__contract_room_link.sql`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/contract/ContractControllerTest.java`, `dehui-property-management/src/test/java/com/dehui/property/schema/ContractRoomMigrationTest.java`
- Admin frontend: `dehui-property-admin/src/views/tenant/ContractList.vue`
- Admin tests: `dehui-property-admin/tests/contractMultiRoom.test.mjs`

Completed:
1. Added `contract_room` as the contract-to-room detail table and backfilled existing `contract.room_id` records through Flyway V23.
2. Kept `contract.room_id` as the primary/compatibility room while adding true multi-room persistence through `roomIds`.
3. `POST /api/contracts` and `PUT /api/contracts/{id}` now accept `roomIds`; single `roomId` remains supported for backward compatibility.
4. `GET /api/contracts` and `GET /api/mobile/contracts` now decorate contract rows with `rooms`, `roomIds`, `floorIds`, `roomNames`, and `roomSummary`.
5. Contract check-in/reactivate/terminate/cancel room-status updates now apply to every active room linked through `contract_room`, with fallback to legacy `contract.room_id`.
6. Admin contract dialog now uses multi-select for floors and rooms. Selecting 7F/8F filters rooms across both floors and submits all selected rooms.
7. Contract table now displays `roomSummary` when available, e.g. `1F B-101` or grouped multi-room summaries.

Verification:
- `cd dehui-property-management && mvn -q -Dtest=ContractControllerTest,ContractControllerElectronicContractTest,ContractRoomMigrationTest test` passed.
- `cd dehui-property-management && mvn -q test` passed.
- `cd dehui-property-admin && node --test tests/contractMultiRoom.test.mjs tests/contractElectronicFile.test.mjs` passed: 4 tests.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.
- `git diff --check -- dehui-property-management/src/main/java/com/dehui/property/modules/contract/controller/ContractController.java dehui-property-management/src/main/resources/db/migration/V23__contract_room_link.sql dehui-property-management/src/test/java/com/dehui/property/modules/contract/ContractControllerTest.java dehui-property-management/src/test/java/com/dehui/property/schema/ContractRoomMigrationTest.java dehui-property-admin/src/views/tenant/ContractList.vue dehui-property-admin/tests/contractMultiRoom.test.mjs` passed with only CRLF normalization warnings.
- Local backend `8080` restarted successfully and `/api/contracts` returned 200 with `roomIds` and `roomSummary`; sample `DEMO-CON-2026-003` returned `roomIds=[12]`, `roomSummary=1F B-101`.
- Local Vite server `5174` served `ContractList.vue` containing `form.floorIds`, `form.roomIds`, multi-select, `roomSummary`, and `roomIds: form.roomIds.map(Number)`.

## Recent Change - 2026-06-30 Equipment Import Supplements Existing Codes

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/building/controller/EquipmentController.java`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/building/EquipmentControllerTest.java`
- Admin frontend: `dehui-property-admin/src/views/building/EquipmentList.vue`
- Admin tests: `dehui-property-admin/tests/equipmentImport.test.mjs`

Completed:
1. Changed equipment import duplicate handling: if an imported fixed asset code / equipment code already exists, the import supplements the existing equipment instead of skipping it.
2. Existing equipment rows keep non-empty core values; blank core fields can be filled from the import row, and import remarks are appended when not already present.
3. Import custom fields are upserted for both new and existing equipment: quantity, manufacturer contact, warranty period, and source sequence.
4. `POST /api/equipments/import` now returns `updatedCount` in addition to `successCount`, `skippedCount`, `failureCount`, and `failures`.
5. The admin import success message now reports "新增 / 补充更新 / 跳过 / 失败" so users can see whether an import supplemented existing records.

Verification:
- `cd dehui-property-management && mvn -q -Dtest=EquipmentControllerTest test` passed.
- `cd dehui-property-management && mvn -q test` passed.
- `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs` passed: 4 tests.
- `cd dehui-property-admin && npm run build` passed. Vite only reported existing large chunk warnings.
- `cd dehui-property-admin && npm audit --omit=dev` returned `found 0 vulnerabilities`.
- Local API check after restarting backend `8080`: importing `CODX-EQP-SUPP-1782806203510` returned `successCount=1`; importing the same code again returned `updatedCount=1` and `skippedCount=0`; `/api/equipments?keyword=CODX-EQP-SUPP-1782806203510` returned exactly one record; `/api/custom-fields/equipment/values?recordIds=1508` returned updated quantity `3`, manufacturer contact `021-20000000`, and warranty period `2027-2031`.

## Recent Change - 2026-06-30 Equipment Import Excel Support And Batch Delete

Scope:
- Admin frontend: `dehui-property-admin/src/views/building/EquipmentList.vue`
- Admin dependencies: `dehui-property-admin/package.json`, `dehui-property-admin/package-lock.json`
- Admin tests: `dehui-property-admin/tests/equipmentImport.test.mjs`

Completed:
1. Fixed the equipment import button so it accepts modern Excel `.xlsx` files as well as CSV files.
2. Avoided the vulnerable `xlsx`/SheetJS package after `npm audit` showed high-severity advisories with no fixed version. The frontend now parses `.xlsx` using `jszip` plus browser XML parsing of the first worksheet.
3. Old binary `.xls` files are intentionally not accepted; users should save them as `.xlsx` or CSV before import.
4. Added table multi-select on the equipment ledger and a toolbar `批量删除` action. It reuses the existing `DELETE /api/equipments/{id}` endpoint for each selected row and reports success/failure counts.
5. Kept the existing single-row delete button and existing import backend APIs unchanged.

Verification:
- `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs` passed: 4 tests.
- `cd dehui-property-admin && npm run build` passed. Vite only reported the existing large chunk warnings.
- `cd dehui-property-admin && npm audit --omit=dev` returned `found 0 vulnerabilities`.
- `git diff --check -- dehui-property-admin/package.json dehui-property-admin/package-lock.json dehui-property-admin/src/views/building/EquipmentList.vue dehui-property-admin/tests/equipmentImport.test.mjs` passed; Git only warned that `EquipmentList.vue` may be normalized from LF to CRLF.
- Local Vite server `5174` served the updated equipment module containing `batchDeleteEquipment`, `handleSelectionChange`, `selectedRows`, and `parseXlsx`.

## Recent Change - 2026-06-30 Equipment Ledger CSV Import

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/building/controller/EquipmentController.java`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/building/EquipmentControllerTest.java`
- Admin frontend: `dehui-property-admin/src/views/building/EquipmentList.vue`
- Admin tests: `dehui-property-admin/tests/equipmentImport.test.mjs`

Completed:
1. Added equipment-ledger CSV template download at `GET /api/equipments/template`.
2. Added equipment-ledger CSV row import at `POST /api/equipments/import`.
3. The import supports the handover-table columns: sequence, equipment/material name, model/spec, quantity, installation location, manufacturer, manufacturer contact, warranty period, system name, remark, and fixed asset code.
4. Imported fixed asset codes are saved as `equipment.equipment_code`; as of 2026-06-30, duplicate fixed asset codes supplement the existing equipment instead of creating another row.
5. System names are mapped into equipment types: ventilation/fresh-air/air-conditioning to `HVAC`, fire to `FIRE`, power/high-voltage/strong-current/solar to `POWER`, water/drainage to `WATER`, elevator to `ELEVATOR`, weak-current/security to `SECURITY`, otherwise `OTHER`.
6. Extra handover-table fields are persisted as equipment custom fields and shown in the equipment table: `custom_quantity`, `custom_manufacturer_contact`, `custom_warranty_period`, and `custom_source_sequence`.
7. Admin equipment page now has `导出模板` and `导入设备` actions. Import is CSV-first; OCR was not added in this stable version.
8. Custom-field JSON writing now escapes backslashes, quotes, CR, and LF so CSV cells containing line breaks remain readable through `/api/custom-fields/equipment/values`.

Verification:
- `cd dehui-property-management && mvn -q -Dtest=EquipmentControllerTest test` passed.
- `cd dehui-property-management && mvn -q test` passed.
- `cd dehui-property-admin && node --test tests/equipmentImport.test.mjs` passed: 2 tests.
- `cd dehui-property-admin && npm run build` passed. Vite only reported the existing large chunk warnings.
- `git diff --check -- dehui-property-management/src/main/java/com/dehui/property/modules/building/controller/EquipmentController.java dehui-property-management/src/test/java/com/dehui/property/modules/building/EquipmentControllerTest.java dehui-property-admin/src/views/building/EquipmentList.vue dehui-property-admin/tests/equipmentImport.test.mjs` passed; Git only warned that `EquipmentList.vue` may be normalized from LF to CRLF.
- Local API check after restarting backend `8080`: template returned 200 and contained the expected Chinese CSV header; importing `CODX-EQP-IMPORT-1782804344799` returned `successCount=1`; importing the same fixed asset code again returned `skippedCount=1`; querying `/api/equipments` found one matching equipment; `/api/custom-fields/equipment/values` returned quantity, manufacturer contact including a newline, and warranty period.

## Recent Change - 2026-06-29 Mobile User Admin Tenant Assignment

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/mobile/controller/MobileUserAdminController.java`
- Backend security: `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`
- Backend migration: `dehui-property-management/src/main/resources/db/migration/V22__mobile_user_admin_menu.sql`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/mobile/MobileUserAdminControllerTest.java`
- Admin frontend: `dehui-property-admin/src/views/system/MobileUserList.vue`
- Admin router/menu: `dehui-property-admin/src/router/index.js`, `dehui-property-admin/src/config/access.js`
- Admin tests: `dehui-property-admin/tests/mobileUserAdmin.test.mjs`

Completed:
1. Confirmed WeChat phone quick-login users are stored in `mobile_user`; tenant/internal binding relationships are stored in `mobile_user_bind`; WeChat identity rows are stored in `wechat_identity`.
2. Added admin APIs: `GET /api/mobile-users`, `POST /api/mobile-users/{id}/bind-tenant`, and `POST /api/mobile-users/{id}/unbind-tenant`.
3. Binding a mini-program user to a tenant updates `mobile_user.user_type = TENANT`, soft-deletes previous TENANT binds, and inserts an enabled `mobile_user_bind` row with `tenant_id` and optional `tenant_contact_id`.
4. Unbinding a tenant soft-deletes TENANT binds and returns TENANT mobile users to `PUBLIC`.
5. Added the admin page at `系统管理 -> 小程序用户` (`/system/mobile-users`) for listing phone-login users, filtering by keyword/identity/status, assigning tenant companies, selecting optional tenant contacts, and unbinding tenants.
6. Added Flyway menu metadata `system:mobile-user` under the existing `system` menu and assigned it to ADMIN/SUPER_ADMIN roles. The backend permission check reuses `system:user:view` and `system:user:update`.
7. Seeded a local dev-only quick-login test mobile user for manual verification: phone `19900000001`, code `MPU_TEST_QUICK_001`, identity `PUBLIC`.
8. Fixed a keyword-filter SQL concatenation bug in `GET /api/mobile-users` where `mu.deleted = 0AND (...)` caused MySQL error `FUNCTION ... 0AND does not exist`; added a regression test for keyword search SQL spacing.

Verification:
- `cd dehui-property-management && mvn -q -Dtest=MobileUserAdminControllerTest test` passed.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.
- `cd dehui-property-management && mvn -q test` passed.
- `cd dehui-property-admin && node --test tests/mobileUserAdmin.test.mjs` passed: 2 tests.
- `cd dehui-property-admin && npm run build` passed.
- 2026-06-30 local API check: after restarting backend `8080`, `GET /api/mobile-users?keyword=19900000001&page=0&size=10` returned 200 with `id=1523`, `userType=PUBLIC`, and no tenant binding.

## Recent Change - 2026-06-29 Mini Program Phone Login Production Guard Fallback

Scope:
- Mini program: `dehui-property-miniprogram/pages/me/index.js`
- Mini program tests: `dehui-property-miniprogram/tests/phone-login-agreements.test.js`

Completed:
1. Diagnosed real-device phone login failure against production domain: `POST https://wuye.mingda.com.cn/api/mobile/auth/wechat-phone-login` currently returns `401 未登录或登录已过期` when called without a token, so the production backend is still blocking the pre-login WeChat phone-number login endpoint.
2. Kept the WeChat `getPhoneNumber` button and backend URL unchanged, but changed the mini-program phone login request to call `/mobile/auth/wechat-phone-login` silently first.
3. Added compatibility fallback for older/protected production backend behavior: if the phone-login request returns 401, the mini program clears the stale session, runs the existing `wx.login -> /mobile/auth/wechat-login` flow, then retries `/mobile/auth/wechat-phone-login`.
4. Confirmed production `/api/ping` is public even with an invalid Authorization header, so the remaining real-device toast was not caused by the health check.

Verification:
- `curl -i https://wuye.mingda.com.cn/api/ping` returned 200.
- `curl -i -H "Authorization: definitely-expired-token" https://wuye.mingda.com.cn/api/ping` returned 200.
- `curl -i -X POST https://wuye.mingda.com.cn/api/mobile/auth/wechat-phone-login` returned 401, matching the real-device toast.
- `cd dehui-property-miniprogram && node --check pages/me/index.js` passed.
- `cd dehui-property-miniprogram && node --test tests/phone-login-agreements.test.js` passed: 6 tests.
- `cd dehui-property-miniprogram && node --test tests/*.test.js` passed: 41 tests.

## Recent Change - 2026-06-29 Mini Program Login Stale Session Toast Fix

Scope:
- Mini program: `dehui-property-miniprogram/pages/me/index.js`
- Mini program tests: `dehui-property-miniprogram/tests/phone-login-agreements.test.js`

Completed:
1. Confirmed the "未登录或登录已过期" toast on the login page came from the automatic `/mobile/me` profile refresh when a stale local `token` still existed.
2. Changed the login page profile refresh to call `/mobile/me` through `api.request({ silent: true })`, so expired sessions are cleared silently before WeChat phone-number authorization.
3. Kept the WeChat `getPhoneNumber` flow, agreement gate, backend endpoint, and page structure unchanged.

Verification:
- `cd dehui-property-miniprogram && node --check pages/me/index.js` passed.
- `cd dehui-property-miniprogram && node --test tests/phone-login-agreements.test.js` passed: 5 tests.
- `cd dehui-property-miniprogram && node --test tests/*.test.js` passed: 40 tests.

## Recent Change - 2026-06-29 Mini Program Login Page Simplification

Scope:
- Mini program: `dehui-property-miniprogram/pages/me/index.*`
- Mini program tests: `dehui-property-miniprogram/tests/phone-login-agreements.test.js`, `tests/mine-account-and-investment-records.test.js`
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/mobile/controller/MobileAuthController.java`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/mobile/MobileAuthControllerTest.java`

Completed:
1. Refactored the mini-program account page into a simple login-first screen: project name, short service tagline, WeChat phone-number login, secondary phone/account-password entry, and agreement consent.
2. Moved phone registration and account-password binding forms into a secondary bottom sheet, so they no longer appear on the first screen.
3. Moved tenant/internal binding entry to the post-login account detail area and only shows it for PUBLIC users.
4. Kept `getPhoneNumber`, `/api/mobile/auth/wechat-phone-login`, phone registration, account-password binding, tenant binding, internal binding, `wx.openPrivacyContract`, invoice download, contract download, and work-order photo upload paths.
5. Updated WeChat phone-number login so the backend automatically binds TENANT when the authorized phone matches an active tenant contact, binds INTERNAL when it matches an enabled system user phone, and otherwise keeps/creates a PUBLIC mobile user.

Verification:
- `cd dehui-property-miniprogram && node --check pages/me/index.js && node --test tests/*.test.js` passed: 39 tests.
- `cd dehui-property-management && mvn -q test` passed.
- Mini-program privacy search found no runtime use of `getLocation`, `chooseLocation`, `openLocation`, `startLocationUpdate`, `getUserProfile`, `chooseMedia`, `chooseMessageFile`, recorder, camera/video/audio components, video upload, or audio upload.

## Recent Change - 2026-06-29 Mini Program Privacy And WeChat Phone Login

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/mobile/controller/MobileAuthController.java`
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/mobile/service/WechatMiniProgramClient.java`
- Backend security: `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`
- Backend config: `dehui-property-management/src/main/resources/application.yml`
- Mini program: `dehui-property-miniprogram/pages/me/index.*`
- Mini program: `dehui-property-miniprogram/pages/agreements/service/index.*`
- Mini program: `dehui-property-miniprogram/utils/privacy.js`, `utils/request.js`, `pages/workorders/index.js`
- Docs: `docs/wechat-miniprogram-privacy-guide.md`, `docs/wechat-miniprogram-privacy-copy.md`

Completed:
1. Added `/api/mobile/auth/wechat-phone-login` for WeChat phone-number authorization login. It exchanges `phoneCode` through the WeChat API and creates or reuses a PUBLIC mobile user by phone number.
2. Added `dehui.wechat.miniprogram.app-id` and `app-secret` config bindings to existing `WECHAT_MINIPROGRAM_APP_ID` and `WECHAT_MINIPROGRAM_APP_SECRET` environment variables.
3. Added mini-program account-page agreement gating. The real `open-type="getPhoneNumber"` button is rendered only after the user checks the service agreement and privacy-policy consent.
4. Added local user-service-agreement page and connected the privacy link to `wx.openPrivacyContract`.
5. Added mini-program privacy pre-authorization helper for work-order image selection. Upload/download wrappers only perform the actual photo upload or business-file download and are not documented as separate privacy abilities.
6. Added a WeChat admin privacy-guide configuration checklist for review submission.
7. Added a copy-ready WeChat admin privacy text template for manual paste into the blocked backend page.

Verification:
- `cd dehui-property-miniprogram && node --test tests/*.test.js` passed: 37 tests.
- `cd dehui-property-miniprogram && node --check pages/me/index.js` passed.
- `cd dehui-property-miniprogram && node --check pages/agreements/service/index.js` passed.
- `cd dehui-property-management && mvn -q -Dtest=MobileAuthControllerTest test` passed.
- `cd dehui-property-management && mvn -q -DskipTests package` passed.
- `cd dehui-property-management && mvn -q test` passed after updating `MobileEndpointCompatibilityTest.mineSummaryReturnsTenantDashboardShape` to include `investmentLeadCount` in the current summary count order.

## Recent Change - 2026-06-28 Inspection Record Edit/Delete

Scope:
- Backend: `dehui-property-management/src/main/java/com/dehui/property/modules/inspection/controller/InspectionController.java`
- Backend tests: `dehui-property-management/src/test/java/com/dehui/property/modules/inspection/InspectionControllerTest.java`
- Admin frontend: `dehui-property-admin/src/views/operation/InspectionList.vue`

Completed:
1. Added real backend `PUT /api/inspections/{id}` for editing existing inspection records.
2. Added real backend `DELETE /api/inspections/{id}` using the existing soft-delete pattern (`deleted = 1`).
3. Updated the inspection list operation column to include necessary actions only: edit, close/closed, delete.
4. Reused the existing inspection dialog for both create and edit; edit preserves the current record status instead of forcing it back to open.

Verification:
- `cd dehui-property-management && mvn -q -Dtest=InspectionControllerTest test` passed.
- `cd dehui-property-management && mvn -q -DskipTests compile` passed.
- `cd dehui-property-admin && npm run build` passed.
## 1. 项目目标

本项目是“德汇创新中心物业管理平台”，目标是形成面向写字楼/园区的物业运营管理系统，覆盖后台管理、Spring Boot 后端服务、微信小程序三端�?

当前业务方向不是 Demo，而是逐步演进为“AI + BI 物业运营平台”：

- 后台管理端：给管理员、物业运营、财务、员工使用，管理楼宇、楼层、房间、租户、合同、账单、停车、能耗、工单、访客、会议室、公告、招商、AI 分析等�?
- 后端服务：统一提供后台接口和小程序移动端接口，负责权限、业务校验、账单生�?审核、数据持久化�?
- 微信小程序：给租户、内部员工、访客使用，支持公告、账单提醒、会议预约、访客预约、工单报修、招商咨询、员工签到等�?
近期重点任务是把原“设备台账”升级为新的“空间资产模型”，但旧 `Equipment` 接口和页面必须保留，新增 `Asset` �?`AssetOperationLog` 作为新资产体系�?
## 最近变更记�?
### 2026-06-27 后台列表摘要数量口径统一
本次根据“摘要卡片应同时展示全部页数量和当前筛选数量”的反馈，统一 WEB 管理端列表页摘要口径：前三张基础卡片固定为“全部数�?/ 筛选结�?/ 当前页”，避免把当前页记录数、筛选后总数和系统全量总数混在一起�?
影响范围�?- WEB 管理端：新增 `dehui-property-admin/src/utils/listSummary.js`，统一生成列表摘要卡片与无筛选全量计数�?- 已接入分页全量计数的模块包括设备台账、资产管理、房间管理、表具管理、抄表管理、招商内容、招商线索、巡检管理、工单管理、停车车位、账单管理、系统用户等�?- 无分页或一次性加载的模块统一了文案和视觉口径，包含楼宇、租户、租约、合同、收费规则、会议室、会议预约、停车账单、公告、访客、角色等页面。楼层管理页后续按页面结构反馈移除了顶部摘要卡片，仅保留左侧楼宇列表和右侧所选楼宇楼层表格�?
实现原则�?1. 分页页面的“全部数据”通过同一接口不带筛选条件请求第一页计数；“筛选结果”使用当前查询接口返回的分页 `total`；“当前页”使用当前表格数组长度�?2. 无分页页面使用当前列表长度作为全部、筛选和当前页数量，保留原有业务卡片用于状态、金额、异常、容量等提醒�?3. 全量计数请求使用 `silent: true`，失败时不影响主列表渲染�?4. 不修改后端接口，不新�?mock，不绕过 `request.js`�?
验证结果�?- `cd dehui-property-admin && npm run build` 通过�?- 2026-06-27 追加调整：`/floors` 楼层管理页顶部摘要区域已删除，构建再次通过�?- 2026-06-27 追加调整：`/investment/contents` 招商内容页选中的顶部摘要区域已删除，保留内容表格和右侧小程序预览；浏览器确�?`.list-summary-grid` �?0，`npm run build` 通过�?
### 2026-06-27 后台侧边栏菜单拖拽排序恢�?
本次修复管理端左侧菜单“拖动排序入口消�?不可用”的问题。根因是 `MainLayout.vue` 已回退为普�?`visibleMenus` 渲染，只残留或缺失拖拽排序所需的排序状态、drop 计算和本地持久化逻辑�?
影响范围�?- WEB 管理端：
  - `dehui-property-admin/src/layout/MainLayout.vue`

已完成内容：
1. 侧边栏菜单由 `visibleMenus` 改为 `orderedMenus` 渲染，先�?RBAC 可见菜单计算，再叠加本地排序�?2. 一级菜单和同一父级下的二级菜单均支持通过左侧点阵手柄拖拽上下排序�?3. 排序结果保存到浏览器 `localStorage` �?`dhic-side-menu-order-v2`，刷新页面后保持用户自己的菜单顺序�?4. 新增同级拖放判断，一级菜单只能和一级菜单排序，二级菜单只能在同一父菜单下排序，避免跨层级误排序�?5. 拖拽手柄改为 CSS 点阵，不依赖特殊字符显示，避免字体或编码导致图标不可见�?
验证结果�?- `npm run build` 通过�?- 本地服务仍在运行：后�?`8080`、前�?`5173`�?- 浏览器页面已确认左侧菜单存在 `.drag-handle` 可拖拽手柄，当前菜单结构中共 35 个手柄，当前可视区域可见 14 个�?- 2026-06-27 追加优化：拖拽方式从原生 HTML5 `dragstart/dragover/drop` 改为 pointer 拖动，按住点阵手柄后根据指针位置实时重排，同级上下拖动一致；拖动中菜单项增加高亮、缩放和禁止文字选择反馈。浏览器实测“资产管理”拖到“租赁管理”下方后顺序即时变化，并已拖回原顺序；`npm run build` 通过�?- 2026-06-27 再次优化：移除左侧点阵手柄，改为按住菜单整行拖动排序；普通轻点仍执行页面跳转或展开菜单，拖动后会拦截本�?click，避免拖完误跳转。浏览器实测 `.drag-handle` 数量�?0、菜单行数量�?35；拖动“资产管理”整行到“租赁管理”下方后顺序即时变化，并已拖回原顺序；`npm run build` 通过�?- 2026-06-27 视觉反馈优化：拖动过程中增加目标插入位置提示，落点菜单会显示浅红背景和上/下红色插入线，被拖动项增加浮起阴影、左侧强调线和轻微位移；同时补充全局 click 捕获，拖动结束后不再误触发菜单跳转。浏览器实测�?`/bills` 拖动“资产管理”到“租赁管理”下方后顺序变化，URL 仍保�?`/bills`，并已拖回原顺序；`npm run build` 通过�?- 2026-06-27 长按触发优化：菜单排序改为长按整行约 420ms 后才进入拖拽模式，普通点击保持页面跳转或展开菜单；如果按下后马上移动，会取消本次排序准备，避免误触发拖拽。默认菜单行光标恢复为普通点击指针，进入长按排序模式后才显示拖拽手势和高亮反馈。浏览器确认 `.drag-handle` 数量�?0、菜单行数量�?35、默认光标为 `pointer`、长按样式已加载；`npm run build` 通过�?
### 2026-06-26 能耗抄表批量任务、导入读数与批量出账

本次把“几百个表具每月逐个新增抄表太累”的问题改为批量工作流：先按账期生成待抄表任务，再导�?CSV 模板现场填读数，导入后批量生成账单；单条“新增抄表”保留为补录入口，并改为远程搜索表具，避免只加载�?100 条导致后续表具找不到�?
影响范围�?- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/energy/controller/EnergyController.java`
- WEB 管理端：
  - `dehui-property-admin/src/views/energy/EnergyRecordList.vue`
  - `dehui-property-admin/src/api/energy.js`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/energy/EnergyControllerTest.java`
  - `dehui-property-admin/tests/energyReadingBatchWorkflow.test.mjs`

已完成内容：
1. 新增 `POST /api/energy/readings/monthly-tasks`，按账期、抄表日期、能耗类型、楼�?楼层/房间等范围，为启用表具生成本月待抄表记录；同一表具同一账期已有记录时自动跳过�?2. 新增 `POST /api/energy/readings/import`，支持按表号导入 CSV 读数；导入时会复用原有抄表保存逻辑，自动计算本期用量、结算金额和异常标记�?3. 新增 `POST /api/energy/readings/batch-generate-bills`，按当前筛选范围批量生成账单；默认只处理正常且 `NOT_GENERATED` 的抄表记录，避免异常读数直接出账�?4. 抄表页面标题区新增“生成本月任�?/ 导出模板 / 导入读数 / 批量生成账单”入口；“新增抄表”保留为补录入口�?5. 导出模板字段包含表号、表具名称、能耗类型、账期、抄表日期、上期读数、本次读数、单价、房间、租户、备注；导入时以表号作为准确关联键�?   - 2026-06-26 追加修复：导出模板改为后端真�?CSV 附件接口 `GET /api/energy/readings/template`；前端通过统一 `request.js` 携带 `Authorization` 获取 blob，并复用 `downloadBlob` 下载。不再展示“下载已生成模板”和“复制模板内容”两个临时入口；支持 `showSaveFilePicker` 的浏览器会优先弹出保存文件窗口�?   - 2026-06-26 追加修复：WPS/Excel 打开并另�?CSV 后，中文表头或日期格式可能被改写，导致导入提示“没有找到已填写本次读数的表具”。导入逻辑已改为“表头识�?+ 模板固定列兜底”：表号�?A 列、本次读数按 G 列、账�?抄表日期/单价/备注按模板对应列读取，并规范�?`2026/6/26` 这类日期�?   - 2026-06-26 追加修复：抄表上期读数改为按账期链路计算，只取目标账期之前最近一期读数；先导�?6 月再补录 5 月时�? 月不再把 6 月当上期。补录或更新较早账期后，系统会自动重算同一表具后续未生成账单的抄表记录，避免本期用量继续沿用错误上期值�?   - 2026-06-26 追加优化：能耗抄表列表改为工作台式表格，顶部展示当前结果、异常记录、本期用量、结算金额；表格将表具、房间、租户合并为主信息列，将读数、用量金额、状态分别归组展示，操作列收敛为主操�?+ 更多菜单，降低明细表一眼看不出重点的问题�?6. 新增抄表弹窗的表具选择改为远程搜索 `/api/energy/meters`，支持按表号、表名、房间、租户等关键字搜索，不再依赖一次性加载前 100 条�?7. 抄表列表首列�?`ID` 改为业务序号，减少后台主键对日常用户的干扰�?
验证结果�?- TDD 红灯：新增后端测试时，`generateMonthlyReadingTasks`、`importReadingValues`、`batchGenerateBills` 尚不存在导致编译失败；新增前端测试时，批量接口和页面入口不存在导致测试失败�?- 后端定向测试：`mvn "-Dtest=EnergyControllerTest" test` 通过�? 个测�?0 失败�?- WEB 测试：`node --test tests/energyReadingBatchWorkflow.test.mjs` 通过�?- WEB 构建：`npm run build` 通过�?- 后端编译：`mvn -q -DskipTests compile` 通过�?- 本地服务已从当前 `Back/prod-architecture-20260522` 工作目录重启：后�?`8080`，前�?`5173`�?- API 实测：`admin / 123456` 登录成功；`GET /api/energy/readings?page=1&pageSize=1` 返回 200；`GET /api/energy/meters?keyword=BULK-TEST&page=1&pageSize=1` 返回 200，当前测试表具总数�?300；三个新增接口路由均已验证，批量出账在未来账期无候选记录时返回 0�?- 浏览器验证：`http://localhost:5173/energy/records` 已显示“生成本月任�?/ 导出模板 / 导入读数 / 批量生成账单 / 新增抄表”，新增抄表弹窗显示“输入表号、表名、房间或租户搜索”�?- 2026-06-26 追加验证：`GET /api/energy/readings/template?periodMonth=2026-06&billStatus=NOT_GENERATED` 带后�?`Authorization` 头返�?200、`text/csv;charset=UTF-8` �?`Content-Disposition: attachment`；页面仅保留“导出模板”按钮，旧的“下载已生成模板”和“复制模板内容”入口已移除�?- 2026-06-26 追加验证：`node --test tests/energyReadingBatchWorkflow.test.mjs` �?`npm run build` 通过；前端导入代码包�?`mapReadingImportRow`、`fallbackReadingImportValue`、`normalizeImportDate`，用于兼�?WPS 保存后的模板列识别�?- 2026-06-26 追加验证：`mvn "-Dtest=EnergyControllerTest" test` 通过�? 个测�?0 失败；`mvn -q -DskipTests compile` 通过�?- 2026-06-26 追加验证：能耗抄表工作台式表格调整后，`npm run build` 通过�?
后续注意�?- CSV 导入目前按表号匹配表具，正式使用前应要求现场抄表模板不要改动表号列�?- 月度任务生成会把本次读数初始化为上期读数，导入读数后才形成真实本期用量�?- 批量生成账单默认跳过异常读数；异常读数应先人工确认或修正后再出账�?
### 2026-05-29 小程序会议记录模式、会议室主动查询与招商房源看�?
本次调整微信小程序会议与招商预约体验：`我的 -> 我的会议` 不再直接进入会议预约表单，而是进入会议预约记录模式；首页“会议预约”仍用于发起预约，但默认不铺开全部会议室，用户先选择日期和时间后点击查询，再选择可用会议室。招商中心预约看房页面在表单前新增可租房源看板，展示后端 MySQL 中空�?可租房源，方便用户先看房源状态再提交看房预约�?
影响范围�?- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/investment/controller/InvestmentController.java`
- 微信小程序：
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\mine\index.js`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\meeting\index.js`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\meeting\index.wxml`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\meeting\index.wxss`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\investment\index.js`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\investment\index.wxml`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\investment\index.wxss`
- 测试�?  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\tests\meeting-records-and-room-board.test.js`
  - `dehui-property-management/src/test/java/com/dehui/property/modules/investment/InvestmentControllerTest.java`

已完成内容：
1. 小程序“我的会议”入口改�?`/pages/meeting/index?mode=records`，会议页根据 `mode=records` 进入记录模式，只显示自己的会议室预约记录�?2. 会议预约页新�?`roomsQueried` 状态和“查询可用会议室”按钮；未主动查询前不展示会议室列表，日期或时间变化后自动清空已查询结果和已选会议室�?3. 会议预约查询后新增时间段状态看板，按用户选择的日期、开始时间、结束时间汇总空闲会议室和已预约会议室，并在会议室卡片列表前展示�?4. 会议预约提交后清空已选会议室和查询结果，避免用户误以为旧时段/旧会议室仍可继续提交�?5. 后端新增 `GET /api/mobile/investment/rooms`，返�?`building_room` �?`rent_status IN ('VACANT','AVAILABLE')` 的可租房源，包含楼宇、楼层、房号、面积、用途、状态和备注�?6. 小程序招商中心新增“可租房源看板”，在预约看房表单之前展示可租房源列表；预约看房记录仍保留在表单后方�?
验证结果�?- TDD 红灯：新增小程序测试后，“我的会议”仍跳普通会议预约页、会议页无记录模�?主动查询状态、招商页无房源看板，测试失败；新增后端测试后 `InvestmentController.mobileRooms()` 缺失导致编译失败�?- 小程序测试：`node --test tests/*.test.js` 通过�?2 个测试全部通过；`node --check pages/meeting/index.js` 通过�?- 后端定向测试：`mvn "-Dtest=InvestmentControllerTest" test` 通过�? 个测试全部通过�?- 后端已重启到 `8080`；实�?`GET http://localhost:8080/api/mobile/investment/rooms` 返回 200，当前本�?MySQL 返回 5 条可租房源�?
后续注意�?- `/api/mobile/investment/rooms` 当前只展示前 20 条可�?空闲房源；后续房源很多时可继续增加楼宇、面积、用途等筛选�?- 会议记录模式复用�?`/api/mobile/meetings` 返回�?`bookings` 数据；后续如果要进一步减少返回体，可以单独拆 `/api/mobile/meetings/bookings/mine`�?
### 2026-05-29 能耗表具管理与抄表真实数据入口

本次补齐“新增抄表”中表具下拉无配置入口的问题。根因是 WEB 端只有“抄表管�?能耗统计”，没有表具档案维护页，且后�?`/energy/meters`、`/energy/readings` 仍由 legacy 骨架处理，导致表具无法从 MySQL 正式数据源配置和读取�?
影响范围�?- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/energy/controller/EnergyController.java`
  - `dehui-property-management/src/main/java/com/dehui/property/modules/legacy/LegacyWriteController.java`
  - `dehui-property-management/src/main/resources/db/migration/V13__energy_meter_reading.sql`
- WEB 管理端：
  - `dehui-property-admin/src/views/energy/EnergyMeterList.vue`
  - `dehui-property-admin/src/views/energy/EnergyRecordList.vue`
  - `dehui-property-admin/src/api/energy.js`
  - `dehui-property-admin/src/router/index.js`
  - `dehui-property-admin/src/config/access.js`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/energy/EnergyControllerTest.java`
  - `dehui-property-management/src/test/java/com/dehui/property/schema/EnergySchemaMigrationTest.java`
  - `dehui-property-admin/tests/energyMeterManagement.test.mjs`

已完成内容：
1. 新增 `energy_meter` 表，作为水、电、燃气表具正式档案，包含表具编号、表具名称、类型、楼�?楼层/房间/租户关联、单位、倍率、状态、逻辑删除等字段�?2. 新增 `energy_reading` 表，作为抄表正式记录，包含表具、账期、抄表日期、上期读数、本次读数、本期用量、单价、结算金额、账单状态、异常标识等字段�?3. 后端新增真实 `/api/energy/meters` CRUD、`/api/energy/meters/{id}/last-reading`、`/api/energy/readings` CRUD、异常状态、生成账单、标记入账和能耗统计接口�?4. �?`LegacyWriteController` 移除能耗相�?501 骨架映射，避免真实能耗接口被旧模块拦截�?5. WEB 管理端新�?`/energy/meters` 表具管理页面，支持查询、分页、新增、编辑、删除和楼宇/楼层/房间/租户关联�?6. 能耗菜单新增“表具管理”，抄表页面标题区新增“表具管理”入口；新增抄表继续从真�?`/energy/meters` 读取表具�?7. Flyway `V13__energy_meter_reading.sql` 同步写入 `energy:meter` 菜单和能耗表具相关权限，并给管理员、运营、工程维修等默认角色补授权�?
验证结果�?- TDD 红灯：新�?WEB 测试后因 `EnergyMeterList.vue` 不存在失败；新增后端测试后因 `EnergyController` �?`V13` 迁移不存在失败�?- WEB 测试：`node --test tests/energyMeterManagement.test.mjs` 通过�?- 后端定向测试：`mvn "-Dtest=EnergySchemaMigrationTest,EnergyControllerTest" test` 通过�? 个测试全部通过�?- WEB 管理端构建：`npm run build` 通过�?- 后端已重启到 `8080`，Flyway 已执�?V13；API 实测 `POST /api/energy/meters`、`GET /api/energy/meters`、`GET /api/energy/meters/{id}/last-reading` 均返�?200�?- 后端全量 `mvn test` 当前�?1 个既有断言失败：`MobileEndpointCompatibilityTest.mineSummaryReturnsTenantDashboardShape` 期望 3 项、当前代码返�?2 项；该失败来自此前小程序“我的”页面入口精简后的旧测试断言，非本次能耗表具改动引入�?
后续注意�?- 表具已有抄表记录时，后端不允许直接删除表具，避免历史抄表数据断链�?- 能耗账单生成要求表具已关联租户；未关联租户时后端会拒绝生成账单�?- 后续如果要把能耗账单推到小程序账单中心，需要继续校�?`bill_type=ENERGY` 在移动端的中文展示和筛选�?
### 2026-05-28 公告首页轮播开关与小程序首页入口调�?
本次调整公告展示规则：后台公告管理新增“首页轮播”配置，只有已发布且勾选“首页轮播”的公告才进入微信小程序首页“园区公告”轮播；未勾选的公告仍保留在小程序“公告通知”列表中查看。小程序首页同时移除了“员工签到”和“我的身份”两个快捷入口，不删除对应页面和底部“我的”Tab�?
影响范围�?- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/notice/controller/NoticeController.java`
  - `dehui-property-management/src/main/resources/db/migration/V10__notice_home_carousel.sql`
- WEB 管理端：
  - `dehui-property-admin/src/views/operation/AnnouncementList.vue`
- 微信小程序：
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\index\index.js`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/notice/NoticeControllerTest.java`
  - `dehui-property-management/src/test/java/com/dehui/property/schema/NoticeHomeCarouselMigrationTest.java`
  - `dehui-property-admin/tests/announcementHomeCarousel.test.mjs`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\tests\home-carousel-announcements.test.js`

已完成内容：
1. `notice` 表新�?`show_on_home` 字段，默�?`0`，用于控制是否进入小程序首页园区公告轮播�?2. 后台 `/api/announcements` 返回 `showOnHome`，新�?编辑公告时保存该字段�?3. 小程序公告列表接�?`/api/mobile/announcements` 默认仍返回全部已发布公告�?4. 小程序首页改为请�?`/api/mobile/announcements?homeCarousel=true`，后端只返回 `show_on_home = 1` 的已发布公告�?5. WEB 公告管理列表增加“首页轮播”列，新�?编辑弹窗增加“首页轮播”开关�?6. 小程序首页九宫格移除“员工签到”和“我的身份”入口，保留公告、账单、能耗、会议、报修、访客、招商入口�?
验证结果�?- TDD 红灯：新增后端测试后，旧 `mobileNotices()` �?`homeCarousel` 参数编译失败；新增小程序测试后，首页仍请求全部公告导致测试失败；新增 WEB 测试后，页面没有“首页轮播”字段导致测试失败�?- 后端定向测试：`mvn "-Dtest=NoticeControllerTest,NoticeHomeCarouselMigrationTest" test` 通过�?- 后端全量测试：`mvn test` 通过�?4 个测试全部通过�?- WEB 管理端测试：`node --test tests/announcementHomeCarousel.test.mjs` 通过�?- WEB 管理端构建：`npm run build` 通过�?- 小程序测试与语法检查：`node --check pages/index/index.js`、`node --check pages/announcements/index.js`、`node --test tests/home-carousel-announcements.test.js` 通过�?- 后端已重启到 `8080`，实�?`/api/mobile/announcements?homeCarousel=true` 返回 200；当前本地数据中首页轮播返回 1 条，全部已发布公告返�?2 条�?- 浏览器验证：`/announcements` 页面已显示“首页轮播”列�?
### 2026-05-27 小程序发票下载无反馈修复

本次排查小程序账单中心点击“下载发票”无明显反应的问题。后端接口、数据库附件关系和租户鉴权经验证均正常，问题收敛在小程序下载和打开文件链路�?
影响范围�?- 小程序端�?  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\utils\request.js`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\bills\index.js`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\bills\index.wxml`
- 小程序测试：
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\tests\request-download.test.js`

已完成内容：
1. `api.download(url, filename)` 现在会携带租�?token，并把发票下载到 `wx.env.USER_DATA_PATH` 下带 `.pdf` 后缀的稳定本地路径�?2. 下载地址支持相对 `/mobile/...` 和完�?`http(s)://...` 地址，避免拼接错误�?3. 小程序账单页点击“下载发票”后立即显示“正在下载发票”，避免用户误以为没有响应�?4. 下载按钮下载中禁用，并使�?`catchtap` 防止事件冒泡干扰�?5. `wx.openDocument` 打开失败时改为明确弹窗提示，方便区分下载失败和当前开发者工具无法打开文档�?
验证结果�?- TDD 红灯：新�?`request-download.test.js` 后，�?`api.download()` 没有�?`filePath`，测试失败�?- 小程序下载层测试：`node --test tests/request-download.test.js` 通过�?- 小程序语法检查：`node --check utils/request.js`、`node --check pages/bills/index.js` 通过�?- 后端移动端发票下载接口复测：使用租户 `16650697751 / 123456` token 请求 `/api/mobile/bills/16/invoice/download` 返回 200，文件大�?60658 字节，`Content-Type` �?`application/pdf;charset=UTF-8`�?
### 2026-05-27 账单编辑入口与账期字段落�?
本次补齐账单管理“编辑”闭环，解决新增账单弹窗关闭后无法再进入修改的问题。账单编辑不是只加前端按钮，而是补齐后端更新接口、数据库字段和前端回填逻辑�?
影响范围�?- WEB 管理端：
  - `dehui-property-admin/src/views/tenant/BillList.vue`
- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/bill/controller/BillController.java`
- 数据库迁移：
  - `dehui-property-management/src/main/resources/db/migration/V9__bill_editable_fields.sql`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/bill/BillControllerEditTest.java`
  - `dehui-property-management/src/test/java/com/dehui/property/schema/BillEditableFieldsMigrationTest.java`

已完成内容：
1. 账单列表操作列增加“编辑”按钮，仅未收款�?`paid_amount = 0` 的账单可编辑�?2. 复用新增账单弹窗作为编辑弹窗，打开时回填账单编号、租户、合同、账单类型、标题、账期开始、账期结束、金额、到期日和备注�?3. 编辑保存调用 `PUT /api/bills/{id}`；保存后账单重新进入 `PENDING` 待审核状态，避免已发布账单被静默修改后直接展示给租户�?4. 已收款账单或已产生已收金额的账单，后端拒绝编辑并返回 HTTP 400，消息为 `已收款账单不能编辑`�?5. 新增 `bill.title`、`bill.period_start`、`bill.period_end` 字段，解决前端原有标题和账期字段没有正式落库的问题�?6. `/api/bills` �?`/api/mobile/bills` 返回标题与账期字段，管理端列表可正常显示账期�?
验证结果�?- TDD 红灯：新�?`BillControllerEditTest` 后，旧代码因没有 `updateBill(Long, Map)` 方法编译失败�?- 后端定向测试：`mvn "-Dtest=BillControllerEditTest,BillControllerDeletionTest,BillEditableFieldsMigrationTest" test` 通过�? 个测试全部通过�?- 后端全量测试：`mvn test` 通过�?0 个测试全部通过�?- WEB 管理端构建：`npm run build` 通过�?- 后端重启�?Flyway 成功�?version 8 迁移�?version 9�?- API 验证：创建测试账单后调用 `PUT /api/bills/{id}` 成功，返回列表中标题、账期、金额、待审核状态均已更新；尝试编辑已收款账单返�?HTTP 400；测试账单已逻辑删除清理�?- 浏览器验证：`/bills` 操作列显示“编辑”，点击后弹出“编辑账单”窗口，按钮为“保存修改并重新待审”�?
### 2026-05-27 账单逻辑删除与操作列排版

本次按“方案一”补�?WEB 端账单删除能力：未收款账单允许删除，已收款账单或 `paid_amount > 0` 的账单不允许删除。删除采用逻辑删除，不物理删除业务数据�?
影响范围�?- WEB 管理端：
  - `dehui-property-admin/src/views/tenant/BillList.vue`
- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/bill/controller/BillController.java`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/bill/BillControllerDeletionTest.java`

已完成内容：
1. 管理端账单列表增加“删除账单”按钮，仅对未收款且未产生已收金额的账单显示�?2. 删除前弹出确认框；删除成功后刷新当前分页列表�?3. 后端新增 `DELETE /api/bills/{id}`，先校验账单收款状态，再执行逻辑删除�?4. 账单删除时同步隐藏关�?`bill_invoice`、`bill_invoice_attachment`、`file_upload`、`bill_payment_record` 记录，避免小程序和管理端继续看到已删账单的发票或支付记录�?5. 已收款账单返�?HTTP 400，错误信息为 `已收款账单不能删除`�?6. WEB 账单操作列改为可换行按钮组，避免“确认收�?/ 替换发票 / 下载发票 / 删除发票”等按钮拥挤错位�?
验证结果�?- TDD 红灯：新�?`BillControllerDeletionTest` 后，旧代码因缺少 `deleteBill` 方法编译失败�?- 后端定向测试：`mvn -Dtest=BillControllerDeletionTest test` 通过�? 个测试全部通过�?- 后端全量测试：`mvn test` 通过�?7 个测试全部通过�?- WEB 管理端构建：`npm run build` 通过�?
### 2026-05-27 小程序租户绑定打通后�?MySQL

本次检查小程序“租户账号绑定”链路时确认：小程序请求配置已指�?`http://localhost:8080/api`，但后端 `/api/mobile/auth/wechat-login` �?`/api/mobile/auth/bind-tenant` 仍是 501 骨架接口，导致小程序无法真正进入数据库绑定逻辑�?
影响范围�?- 小程序端�?  - `dehui-property-miniprogram/config/env.js` 当前 dev 地址�?`http://localhost:8080/api`�?  - `dehui-property-miniprogram/pages/mine/tenant-bind/index.js` 绑定流程为：`wx.login()` -> `/mobile/auth/wechat-login` -> `/mobile/auth/bind-tenant`�?- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/mobile/controller/MobileAuthController.java`
  - `dehui-property-management/src/main/java/com/dehui/property/modules/tenant/controller/TenantController.java`
- 数据库迁移：
  - `dehui-property-management/src/main/resources/db/migration/V8__tenant_contact_password_hash.sql`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/mobile/MobileAuthControllerTest.java`
  - `dehui-property-management/src/test/java/com/dehui/property/schema/TenantContactFieldsMigrationTest.java`
  - `TenantControllerContactValidationTest` 已同步适配 `PasswordEncoder` 依赖�?
已完成内容：
1. `/api/mobile/auth/wechat-login` 不再返回 501；本地开发阶段使用微�?`code` 生成稳定 dev openid，写�?复用 `mobile_user` �?`wechat_identity`，并通过 Redis token 返回移动端登录态�?2. `/api/mobile/auth/bind-tenant` 不再返回 501；现在按手机号查�?`tenant_contact`，校验联系人和租户状态，再校验密码哈希�?3. 绑定成功后写�?`mobile_user_bind`，更�?`mobile_user.user_type = TENANT`，返回包�?`tenantId`、`tenantContactId`、`boundTenantName`、`contactName` 的小程序 profile；手机号�?`tenant_contact` 读取，不写入 `mobile_user.phone`，避免同一联系人反复绑定测试时触发唯一键冲突�?4. 新增 `tenant_contact.password_hash` 字段，并为历史联系人补默�?`123456` �?BCrypt 哈希�?5. 后台新增联系人和重置联系人密码时，都会写入新�?`password_hash`，避免只显示初始密码但数据库无法校验�?
验证结果�?- TDD 红灯：新�?`MobileAuthControllerTest` 后，旧代码因接口仍返�?`Void/501` 且构造参数不匹配编译失败�?- 后端定向测试：`mvn -Dtest=MobileAuthControllerTest,TenantContactFieldsMigrationTest,TenantControllerContactValidationTest test` 通过�? 个测试全部通过�?- 后端重启：Flyway 成功�?version 7 迁移�?version 8�?- API 验证�?  - `/api/mobile/auth/wechat-login` 返回 `PUBLIC` token�?  - 使用 `16650697751 / 123456` 调用 `/api/mobile/auth/bind-tenant` 返回 `TENANT` token�?  - 返回 profile 包含：`tenantId=8`、`tenantContactId=5`、`boundTenantName=德汇咖啡服务有限公司`、`contactName=Tech`�?  - 使用两个不同微信 dev code 重复绑定同一手机号，均返�?`TENANT` profile，无唯一�?500�?  - `/api/mobile/auth/me` 使用绑定后的 token 可返回同一租户 profile�?
### 2026-05-27 角色菜单与权限回显修�?
本次修复角色管理中内置角色打开“菜单”“权限”弹窗时未勾选已有权限的问题。根因不是前端弹窗显示字段缺失，而是部分内置角色�?`sys_role_menu`、`sys_role_permission` 关系表中没有补齐默认关系，导致接口返回空数组，弹窗只能显示未勾选�?
影响范围�?- 后端数据库迁移：
  - 新增 `dehui-property-management/src/main/resources/db/migration/V7__system_role_access_repair.sql`�?  - `ADMIN`、`SUPER_ADMIN`、`OPERATION_MANAGER` 默认补齐全部启用菜单和全部启用权限�?  - `PROPERTY_MANAGER`、`MANAGER`、`OPERATION`、`ASSET_MANAGER` 默认补齐非系统业务菜单和非系统业务权限�?  - `FINANCE`、`FINANCE_MANAGER` 默认补齐财务、账单、停车账单相关菜单和权限�?  - `ENGINEER`、`MAINTENANCE` 默认补齐工单、巡检、能耗相关菜单和权限�?- 后端测试�?  - 更新 `SystemAccessSeedMigrationTest`，增�?V7 迁移存在性和核心角色授权规则检查�?
验证结果�?- 先运�?`mvn -Dtest=SystemAccessSeedMigrationTest test` 看到 V7 不存在导致失败�?- 新增 V7 后同一测试通过�?- 重启后端�?Flyway 成功�?version 6 迁移�?version 7�?- API 验证�?  - `ADMIN`�?4 个菜单�?3 个权限�?  - `PROPERTY_MANAGER`�?1 个菜单�?7 个权限�?  - `FINANCE`�? 个菜单�?4 个权限�?  - `ENGINEER`�? 个菜单�? 个权限�?- 浏览器验证：
  - `/system/roles` 打开 `ADMIN` 权限弹窗�?3/83 已勾选�?  - 打开 `PROPERTY_MANAGER` 权限弹窗，业务权限已勾选，系统管理权限未勾选�?  - 打开 `PROPERTY_MANAGER` 菜单弹窗，业务菜单已勾选，系统管理菜单未勾选�?
### 2026-05-27 车位楼层/位置必填下拉

本次将车位管理新�?编辑表单中的“楼�?位置”从自由输入改为必填下拉选择，固定选项�?`B1`、`B2`、`地面`�?
影响范围�?- WEB 管理端：
  - `dehui-property-admin/src/views/parking/ParkingSpaceList.vue`
- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/parking/controller/ParkingController.java`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/parking/ParkingControllerTest.java`

已完成内容：
1. 前端新增/编辑车位时，“楼�?位置”必须选择，不能留空�?2. 下拉选项固定�?`B1`、`B2`、`地面`�?3. 编辑旧数据时，会尝试将包�?`B1`、`B2`、`地面` 的历史位置归一为对应选项；无法识别的历史值需要重新选择�?4. 后端保存车位时只接受 `floor` �?`location`，不再用 `area` 兜底为位置，避免跳过楼层/位置选择�?5. 后端只允�?`B1`、`B2`、`地面` 三个位置值；空值返�?`楼层/位置不能为空`�?
验证结果�?- TDD 红灯：`ParkingControllerTest` 初次运行时失败，证明旧逻辑允许未选楼�?位置�?- 后端测试：`mvn "-Dtest=ParkingControllerTest" test` 通过�?- 前端构建：`npm run build` 通过�?- 后端重启：`8080` 正常启动�?- API 验证：未�?`floor/location` 调用 `/api/parking/spaces` 返回 HTTP 400，响应消息为 `楼层/位置不能为空`�?- 浏览器验证：`/parking/spaces` 新增车位弹窗显示楼层/位置下拉框，选项包含 `B1`、`B2`、`地面`，无 500 提示�?
### 2026-05-27 巡检管理筛选分页与正式数据�?
本次将巡检管理�?`LegacyWriteController` 的空分页骨架中拆出，补为独立后端接口和正�?MySQL 表，并按工单管理交互补齐筛选与分页�?
影响范围�?- WEB 管理端：
  - `dehui-property-admin/src/views/operation/InspectionList.vue`
- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/inspection/controller/InspectionController.java`
  - `dehui-property-management/src/main/java/com/dehui/property/modules/legacy/LegacyWriteController.java`
- 数据库迁移：
  - `dehui-property-management/src/main/resources/db/migration/V6__inspection_record.sql`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/inspection/InspectionControllerTest.java`

已完成内容：
1. 巡检管理页面增加筛选条件：
   - 巡检�?   - 巡检对象/区域/地点
   - 巡检类型
   - 巡检结果
   - 巡检状�?   - 巡检日期范围
2. 巡检列表继续使用后端分页，保�?`page`、`pageSize`、`total`、`records` 结构�?3. 后端新增 `/api/inspections` 正式接口，支持筛选分页、新增巡检、关闭巡检�?4. 新增 `inspection_record` 表，字段覆盖巡检日期、巡检人、巡检类型、区域、对象、结果、问题描述、处理措施、巡检状态、备注、审计字段和逻辑删除字段�?5. �?`LegacyWriteController` 移除 `/inspections` �?`/inspections/{id}/close`，避免正式接口与旧骨架冲突�?
验证结果�?- TDD 红灯：`InspectionControllerTest` 初次运行�?`InspectionController` 不存在而编译失败�?- 后端测试：`mvn "-Dtest=InspectionControllerTest,BuildingControllerPaginationTest,WorkOrderControllerTest" test` 通过�? 个测试全部通过�?- 前端构建：`npm run build` 通过�?- 后端重启：`8080` 正常启动，Flyway 成功迁移�?version 6�?- API 验证：带巡检人、巡检对象、类型、结果、状态、日期范围的 `/api/inspections` 查询返回 200�?- 浏览器验证：`/inspections` 筛选栏和分页显示正常，�?500 提示�?
### 2026-05-27 工单筛选分页、租户入驻搜索、楼层房间兼容接�?
本次完成 WEB 端工单管理筛选分页优化，并修复合同台账、租户入驻页面因旧楼�?房间接口路径导致�?500 问题。后续继续相关功能时，应以以下文件和接口约定为准�?
影响范围�?- WEB 管理端：
  - `dehui-property-admin/src/views/operation/WorkOrderList.vue`
  - `dehui-property-admin/src/views/tenant/LeaseList.vue`
- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/workorder/controller/WorkOrderController.java`
  - `dehui-property-management/src/main/java/com/dehui/property/modules/building/controller/BuildingController.java`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/workorder/WorkOrderControllerTest.java`
  - `dehui-property-management/src/test/java/com/dehui/property/modules/building/BuildingControllerPaginationTest.java`

已完成内容：
1. 工单管理�?   - 增加关键词、问题类别、租户、处理人、状态、提交时间筛选�?   - 继续使用后端分页，接口支�?`page`、`pageSize`、`total`、`records`�?   - 后端 `/api/workorders` 支持筛选参数：`keyword`、`category`、`tenantId`、`tenantKeyword`、`handlerId`、`assigneeId`、`status`、`startTime`、`endTime`�?   - 工单列表补齐租户名、房间号、处理人、提交时间、阶段时间等前端展示字段�?2. 租户入驻�?   - 租户筛选从下拉框改为搜索输入框，避免租户数量增多后下拉列表过长�?   - 进入租户入驻页面默认展示全部租约数据；输入租户名称、租�?ID、合同编号或房间信息后再筛选�?3. 楼层/房间兼容接口�?   - 后端补回 `/api/buildings/{buildingId}/floors`�?   - 后端补回 `/api/buildings/{buildingId}/floors/{floorId}/rooms`�?   - 新接口仍复用正式楼层、房间数据，不引�?mock 数据�?
验证结果�?- 前端构建：`npm run build` 通过�?- 后端测试：`mvn "-Dtest=BuildingControllerPaginationTest,WorkOrderControllerTest" test` 通过�? 个测试全部通过�?- 浏览器验证：`/workorders` 筛选栏、租户关键词筛选和分页正常，无 500 提示�?- API 验证：`/api/workorders?page=1&pageSize=2`、`/api/workorders?status=SUBMITTED&tenantKeyword=云启`、`/api/buildings/1/floors` 均返�?200�?
### 2026-05-26 合同、租户、联系人联系方式校验

本次已完成合同管理、租户管理、租户联系人账号维护的联系电话和邮箱校验改造。后续新会话继续相关功能时，应以本段�?`docs/superpowers/plans/2026-05-26-contract-tenant-contact-validation.md` 为准�?
影响范围�?
- WEB 管理端：
  - `dehui-property-admin/src/views/tenant/ContractList.vue`
  - `dehui-property-admin/src/views/tenant/TenantList.vue`
  - `dehui-property-admin/src/utils/contactValidation.js`
- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/common/ContactValidators.java`
  - `dehui-property-management/src/main/java/com/dehui/property/modules/contract/controller/ContractController.java`
  - `dehui-property-management/src/main/java/com/dehui/property/modules/tenant/controller/TenantController.java`
- 数据库迁移：
  - `dehui-property-management/src/main/resources/db/migration/V5__tenant_contact_fields.sql`

已完成内容：

1. 合同管理�?   - 新增/编辑合同时校验联系电话、邮箱�?   - 增加合同编辑入口�?   - 增加“恢复”按钮，终止/作废后的合同可以恢复履约�?   - 合同列表展示历史异常联系方式时标记为“格式异常”�?2. 租户管理�?   - 租户新增/编辑时校验联系电话、邮箱�?   - 租户联系人账号维护中，手机号必填且必须符合格式�?   - 租户联系人账号维护中，邮箱可为空；填写时必须符合邮箱格式�?3. 后端统一校验�?   - `ContactValidators` 统一手机�?座机、邮箱校验规则�?   - 合同、租户、租户联系人接口均有后端兜底校验�?   - 错误电话或邮箱返�?HTTP 400，响应消息为 `联系电话格式不正确` �?`邮箱格式不正确`�?4. 数据库字段补齐：
   - `tenant.contact_person`：租户联系人�?   - `tenant.contact_email`：租户邮箱�?   - `tenant_contact.email`：租户联系人邮箱�?
历史数据原则�?
- 已存在的错误电话、错误邮箱不自动删除、不自动清空�?- 列表和详情页只做“格式异常”提示�?- 编辑保存时必须修正为合法格式，否则前后端都会拒绝保存�?
验证结果�?
- 后端全量测试：`mvn test`�?0 个测试全部通过�?- 前端构建：`npm run build` 通过�?- 后端本地启动正常：`http://localhost:8080/api`�?- 前端本地启动正常：`http://localhost:5173`�?- 浏览器已回到 `/contracts` 页面�?- 合同、租户、租户联系人接口在错误手机号/邮箱时返�?HTTP 400�?
## 2. 当前技术栈

后端�?

- 路径：`dehui-property-vscode/dehui-property-management`
- Java 21
- Spring Boot 3.2.5
- Spring Web
- Spring Data JPA
- JdbcTemplate
- Spring Validation
- Lombok
- Maven
- Flyway
- MySQL 8，当�?dev/prod 均使�?MySQL 作为正式业务数据�?- Redis，当�?dev/prod 均已接入，用于登录状态、验证码、权限缓存、统计缓存、限流和临时�?- 统一接口前缀：`/api`
- 本地端口：`8080`

数据库：

- 配置文件：`dehui-property-vscode/dehui-property-management/src/main/resources/application-dev.yml`
- 默认 JDBC URL：`jdbc:mysql://127.0.0.1:3306/dehui_property_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`
- 默认用户名：`dehui`
- 默认密码：`dehui_dev_password`
- 可通过环境变量 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 覆盖�?- JPA：`ddl-auto: none`
- Flyway：启用，迁移目录�?`src/main/resources/db/migration`�?- Redis 默认连接：`127.0.0.1:6379`，可通过 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` 覆盖�?
后台前端�?

- 路径：`dehui-property-vscode/dehui-property-admin`
- Vue 3
- Vite
- Element Plus
- axios
- vue-router
- pinia 已安装，但现有业务不依赖全局 store
- 本地端口：`5173`
- Vite 代理：`/api -> http://localhost:8080`
- 所有请求必须走 `src/utils/request.js`

微信小程序：

- 推荐工作目录：`dehui-property-vscode/dehui-property-miniprogram`
- 根目录另有一份独立小程序：`dehui-property-miniprogram`，内容结构相似，后续修改前需确认使用哪一份�?
- 小程序请求封装：`utils/request.js`
- 小程序接口配置：`config/env.js`
- 当前 activeEnv：`dev`
- dev baseURL：`http://localhost:8080/api`
- prod baseURL：`https://wuye.mingda.com.cn/api`

本地工具�?

- Workspace 根目录：`dehui_`
- 本地 Maven：`tools/apache-maven-3.9.9`
- 本地 JDK：`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`

部署/运行�?

- 后端本地运行：在 `dehui-property-vscode/dehui-property-management` 执行 `mvn spring-boot:run`
- 后台前端本地运行：在 `dehui-property-vscode/dehui-property-admin` 执行 `npm run dev`
- 后台访问：`http://localhost:5173`
- 后端访问：`http://localhost:8080/api`
- 生产小程序合法域名已�?`https://wuye.mingda.com.cn` 设计�?

## 3. 目录结构说明

根目录：

- `CONTEXT_SUMMARY.md`：长期交接文件，后续新会话优先读取�?
- `dehui-property.code-workspace`：VS Code 工作区配置�?
- `打开项目到VSCode.cmd`：打开项目辅助脚本�?
- `tools/apache-maven-3.9.9`：本�?Maven�?
- `dehui-property-vscode`：当前主要代码仓库�?
- `dehui-property-miniprogram`：根目录独立小程序副本，需�?`dehui-property-vscode/dehui-property-miniprogram` 区分�?

主仓库：

- `dehui-property-vscode/README.md`：项目总体说明，当前文件存在编码显示问题，但内容包含三端职责、部署路径、接口约定等�?
- `dehui-property-vscode/AGENTS.md`：开发守则，要求使用真实接口、不�?mock、不绕过 `request.js`、谨慎改后端和权限�?
- `dehui-property-vscode/MINIPROGRAM_PLAN.md`：小程序建设方向和阶段计划�?
- `dehui-property-vscode/dehui-property-management`：Spring Boot 后端�?
- `dehui-property-vscode/dehui-property-admin`：Vue 后台管理端�?
- `dehui-property-vscode/dehui-property-miniprogram`：推荐使用的微信小程序目录�?

后端关键目录�?

- `src/main/java/com/dehui/property/PropertyManagementApplication.java`：后端启动类�?
- `src/main/java/com/dehui/property/common`：统一返回、基础实体、健康检查、全局异常处理�?
- `src/main/java/com/dehui/property/config`：拦截器、Web 配置、初始化数据�?
- `src/main/java/com/dehui/property/modules`：业务模块目录�?- `src/main/resources/application.yml`：激�?`dev` profile�?- `src/main/resources/application-dev.yml`：本�?MySQL、Redis、Flyway、JPA、日志配置�?- `src/main/resources/application-prod.yml`：生�?MySQL、Redis、连接池和日志配置，敏感信息通过环境变量注入�?- `src/main/resources/db/migration`：Flyway SQL 迁移目录，当前包�?`V1__baseline_schema.sql` �?`V5__tenant_contact_fields.sql`�?- `pom.xml`：后�?Maven 配置�?
后端主要模块�?

- `building`：楼宇、楼层、房间�?
- `asset`：新增空间资产模型，当前处于部分实现状态�?
- `equipment`：旧设备台账，必须保留�?
- `tenant`：租户和租户联系人后台管理�?
- `mobile`：微信小程序移动端接口、微信用户、租户联系人绑定、账单、小程序工单/访客/会议/签到�?
- `contract`：合同及合同账单生成�?
- `bill`：财务账单、审核、收款、小程序可见账单数据来源�?
- `feerule`：收费规则和周期出账�?
- `parking`：车位和停车账单�?
- `energy`：能耗抄表和能耗账单�?
- `meeting`：会议室和会议预约�?
- `workorder`：工单�?
- `visitor`：访客�?
- `inspection`：巡检�?
- `announcement`：公告�?
- `investment`：招商内容和招商线索�?
- `system`：后台用户、角色、登录、权限�?
- `aiassistant`：AI 日报、问答、提醒、工单分析�?

后台前端关键目录�?

- `src/router/index.js`：后台路由�?
- `src/config/access.js`：菜单和前端角色访问控制�?
- `src/utils/request.js`：后台统一 axios 请求封装�?
- `src/layout/MainLayout.vue`：后台主布局�?
- `src/views/building`：楼宇、楼层、房间、旧设备台账页面�?
- `src/views/tenant`：租户、租约、合同、账单、收费规则页面�?
- `src/views/finance`：财务看板�?
- `src/views/parking`：车位和停车账单�?
- `src/views/meeting`：会议室和会议预约�?
- `src/views/operation`：工单、访客、巡检、公告�?
- `src/views/energy`：能耗抄表和统计�?
- `src/views/system`：用户、角色�?
- `src/views/ai`：AI 运营日报�?

微信小程序关键目录：

- `app.json`：页面注册、窗口、定位权限�?
- `app.js`：全局 token/userInfo/identity 初始化�?
- `config/env.js`：接口环境配置，可在小程序“我的”页动态保�?baseURL�?
- `utils/request.js`：统一 `wx.request` 封装，自动带 `Authorization`�?
- `utils/auth.js`：小程序 session 存储/清除�?
- `pages/home`：首页入口�?
- `pages/me`：登录、身份绑定、接口地址配置、后端联通检查�?
- `pages/bills`：租户账单中心�?
- `pages/announcements`：公告列表和详情�?
- `pages/meeting`：会议预约�?
- `pages/workorders`：工单�?
- `pages/visitors`：访客�?
- `pages/investment`：招商�?
- `pages/checkin`：员工签到�?

## 4. 已完成功�?

后台/权限�?

- 后台登录接口：`POST /api/system/login`
- 角色：`ADMIN`、`MANAGER`、`STAFF`、`SECURITY`、`CLEANER`、`FINANCE`
- 前端�?`localStorage.roles` 读取角色数组�?
- 后端 `AuthInterceptor` 做后台与小程序接口鉴权�?
- `/api/ping` 健康检查已放行�?

基础物业�?

- 楼宇、楼层、房间基础 CRUD 已有�?
- 当前旧接口仍是嵌套路由：
  - `/api/buildings`
  - `/api/buildings/{buildingId}/floors`
  - `/api/buildings/{buildingId}/floors/{floorId}/rooms`
- 后台有楼宇、楼层、房间页面�?

旧设备台账：

- 后端旧接口：`/api/equipments`
- 前端旧页面：`dehui-property-vscode/dehui-property-admin/src/views/building/EquipmentList.vue`
- 用户明确要求：不要删除、不要重命名、不要影响旧功能�?

租户联系人与小程序绑定：

- 已新增租户联系人机制�?
- `TenantContact` 已扩展密码、是否需要重置、最近绑定时间�?
- 后台租户页已支持联系人管理�?
- 合同创建时，如果填写 `contactPerson/contactPhone/contactEmail`，会同步租户联系人�?
- 小程序正式绑定逻辑为手机号 + 初始密码�?
- 保留开发模式按租户 ID 直绑�?

账单�?

- 账单支持创建、查询、审核通过、驳回、确认收款�?
- 账单创建时进�?`PENDING` 待审核�?
- 审核通过后小程序可见�?
- 已修复“账单编号留空保存失败”：
  - `BillCreateRequest.billNumber` 不再强制 `@NotBlank`
  - `BillService.normalizeBillNumber()` 自动生成 `BILL-yyyyMMdd-XXXXXXXX`
  - 全局 validation 异常改为较短中文提示�?

小程序：

- 支持开发态登录：`POST /api/mobile/auth/dev-login`
- 支持内部账号绑定：`POST /api/mobile/auth/bind-internal`
- 支持租户绑定：`POST /api/mobile/auth/bind-tenant`
- 支持查看当前身份：`GET /api/mobile/auth/me`
- 支持租户账单：`GET /api/mobile/bills`
- 支持公告、会议、工单、访客、招商、签到等移动端接口�?

初始化数据：

- `DataInitializer` 初始化角色、admin 用户、德汇创新中心、楼层、房间、默认能耗规则、开发联调数据�?
- 默认 admin�?
  - 用户名：`admin`
  - 密码：`123456`
  - 角色：`ADMIN`

## 5. 后端逻辑

统一约定�?

- 所有后台和移动端接口都挂在 `server.servlet.context-path=/api` 下�?
- 后端统一返回�?

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

## 13. 2026-05-26 合同与租户联系方式校�?
详细记录已合并到本文顶部“最近变更记录”，完整实施记录见：

```text
docs/superpowers/plans/2026-05-26-contract-tenant-contact-validation.md
```

核心结论�?
- 合同、租户、租户联系人新增/编辑均已接入电话和邮箱校验�?- 后端统一通过 `ContactValidators` 兜底校验，错误格式返�?HTTP 400�?- `V5__tenant_contact_fields.sql` 补齐 `tenant.contact_person`、`tenant.contact_email`、`tenant_contact.email`�?- 历史异常数据只提示，不自动删除�?- 验证结果：后端全量测�?40 个通过，前�?`npm run build` 通过�?
## 12. 2026-05-26 招商内容展示区块改�?
本次将“招商内容”从普通表格维护改为按微信小程序招商中心展示区块维护�?
后端�?
- `investment_display` 通过 `V4__investment_content_sections.sql` 增加�?  - `section_key`：小程序展示区块
  - `subtitle`：副标题
  - `image_url`：图片地址
- 旧数据保留，迁移脚本按标题关键词尽量映射�?`hero/highlight/policy/introduction/location/contact/notice`，无法识别的默认放入 `highlight`�?- 管理端接�?`/api/investment/contents` 返回分页数据，字段包�?`sectionKey/sectionName/title/subtitle/content/imageUrl/sortOrder/publishStatus/updatedAt`�?- 新增管理端预览接口：`GET /api/investment/contents/preview`，返回全部未删除内容并按展示区块分组�?- 新增生命周期接口�?  - `POST /api/investment/contents/{id}/publish`
  - `POST /api/investment/contents/{id}/disable`
  - `DELETE /api/investment/contents/{id}`
- 小程序展示接口支持：
  - `GET /api/mobile/investment/overview`
  - `GET /api/mobile/investment-content`
  - `GET /api/miniapp/investment-content`
- 小程序接口只返回 `publish_status = 'PUBLISHED'` 的内容，并按 `sectionKey` 分组返回�?  - `hero`
  - `highlight`
  - `policy`
  - `introduction`
  - `location`
  - `contact`
  - `notice`

WEB 管理端：

- `dehui-property-admin/src/views/investment/InvestmentContentList.vue` 已改为展示：
  - 序号
  - 展示区块
  - 标题
  - 副标�?  - 排序
  - 状�?  - 更新时间
  - 操作
- 不再直接展示数据�?ID�?- 表单字段改为：展示区块、标题、副标题、正文内容、图片地址、排序、状态、后台备注�?- 操作支持：编辑、预览、发布、停用、删除�?- 页面右侧增加小程序招商中心预览，管理员能直接看到各区块在小程序端的大致位置�?
小程序：

- `dehui-property-miniprogram/pages/investment` 不再使用写死招商亮点、政策、介绍内容�?- 页面�?`/mobile/investment/overview` 读取后端分组数据�?- 小程序端�?`hero/highlight/policy/introduction/location/contact/notice` 渲染，并�?`sortOrder` 排序�?- 预约看房表单保留原有提交流程�?
验证�?
- 后端新增测试�?  - `InvestmentControllerTest`
  - `InvestmentContentSectionsMigrationTest`
- 已通过针对性后端测试：

```powershell
cd dehui-property-management
& "C:\Users\Administrator\Desktop\dehui_\.tools\apache-maven-3.9.11\bin\mvn.cmd" "-Dtest=InvestmentControllerTest,InvestmentContentSectionsMigrationTest" test
```

- 已通过 WEB 管理端构建：

```powershell
cd dehui-property-admin
npm run build
```

## 11. 2026-05-20 分支同步说明

当前 GitHub 仓库统一为：

- `https://github.com/shao-xiao/Mini-Program`

本次处理的是远端两个不同历史分支�?

- `main`：保留为最终主分支，包含后端、后台管理端、小程序子目录和项目说明文档�?
- `dehui-property-miniprogram-standalone`：独立小程序仓库历史，根目录即小程序代码�?

同步策略�?

- 未执�?`merge`、`rebase` 或强行合并历史�?
- 仅把 `dehui-property-miniprogram-standalone` 中确认有效且 `main` 缺失的小程序开发辅助文件同步到 `main` �?`dehui-property-miniprogram/` 目录�?
- 保留 `main` 根目�?`README.md`、`AGENTS.md`、`CONTEXT_SUMMARY.md` 等项目说明文件，不使�?standalone 分支覆盖�?
- 同名小程序页面经过对比后，`main` 版本更完整，保留 `main`�?
  - `pages/bills`：`main` 保留账单格式化、状态映射、发票下载扩展和线下缴费说明�?
  - `pages/me`：`main` 保留联调面板开关、接口地址切换、健康检查和更保守的表单行为�?
  - `pages/workorders`、`pages/visitors`、`pages/meeting`：`main` 保留统一格式化工具、状�?类型文本映射和更完整的交互逻辑�?
  - `pages/energy`、`utils/format.js`：为 `main` 独有有效功能，未删除�?

实际同步目录/文件�?

- `dehui-property-miniprogram/.vscode/settings.json`
- `dehui-property-miniprogram/.vscode/tasks.json`

涉及模块�?

- 仅涉及小程序开发辅助配置�?
- 不涉及后�?Java 代码�?
- 不涉及后台管理端 Vue 代码�?
- 不涉及数据库实体、字段或初始化数据变更�?
- 不涉及生产部署配置变更�?

后续注意�?

- `dehui-property-miniprogram-standalone` 分支可以作为独立小程序历史参考，但后续正式开发应�?`main` 分支下的 `dehui-property-miniprogram/` 为准�?
- 如需删除 standalone 分支，应先确认不再需要独立小程序历史�?

鉴权�?

- 文件：`dehui-property-vscode/dehui-property-management/src/main/java/com/dehui/property/config/AuthInterceptor.java`
- 后台请求使用 `Authorization` header，前端也兼容发�?`token` header�?
- 移动�?token 形如 `MOBILE-uuid`，当前存放在 `MobileAuthService` 的内�?`ConcurrentHashMap`，服务重启后会失效�?
- 放行接口包括�?
  - `/api/ping`
  - `/api/system/login`
  - `/api/mobile/auth/dev-login`
  - `/api/mobile/dev/fixtures`
  - `/api/mobile/announcements` �?GET
  - `/api/mobile/investment`
  - `/api/uploads` �?GET

后台主要控制器：

- `HealthController`：`GET /api/ping`
- `SystemUserController`：登录、用户、角色、修改密码�?
- `BuildingController`：`/api/buildings`
- `FloorController`：`/api/buildings/{buildingId}/floors`
- `RoomController`：`/api/buildings/{buildingId}/floors/{floorId}/rooms`
- `EquipmentController`：`/api/equipments`
- `AssetController`：`/api/assets`，新增但尚未完成整体联调�?
- `TenantController`：`/api/tenant`，包含租户联系人接口�?
- `ContractController`：`/api/contracts`
- `BillController`：`/api/bills`
- `FeeRuleController`：`/api/feerules`
- `ParkingSpaceController`：`/api/parking/spaces`
- `ParkingBillController`：`/api/parking/bills`
- `EnergyController`：`/api/energy`
- `MeetingRoomController`：`/api/meetings/rooms`
- `MeetingBookingController`：`/api/meetings/bookings`
- `WorkOrderController`：`/api/workorders`
- `VisitorRecordController`：`/api/visitors`
- `InspectionController`：`/api/inspections`
- `AnnouncementController`：`/api/announcements`
- `InvestmentContentController`：`/api/investment/contents`
- `InvestmentLeadController`：`/api/investment/leads`
- `AIAssistantController`、`OperationQaController`、`ReminderController`、`WorkOrderAnalysisController`：`/api/ai/**`

移动端主要控制器�?

- `MobileAuthController`：`/api/mobile/auth`
- `MobileBillController`：`/api/mobile/bills`
- `MobileAnnouncementController`：`/api/mobile/announcements`
- `MobileMeetingController`：`/api/mobile/meetings`
- `MobileWorkOrderController`：`/api/mobile/workorders`
- `MobileVisitorController`：`/api/mobile/visitors`
- `MobileCheckinController`：`/api/mobile/checkins`
- `MobileInvestmentController`：`/api/mobile/investment`
- `MobileDevController`：`/api/mobile/dev/fixtures`

账单业务流程�?

1. 后台创建账单：`POST /api/bills`
2. 账单编号为空时由后端自动生成�?
3. 新账单默认：
   - `status=UNPAID`
   - `auditStatus=PENDING`
   - `sourceType=MANUAL`
4. 财务审核通过：`POST /api/bills/{id}/approve`
5. 审核通过后，小程序租户账单中心通过 `/api/mobile/bills` 可见�?
6. 后台确认收款：`POST /api/bills/{id}/pay`
7. 小程序当前未接微信支付，只显示线下缴费说明�?

租户联系人绑定流程：

1. 后台合同创建或租户联系人管理生成联系人�?
2. 联系人有手机号、初始密码、状态�?
3. 小程序先开发态登录拿移动�?token�?
4. 小程序用手机�?+ 初始密码调用 `/api/mobile/auth/bind-tenant`�?
5. 成功�?`wechat_user.boundTenantId` 绑定到租户�?
6. 小程序账单中心按 `boundTenantId` 查询已审核通过账单�?

## 6. 微信小程序对接逻辑

小程序推荐目录：

- `dehui-property-vscode/dehui-property-miniprogram`

请求地址�?

- 配置�?`config/env.js`
- `activeEnv='dev'`
- 默认 dev 地址：`http://localhost:8080/api`
- 可在“我的”页保存自定�?baseURL�?
- `utils/request.js` 每次请求通过 `getBaseURL()` 读取当前 baseURL�?

鉴权方式�?

- 小程序登录后�?token 存到 `wxStorage` �?`token`�?
- `utils/request.js` 自动�?token 放到 `Authorization` header�?
- 用户信息存储�?`userInfo`�?
- 身份存储�?`identity`，包括：
  - `PUBLIC`
  - `TENANT`
  - `INTERNAL`

小程序页面和接口�?

- `pages/me`
  - 检查后端：`GET /ping`
  - 获取开发联调数据：`GET /mobile/dev/fixtures`
  - 开发态登录：`POST /mobile/auth/dev-login`
  - 内部账号绑定：`POST /mobile/auth/bind-internal`
  - 租户绑定：`POST /mobile/auth/bind-tenant`
  - 当前用户：`GET /mobile/auth/me`
- `pages/bills`
  - 租户账单列表：`GET /mobile/bills`
  - 可按 `status=UNPAID/PAID/OVERDUE` 筛选�?
- `pages/announcements`
  - 公告列表：`GET /mobile/announcements`
  - 公告详情：`GET /mobile/announcements/{id}`
- `pages/meeting`
  - 会议�?预约：`GET /mobile/meetings`、`POST /mobile/meetings/bookings`
  - 取消预约兼容 POST/PATCH�?
- `pages/workorders`
  - 工单列表/创建/取消/评价/图片上传：`/mobile/workorders`
- `pages/visitors`
  - 访客预约/取消：`/mobile/visitors`
- `pages/investment`
  - 招商概览和线索：`/mobile/investment`
- `pages/checkin`
  - 员工签到：`/mobile/checkins`

账单提醒数据流：

1. 后台账单必须�?`tenantId`�?
2. 后台账单必须审核通过，即 `auditStatus=APPROVED` 或历史账单无审核字段时按已发布兼容�?
3. 小程序用户必须绑定为 `TENANT`，且 `boundTenantId` 匹配账单 `tenantId`�?
4. `/mobile/bills` 返回该租户账单、汇总金额、待�?逾期/已缴统计�?

## 7. 数据库与资产管理逻辑

当前数据库维护方式：

- MySQL �?dev/prod 的正式业务数据库�?- Flyway SQL 迁移是当前表结构维护方式，目录为 `dehui-property-management/src/main/resources/db/migration`�?- JPA `ddl-auto` �?`none`，不再依赖实体自动建�?补字段�?- Redis 只做登录状态、验证码、权限菜单缓存、首页统计缓存、接口限流和临时锁，不存正式业务数据�?- `BaseEntity` 统一字段�?  - `id`
  - `created_time`
  - `updated_time`
- 部分新空间资产相关实体另行添�?`deletedAt`，尚未完全在服务层打通�?

旧资�?设备�?

- 旧表：`equipment`
- 实体：`modules/equipment/entity/Equipment.java`
- 字段包括�?
  - `equipmentName`
  - `equipmentCode`
  - `equipmentType`
  - `location`
  - `status`
  - `manufacturer`
  - `model`
  - `installDate`
  - `remark`
- 旧接口：`/api/equipments`
- 旧前端页面：`src/views/building/EquipmentList.vue`
- 旧模块必须保留�?

新空间资产模型目标关系：

```text
Building -> Floor -> Room -> Asset -> AssetOperationLog
```

Building�?

- 表：`building`
- 实体：`modules/building/entity/Building.java`
- 当前已有字段�?
  - `buildingName`
  - `buildingCode`
  - `address`
  - `totalFloors`
  - `description`
  - `status`
  - `deletedAt`，已加实体字段但服务层尚未完全按软删除过滤�?

Floor�?

- 表：`floor`
- 实体：`modules/building/entity/Floor.java`
- 当前字段�?
  - `building`
  - `floorNumber`
  - `floorName`
  - `sortOrder`
  - `totalArea`
  - `description`
  - `status`
  - `deletedAt`
- 目标要求：楼层必须绑定楼宇；支持平铺查询 `/api/floors?building_id=1`；支持批量生�?B2、B1�?F �?9F�?

Room�?

- 表：`room`
- 实体：`modules/building/entity/Room.java`
- 当前字段�?
  - `building`
  - `floor`
  - `roomNumber`
  - `roomName`
  - `area`
  - `roomType`
  - `status`
  - `description`
  - `deletedAt`
- 目标要求：房间必须绑定楼宇和楼层；支持按 buildingId/floorId/status 查询；删除使用软删除�?

Room 状态：

- `AVAILABLE`：空�?
- `RENTED`：已出租
- `RESERVED`：预�?
- `RENOVATING`：装修中
- `DISABLED`：停�?

Room 类型�?

- `OFFICE`：办�?
- `WAREHOUSE`：仓�?
- `EQUIPMENT`：设备间
- `PARKING`：车�?
- `PUBLIC_AREA`：公�?

Asset�?

- 表：`asset`
- 实体：`modules/asset/entity/Asset.java`
- 当前已新增实体、Repository、DTO、Service、Controller，但尚未编译验证�?
- 字段包括�?
  - `assetCode`，唯一
  - `assetName`
  - `assetCategory`
  - `assetType`
  - `manufacturer`
  - `model`
  - `serialNo`
  - `building`
  - `floor`
  - `room`，可�?
  - `locationDesc`
  - `status`
  - `installDate`
  - `warrantyStartDate`
  - `warrantyEndDate`
  - `responsiblePerson`
  - `maintenanceCycleDays`
  - `lastMaintenanceDate`
  - `nextMaintenanceDate`
  - `remark`
  - `deletedAt`

Asset 状态：

- `IN_USE`：使用中
- `IDLE`：闲�?
- `MAINTENANCE`：维修中
- `DISABLED`：停�?
- `SCRAPPED`：报�?

AssetOperationLog�?

- 表：`asset_operation_log`
- 实体：`modules/asset/entity/AssetOperationLog.java`
- 字段包括�?
  - `asset`
  - `operationType`
  - `oldStatus`
  - `newStatus`
  - `oldLocation`
  - `newLocation`
  - `operator`
  - `operationTime`
  - `description`
  - `attachmentUrl`

操作类型�?

- `CREATE`：新增资�?
- `UPDATE`：修改信�?
- `STATUS_CHANGE`：状态变�?
- `TRANSFER`：位置转�?
- `MAINTENANCE`：维�?维保
- `INSPECTION`：巡检
- `SCRAP`：报�?

�?Asset 接口当前已写入代码：

- `GET /api/assets`
- `GET /api/assets/{id}`
- `POST /api/assets`
- `PUT /api/assets/{id}`
- `DELETE /api/assets/{id}`
- `PATCH /api/assets/{id}/status`
- `POST /api/assets/{id}/transfer`
- `POST /api/assets/{id}/maintenance`
- `GET /api/assets/{id}/logs`
- `GET /api/assets/statistics/overview`
- `GET /api/assets/statistics/by-type`
- `GET /api/assets/statistics/by-status`
- `GET /api/assets/statistics/by-floor`
- `GET /api/assets/statistics/warranty-expiring`
- `GET /api/assets/statistics/maintenance-due`

资产逻辑当前状态：

- `AssetService` 已实现基本校验：
  - 资产必须绑定楼宇和楼层�?
  - 楼层必须属于楼宇�?
  - 房间如果填写，必须属于对应楼�?楼层�?
  - 默认查询排除 `deletedAt != null`�?
  - 删除资产�?`deletedAt` 并把状态改�?`DISABLED`�?
  - 创建�?`CREATE` 日志�?
  - 修改�?`UPDATE` 日志�?
  - 状态变更写 `STATUS_CHANGE`；报废状态写 `SCRAP`�?
  - 转移�?`TRANSFER`�?
  - 维修�?`MAINTENANCE`�?
- 尚未完成�?
  - 后端编译验证�?
  - 楼宇/楼层/房间服务层软删除和过滤�?
  - 平铺 `/api/floors`、`/api/rooms` 接口�?
  - 前端新资产页面和核心联动�?

## 8. 当前问题与未完成事项

当前工作树状态：

- `dehui-property-vscode` 有未提交改动�?
- 已新�?`asset` 模块文件�?
- 已改动租户联系人、账单编号、楼�?楼层/房间实体�?DTO�?
- `CONTEXT_SUMMARY.md` 在仓库根目录 `dehui_` 下，不在 `dehui-property-vscode` 内�?

必须注意�?

- 不要回滚已有租户联系人和账单编号修复�?
- 不要删除或重命名�?`Equipment` 接口和页面�?
- 不要物理删除空间资产相关数据，目标是统一使用 `deletedAt` 软删除�?
- 不要绕过前端 `src/utils/request.js`�?
- 不要�?mock 数据�?

空间资产模型未完成：

- `BuildingService` 仍有物理删除逻辑�?
  - 删除楼宇时如果有楼层直接拒绝，否�?`delete`�?
  - 删除楼层时如果有房间直接拒绝，否�?`delete`�?
  - 删除房间直接 `delete`�?
- `BuildingService` 尚未把新增的 `Floor.sortOrder/description/status/deletedAt`、`Room.building/roomName/description/deletedAt` 全面映射到响应和保存逻辑�?
- `DataInitializer` 创建房间时当前只设置�?`floor`，新模型需要同时设�?`building`�?
- `FloorController` �?`RoomController` 当前只有嵌套路由，还没有目标平铺接口�?
- `AuthInterceptor` 目前只显式保�?`/api/buildings`，新平铺 `/api/floors`、`/api/rooms`、`/api/assets` 可能需要补权限逻辑�?
- `AssetController` 中统计接口写�?`/{id}` 后面，Spring 通常能按精确匹配处理，但后续可考虑�?`/statistics/**` 放到 `/{id}` 前面，降低歧义�?
- `AssetService.updateStatus()` 使用 `SCRAPPED` 判断报废，符合当前实体状态，但需确认前端也使�?`SCRAPPED`�?
- 后端尚未运行 `mvn -DskipTests compile` 验证�?

前端未完成：

- `FloorList.vue` 当前仍调�?`/buildings/{buildingId}/floors`，还未切�?`/floors?building_id=...`�?
- `RoomList.vue` 当前硬编�?`BUILDING_ID=1`，还未做楼宇 -> 楼层 -> 房间联动�?
- 尚未新增新的 `AssetList.vue` 或新资产路由�?
- 菜单 `src/config/access.js` 中资产管理只有楼宇、楼层、房间、设备台账，尚未加入新资产入口�?
- 路由 `src/router/index.js` 尚未加入 `/assets`�?
- 前端尚未运行 `npm run build` 验证新改动�?

小程序当前限制：

- 正式微信登录尚未接入，当前主要靠开发态登录�?
- 租户绑定已有后端正式手机�?+ 密码逻辑，但小程�?`pages/me/index.js` 当前数据结构仍包含开发直绑字段，需确认 WXML 是否已显示正式密码入口�?
- 移动�?token 在后端内存中，服务重启会失效�?
- 账单支付未接微信支付，仅线下缴费说明�?

缺失/不确定信息：

- 生产服务器的真实 MySQL、Redis 密码不应写入仓库；需通过服务器环境变量或本机私有配置提供�?- 未找到完整生产部署脚本，当前已有 `deploy/env/dehui-property.env.example` �?`deploy/nginx/dehui-property.conf` 可作为部署配置参考�?- 项目 README/AGENTS 曾存在旧数据库描述，已按当前 MySQL + Redis + Flyway 架构修正�?
## 9. 下一步开发计�?

优先�?P0�?

1. 先运行后端编译，拿到真实编译错误�?
2. 修复 `asset` 模块与现�?`building` 模块的编译问题�?
3. 更新 `BuildingService`、Repository、Controller�?
   - 楼宇/楼层/房间默认查询排除 `deletedAt != null`�?
   - 删除楼宇/楼层/房间改为软删除�?
   - 房间保存时设�?`building` �?`floor`�?
   - 楼层保存时校验楼宇存在且未软删除�?
   - 房间保存时校验楼层属于楼宇�?
4. 新增平铺接口�?
   - `GET /api/floors?building_id=1`
   - `POST /api/floors`
   - `PUT /api/floors/{id}`
   - `DELETE /api/floors/{id}`
   - `POST /api/floors/batch-generate`
   - `GET /api/rooms?building_id=1&floor_id=2&status=AVAILABLE`
   - `POST /api/rooms`
   - `PUT /api/rooms/{id}`
   - `DELETE /api/rooms/{id}`
5. �?`AuthInterceptor` �?`/api/floors`、`/api/rooms`、`/api/assets` 的访问控制�?

优先�?P1�?

1. 后台前端更新楼层页面：点击楼宇后调用平铺楼层接口�?
2. 后台前端更新房间页面：楼�?-> 楼层 -> 房间联动，移�?`BUILDING_ID=1` 硬编码�?
3. 新增最小可用资产页面：
   - 路由 `/assets`
   - 菜单“空间资产”或“资产台账�?
   - 列表查询 `/api/assets`
   - 新增资产表单使用楼宇 -> 楼层 -> 房间级联选择�?
4. 保持�?`/equipment` 页面可打开�?

优先�?P2�?

1. 补资产日志查看入口�?
2. 补资产统计页面或看板�?
3. 补资产巡检 `INSPECTION` 操作入口�?
4. 补小程序侧资产报�?巡检关联能力�?

验证命令�?

后端�?

```powershell
cd dehui-property-vscode/dehui-property-management
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;..\..\tools\apache-maven-3.9.9\bin;$env:Path"
mvn -q -DskipTests compile
```

前端�?

```powershell
cd dehui-property-vscode/dehui-property-admin
npm run build
```

后端重启�?

```powershell
netstat -ano | Select-String ':8080' | Select-String 'LISTENING'
Stop-Process -Id <PID> -Force

cd dehui-property-vscode/dehui-property-management
mvn spring-boot:run
```

## 10. 给下一�?Codex 会话的启动提�?

可复制给新会话：

```text
请先读取 dehui_/CONTEXT_SUMMARY.md，然后继续实现德汇创新中心物业管理平台的空间资产模型�?

要求�?
1. 不要改业务代码以外的无关文件，不要回滚已有租户联系人和账单编号修复�?
2. 保留�?Equipment 后端接口 `/api/equipments` 和前�?`/equipment` 页面，不删除、不重命名�?
3. 新增 Asset/AssetOperationLog 是新空间资产模型；当�?asset 模块部分代码已写入但尚未编译验证�?
4. 先运行后�?`mvn -q -DskipTests compile`，根据真实错误修复�?
5. 完成 Building/Floor/Room/Asset 的主外键关系、软删除、平�?`/api/floors` �?`/api/rooms` 接口�?
6. 所有空间资产相关删除统一使用 `deletedAt` 软删除�?
7. 新增、修改、状态变更、转移、维修、报废必须写 AssetOperationLog�?
8. 前端只做核心联动：楼层按楼宇加载、房间按楼宇和楼层加载、新资产页面按楼�?>楼层->房间选择�?
9. 完成后运行后端编译和前端 `npm run build`，并说明验证结果�?
```
## 2026-05-27 小程序移动端接口对接后端 MySQL 全链路检�?
本次对微信小程序实际调用�?`/api/mobile/**` 接口做了逐项盘点�?HTTP 验证。根因不是小程序直接连数据库，而是部分移动端接口仍返回管理端数组结构�?01 骨架或缺少移动端写入路径，导致小程序页面无法�?`profile/summary/records` 等结构读取数据�?
涉及小程序当前开发者工具目录：`C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram`。已修正 `utils/auth.js`、`pages/mine/tenant-bind/index.js`、`pages/mine/staff-bind/index.js`、`pages/me/index.js`，将旧的 `/mobile/auth/dev-login` 调整�?`wx.login()` -> `/mobile/auth/wechat-login`，再进入绑定接口�?
涉及后端文件：`MobileAuthController.java`、`MobileProfileController.java`、`MobileApiSupport.java`、`BillController.java`、`WorkOrderController.java`、`MeetingController.java`、`VisitorController.java`、`CheckinController.java`、`NoticeController.java`、`InvestmentController.java`。新�?`MobileEndpointCompatibilityTest` 覆盖移动端摘要、账单聚合、工单创建、会议预约、访客创建、签到和公告详情结构�?
已补齐或调整的移动端接口：`/mobile/me`、`/mobile/mine/summary`、`/mobile/bills`、`/mobile/workorders`、`/mobile/workorders/{id}/cancel`、`/mobile/workorders/{id}/evaluation`、`/mobile/workorders/{id}/images`、`/mobile/meetings`、`/mobile/meetings/bookings`、`/mobile/meetings/bookings/{id}/cancel`、`/mobile/visitors`、`/mobile/visitors/{id}/cancel`、`/mobile/checkins`、`/mobile/announcements/{id}`、`/mobile/auth/bind-internal`。`/mobile/investment/overview` 保留新版按区块分组结构，同时补充旧小程序页面仍使用的 `title`、`subtitle`、`address`、`highlights`、`policies` 字段�?
验证结果：`mvn -Dtest=MobileEndpointCompatibilityTest test` 通过�? 个测�?0 失败；`mvn test` 通过�?4 个测�?0 失败；后端已重启�?`8080`，Flyway 当前 schema version �?8。HTTP 实测中，租户 `16650697751 / 123456` 绑定成功并返�?`TENANT`；`/mobile/me`、`/mobile/mine/summary`、`/mobile/bills`、`/mobile/workorders`、`/mobile/meetings`、`/mobile/visitors`、`/mobile/announcements`、`/mobile/investment/overview` 均返�?200；`POST /mobile/workorders`、`POST /mobile/visitors`、`POST /mobile/meetings/bookings` 均返�?200 并写�?MySQL；`admin / 123456` 绑定内部员工成功并返�?`INTERNAL`，`GET/POST /mobile/checkins` 均返�?200�?
后续注意：小程序开发者工具模拟器可继续使�?`http://localhost:8080/api`；真机预览必须使用电脑局域网 IP。小程序端仍存在部分中文源码显示为乱码的历史文件，本次未整体重写编码，避免扩�?UI 文案改动范围�?## 2026-05-27 小程序账单发票下载入�?
本次补齐微信小程序“账单中�?我的账单”的发票下载链路，解决管理端可维护发票但小程序端无下载入口的问题�?
影响范围�?- 小程序端�?  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\utils\request.js`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\bills\index.js`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\bills\index.wxml`
  - `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram\pages\bills\index.wxss`
- 后端�?  - `dehui-property-management/src/main/java/com/dehui/property/modules/bill/controller/BillController.java`
  - `dehui-property-management/src/main/java/com/dehui/property/common/GlobalExceptionHandler.java`
- WEB 管理端：
  - `dehui-property-admin/src/views/tenant/BillList.vue`
- 测试�?  - `dehui-property-management/src/test/java/com/dehui/property/modules/mobile/MobileEndpointCompatibilityTest.java`

已完成内容：
1. 后端 `/api/mobile/bills` 每条账单返回 `hasInvoice`、`hasInvoiceFile`、`invoiceFileName`、`invoiceDownloadUrl`，小程序不直接读取数据库或文件表�?2. 新增移动端安全下载接口：`GET /api/mobile/bills/{id}/invoice/download`，必须带移动�?token，并校验账单属于当前绑定租户�?3. 管理端发票上传接�?`POST /api/bills/{id}/invoice` 支持 PDF 文件上传，文件保存到 `uploads/private/invoices`，记录写�?`file_upload` �?`bill_invoice_attachment`�?4. 管理端下载接�?`GET /api/bills/{id}/invoice/download` 可流式下载已上传发票�?5. 小程序账单卡片在 `hasInvoiceFile=true` 时显示“下载发票”，点击后使�?`wx.downloadFile` 携带 `Authorization` 下载，再�?`wx.openDocument` 打开�?6. 管理端“下载发票”按钮调整为只有存在实际发票文件时显示，避免旧开票记录没有附件时点击 404�?7. 全局异常处理补充后端错误日志，便于定位接�?500 的真实原因�?
验证结果�?- TDD 红灯：新�?`mobileDownloadInvoice` 测试后，旧后端因缺少方法编译失败�?- 后端定向测试：`mvn -Dtest=MobileEndpointCompatibilityTest test` 通过�? 个测试全部通过�?- 后端全量测试：`mvn test` 通过�?5 个测试全部通过�?- WEB 管理端构建：`npm run build` 通过�?- 小程�?JS 语法检查：`node --check utils/request.js`、`node --check pages/bills/index.js` 通过�?- 真实 8080 端到端验证：后台创建测试账单并上�?PDF 发票后，移动�?`/mobile/bills` 返回 `/mobile/bills/{id}/invoice/download`，使用租�?token 下载成功�?- 后端已重启，8080 当前运行本次新代码�?
注意事项�?- 发票文件属于财务敏感文件，当前实现通过后端鉴权流式下载，不�?`/files/public/**`�?- 旧的“已开票但无附件”历史记录不会在小程序显示下载按钮，需要管理端重新上传 PDF 后才可下载�?## 2026-06-26 能耗抄表模板下载修�?
本次修复管理端【能耗管�?-> 抄表管理】“导出模板”无明确下载反馈的问题。前端不再生成“下载已生成模板”二次链接，也不再提供“复制模板内容”兜底按钮；页面只保留一个“导出模板”入口�?
后端新增/完善 `GET /api/energy/readings/template`，按当前筛选条件导�?CSV 模板附件，字段包含：表号、表具名称、能耗类型、账期、抄表日期、上期读数、本次读数、单价、房间、租户、备注。接口使用正常后台登录鉴权，请求仍通过 `Authorization` 头传 token，不�?token 放到 URL�?
前端 `dehui-property-admin/src/api/energy.js` 新增 `downloadEnergyReadingTemplate`，使用统一 `request.js`，`responseType: 'blob'`。`EnergyRecordList.vue` 的“导出模板”调用后端真实模板接口，然后使用现有 `downloadBlob` 工具下载；支�?`showSaveFilePicker` 的浏览器会优先弹出保存文件窗口，用户可以直接选择保存位置，不支持时回退为普通浏览器下载，并提示“模板下载已开始”�?
验证结果�?
- `node --test tests/energyReadingBatchWorkflow.test.mjs` 通过�?- `mvn -Dtest=EnergyControllerTest#readingTemplateDownloadReturnsCsvAttachment test` 通过�?- `mvn -q -DskipTests compile` 通过�?- `npm run build` 通过�?- 使用 `admin / 123456` 登录后，�?`Authorization` 头请�?`GET http://localhost:8080/api/energy/readings/template?periodMonth=2026-06&billStatus=NOT_GENERATED` 返回 `200`，`Content-Type: text/csv;charset=UTF-8`，`Content-Disposition: attachment`，CSV 内容包含模板表头和实际抄表任务行�?## 2026-06-27 本地开发库批量写入 500+ 条模块测试数�?
本次按用户要求为后台各业务模块写入不少于 500 条可检索、可分页的本地开发测试数据。仅新增数据，不清空、不重置、不删除历史数据；角色、权限、菜单等基础权限配置表未批量�?500 条，避免破坏 RBAC 权限体系�?
新增开发脚本：`scripts/DevSeed500Modules.java`。脚本使用后�?Maven 运行时中�?MySQL JDBC 驱动连接本地 dev 数据库，默认连接 `jdbc:mysql://127.0.0.1:3306/dehui_property_dev`，用�?`dehui`，密�?`dehui_dev_password`；也支持通过 `DEHUI_DB_URL`、`DEHUI_DB_USER`、`DEHUI_DB_PASSWORD` 覆盖。数据统一使用 `SEED500-20260627` 前缀，方便后续识别来源�?
执行方式�?
```powershell
cd C:\Users\Administrator\.config\superpowers\worktrees\dehui-property-vscode\Back\prod-architecture-20260522
cd dehui-property-management
mvn -q dependency:build-classpath -DincludeScope=runtime "-Dmdep.outputFile=target/runtime-classpath.txt"
cd ..
$cp = Get-Content dehui-property-management\target\runtime-classpath.txt
javac -encoding UTF-8 -cp $cp scripts\DevSeed500Modules.java
$runCp = (Get-Content dehui-property-management\target\runtime-classpath.txt) + ';scripts'
java -cp $runCp DevSeed500Modules
```

本次执行后活跃数据量�?
```text
sys_user                     502
mobile_user                  520
building                     503
building_floor               524
building_room                508
tenant                       503
tenant_contact               505
contract                     503
bill                         506
bill_payment_record          502
bill_invoice                 503
parking_space                504
parking_assignment           502
parking_bill                 502
meeting_room                 501
meeting_booking              503
meeting_bill                 501
energy_meter                 801
energy_reading               703
work_order                   506
work_order_record            1012
inspection_record            500
visitor_appointment          506
visitor_pass_record          1001
investment_display           503
investment_lead              503
investment_follow_record     1001
notice                       500
staff_checkin                504
equipment                    502
```

后续注意：这些是本地开发测试数据，主要用于后台页面分页、筛选、搜索、导入导出和 BI 汇总压力验证；不要作为生产初始化数据迁移，也不要写�?Flyway。脚本已修正为重复运行时不再继续追加无业务编号的工单处理记录、访客通行记录、招商跟进记录�?## 2026-06-27 后台列表页摘要卡片统一接入

本次按用户反馈，将能耗抄表页类似的“先给视觉落点”的摘要能力同步到后台其他适合的列�?台账模块。新增公共组�?`dehui-property-admin/src/components/ListSummaryGrid.vue`，统一摘要卡片的布局、颜色、间距和响应式规则，避免每个页面单独复制样式�?
已接入页面包括：楼宇管理、楼层管理、房间管理、设备台账、资产台账、租户管理、租户入驻、合同台账、收费规则、账单管理、车位管理、停车账单、会议室管理、会议预约、工单管理、巡检管理、访客管理、公告管理、招商内容、招商线索、用户管理、角色管理、表具管理。能耗抄表页已有专属统计摘要，本次未重复接入�?
摘要卡片原则：不新增虚构接口，不使用 mock 数据；优先使用当前页面已有列表数据、分页总数和当前页状态计算。涉及金额、容量、待处理、启�?停用、异�?完成等指标时，只做页面级辅助定位，不替代 BI Dashboard 的全量分析�?
验证结果：`cd dehui-property-admin && npm run build` 通过�?
## 2026-06-27 后台多模块空白页修复

本次定位后台多模块进入后出现空白�?路由视图异常的问题。根因是 `dehui-property-admin/src/views/system/UserList.vue` 在接入列表摘要卡片时引用了未定义�?`overallTotal`，并漏导�?`buildCountSummaryCards`；进入用户管理页后会触发运行时异常，导致 Vue Router 视图更新中断，后续切换模块也容易表现为空白�?
修复内容�?
- `UserList.vue` 补充 `buildCountSummaryCards` 导入�?- 用户管理摘要卡片的总数改为使用页面已有�?`total.value`，不再引用不存在�?`overallTotal`�?- 保持所有接口仍走统一 `request.js`，未新增 mock 或虚构接口�?
验证结果�?
- `cd dehui-property-admin && npm run build` 通过�?- 重启前端 Vite 服务后，浏览器检�?`/system/users`、`/system/roles`、`/buildings`、`/tenants`、`/bills`、`/energy/records`、`/workorders`、`/finance/dashboard` 均能正常渲染主内容，不再为空白�?- 静态扫描确认其余使�?`buildCountSummaryCards` 的页面没有漏导入或漏定义 `overallTotal` 的同类问题�?
## 2026-06-28 后台 RBAC 权限、空白页、租户删除与小程序内部账号修�?
本次按用户提出的 4 项需求处理：

1. 修复后台空白页：`/investment/leads`、`/contracts`、`/parking/bills` 的根因是列表摘要卡片中调用了未定义的 `formatNumber()` �?`money()`，运行时异常导致页面空白。已在招商线索补�?`formatNumber`，合同台账改用已�?`formatMoney`，停车账单改用已�?`formatMoney`�?2. 租户管理新增删除能力：前�?`TenantList.vue` 增加“删除”按钮；后端 `TenantController` 新增 `DELETE /api/tenants/{id}` �?`DELETE /api/tenant/{id}`，使用软删除 `deleted = 1`，不物理删除业务历史数据�?3. 小程序内部账号可用性：`pages/index/index.js` 将会议预约、报修服务、访客预约从 tenant-only 改为 tenant/staff 绑定后均可进入；账单中心、能耗查询仍保留租户限定，因为它们依赖租户账单归属�?4. 后台 RBAC 完善�?   - `LoginResponse` 增加 `menus`、`userInfo`，保留旧�?`token/userId/username/realName/roles/permissions` 字段�?   - `/api/system/login` �?`/api/auth/me` 现在返回用户信息、角色、权限和树形菜单�?   - 前端登录页保�?`userInfo/roles/permissions/menus` �?localStorage�?   - 前端路由增加 `/403`，无权限直输路由时跳�?403，不再回落到驾驶舱�?   - 后端 `AuthInterceptor` 增加接口级权限校验，�?ADMIN/SUPER_ADMIN 访问受控后台 API 时按路径�?HTTP 方法匹配权限码，无权限返�?403�?   - 新增 Flyway 迁移 `V17__rbac_access_completion.sql`，只补充 RBAC 元数据：TENANT/STAFF/SECURITY 角色、缺失权限、内置角色菜单和权限关系；ADMIN/SUPER_ADMIN 自动拥有所有启用菜单和权限�?
权限控制位置�?- 前端菜单来源：`dehui-property-admin/src/config/access.js` 读取登录返回�?`menus`，并�?`MainLayout.vue` 渲染左侧菜单�?- 前端路由拦截：`dehui-property-admin/src/router/index.js` �?`beforeEach`，无权限�?`/403`�?- 后端 API 校验：`dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`�?- 角色权限配置：`dehui-property-admin/src/views/system/RoleList.vue` 继续使用现有“菜单”和“权限”配置入口，对应后端 `/system/roles/{id}/menus` �?`/system/roles/{id}/permissions`�?
初始化方式：
- 正常启动后端时由 Flyway 自动执行 `V17__rbac_access_completion.sql`�?- 不需要清空数据库，不覆盖现有用户和业务数据�?- admin 重新登录后会拿到全部菜单、按钮和接口权限，避免管理员被锁死�?
验证结果�?- `cd dehui-property-management && mvn -q -DskipTests compile` 通过�?- `cd dehui-property-admin && npm run build` 通过�?

## 2026-06-28 С�����ڲ�Ա������������·�޸�

�����޸���̨�ɵ����ڲ�Ա����С������ղ���ָ�ɹ��������⡣������ `GET /api/mobile/workorders` ԭ��ǿ��Ҫ���⻧��� `requireTenantId`��ֻ�� `tenant_id` ��ѯ�⻧�ύ�ı��޼�¼���ڲ�Ա����Ȼ���԰󶨺�̨�˺ţ������ᰴ�󶨵� `sys_user_id` ��ѯ `work_order.assignee_id`����˺�̨�ɸ�Ա���Ĺ����޷���С���򹤵�ҳ�͡��ҵġ�ժҪ�г��֡�

�޸����ݣ�
- `WorkOrderController.mobileWorkorders` ���ƶ�����ݷ������⻧������ `tenant_id` ��ѯ�Լ��ı��ޣ��ڲ�Ա��ͨ�� `MobileApiSupport.requireStaffUserId` ��ȡ�󶨵ĺ�̨�û� ID������ `work_order.assignee_id` ��ѯ�ɸ��Լ��Ĺ�����
- �ƶ��˹������ز��� `handlerName`��`submittedTime`��`assignedTime`��`processingTime`��`completedTime`��`closedTime`��`cancelledTime`����С����ʱ����չʾ��
- �����ƶ���Ա������ӿڣ�`POST /api/mobile/workorders/{id}/start` �� `POST /api/mobile/workorders/{id}/complete`��ֻ�������ָ�ɸ���ǰ�󶨺�̨�˺ŵĹ�����
- `MobileProfileController.summary` ���ڲ�Ա���� `assignee_id` ͳ��δ���ָ�ɹ���������������ʾ������⻧��
- С���� `pages/workorders/index.js/wxml` �����ڲ�Ա��ģʽ��Ա�������⻧�����ύ������б���ʾ����ʼ����/��ɹ�����������

��֤�����
- `cd dehui-property-management && mvn -q -DskipTests compile` ͨ����
- `node --check dehui-property-miniprogram/pages/workorders/index.js` ͨ����
- ��� 8080 ����������е�ǰ���롣
- ʹ�ÿ���С�����¼ code `codex-test-staff-workorder` �󶨺�̨�˺� `test / 123456` ������ `GET /api/mobile/workorders` ���� `userType=INTERNAL`��`boundSysUserId=3`�����ܲ鵽�ɸ� `test` �Ĺ��� `WO-9FB6875ABE7A`��`GET /api/mobile/mine/summary` ���� `workOrderCount=1`��`needTenantBind=false`��

## 2026-06-28 ��̨ģ�鿴��ҵ�������Ż�

���ΰ��û������Ż���̨�б�ҳ�Ϸ���ģ�鿴�壺���岻��Ĭ��չʾ��ȫ������ / ɸѡ��� / ��ǰҳ������ͨ�÷�ҳ��Ϣ�������Ա�ģ���ҵ���ж�Ϊ���������ģ������ + ɸѡ��� + ҵ��ָ�ꡱ�Ľṹ��

ǰ�˹���������
- ���� `dehui-property-admin/src/utils/businessSummary.js`���ṩ��ҵ�񿴰忨Ƭ����������
- ���� `dehui-property-admin/src/utils/listSummary.test.js`���� Node ���� test �̶�ҵ�񿴰岻�����ɡ���ǰҳ������
- `dehui-property-admin/src/utils/listSummary.js` �� `buildCountSummaryCards` ��Ϊ�����{ģ��}������ɸѡ�����ҵ��ָ�ꡱ�������� `loadListTotals` ������ȡ��ʵ�б�������

�ص�ҳ�������
- �豸̨�ˣ������Ϊ�豸������ɸѡ����������豸��ά���С�����/ͣ�ã�״̬����ͨ�� `/equipments` ��ʵ��ҳ����ͳ�ơ�
- �����������尴��ǰ¥��/¥����ʾ�ɳ��⡢�ѳ��⡢Ԥ��/�����ã�ͳ������ `/rooms` ��״̬������
- ��߹���������Ϊ����ˮ���ȼ�����ͣ�ñ�ߣ�ͳ������ `/energy/meters`��
- ��λ����������Ϊ���ó�λ����ռ�á�Ԥ��/ͣ�ã�ͳ������ `/parking/spaces`��
- ��������������Ϊ���ɵ������ɵ��������С������/�رգ�ͳ������ `/workorders` ��״̬�����������õ�ǰҳ��������ȼ���
- Ѳ�����������Ϊ�쳣Ѳ�졢δ�رա��ѹرգ�ͳ������ `/inspections` �Ľ��/״̬������
- �˵�����������Ϊ���տ�����˵������տӦ�պϼƣ�������������������˻��ܽӿڡ�
- ���������������Ϊ�������������С���ת������������״̬ɸѡ֧�֡�
- �ÿ͡����桢����ԤԼ����ͬ��ͣ���˵����շѹ����ҳ��ͬ���������ǰҳ�����İ���

��˽ӿڵ�����
- ���� `GET /api/bills/summary`����ѯ������ `/api/bills` һ�£����� `totalAmount`��`paidAmount`��`unpaidAmount`��`paidCount`��`unpaidCount`��`overdueCount`��`overdueAmount`��
- `/api/energy/stats` ��Ϊ SQL �ۺ�ͳ�ƣ�����ֻȡǰ 500 ����¼���ڴ���ܣ����� 700+ �����¼������������������
- `/api/investment/leads` ���� `status` ��ѯ������֧����������״̬ͳ�ơ�

��֤�����
- `node --test dehui-property-admin/src/utils/listSummary.test.js` ͨ����
- `cd dehui-property-admin && npm run build` ͨ����
- `cd dehui-property-management && mvn -q -DskipTests compile` ͨ����

## 2026-06-28 ��̨�б�����Ӿ���ʽ�Ż�

���ΰ��û�����Լ����ҳ�������Ż�ͨ���б���� `ListSummaryGrid.vue` ���Ӿ����֣��Ӵ����ǳɫ�׸�Ϊ�׵���߿� + ���״̬ɫ����ǿ��ָ�����ֲ㼶���������� hover ��������ʹ�� `auto-fit/minmax` �ò�ͬģ��Ŀ�Ƭ�����Զ������ȡ���Լ����ҳͬ�����������İ���˳��չʾ����Լ������ɸѡ����������С�����פ��ͬ�������⡱�����û��ȿ�����Ч��Լ�ʹ��������

Ӱ�췶Χ��
- `dehui-property-admin/src/components/ListSummaryGrid.vue`
- `dehui-property-admin/src/views/tenant/LeaseList.vue`

��֤�����
- `cd dehui-property-admin && npm run build` ͨ����

## 2026-06-28 ��̨���� UI �����ʸ��Ż�

���ΰ��û�Ҫ���ڱ��ֵ»��̨���к�ɫ��ɫ����ɫ������ Element Plus ҵ���̨����ǰ���£����ͷ���ȫ�� UI �Ż���δ�޸Ľӿڡ�·�ɡ�Ȩ�޺�ҵ���߼���

ǰ�˵�����
- `dehui-property-admin/src/styles/dhic-theme.css`������ DHIC ���������ͳһҳ�汳�������塢ѡ���ı�����ť������������򡢱����ࡢ��Ƭ����񡢷�ҳ�������������˵��Ϳ�״̬���Ӿ���ʽ��
- `dehui-property-admin/src/layout/MainLayout.vue`��΢�������ֱ�����������������Ʒ������logo �ߴ硢�����˵���Բ��/���/active ״̬��ʹ������ɾ�������ԭ����ʶ��
- ���ð�ť���ٳ��� hover ��Ӱ������ֻ��/���ɲ���״̬���û���

��֤�����
- `cd dehui-property-admin && npm run build` ͨ����
- `http://localhost:5173/meetings/rooms` ���� 200��ǰ�˿�������������Ӧ��

## 2026-06-28 �豸̨�˶�ά����Ե�

���ΰ��û�ϣ����ȫ��ͳ�Ʊ��������ά���£���ķ����������豸̨������Ե㣬������ɸ��õı����ͼ����������ͼƫ�ù��ߣ�����ÿ��ģ���ظ�ʵ�֡�

ǰ��������
- `dehui-property-admin/src/components/DhicDataToolbar.vue`���ṩ��������¼���ֶ����á���ͼ���á�ɸѡ�����顢�����иߡ������ά��񹤾���������
- `dehui-property-admin/src/utils/tableViewPrefs.js`���ṩ�����ͼƫ�ù�һ��������ͷ��鴿������
- `dehui-property-admin/src/utils/tableViewPrefs.test.js`�������ֶ�ƫ����ϴ��Ĭ��ֵ���ˡ�����ͷ����߼���

�豸̨�˵�����
- `dehui-property-admin/src/views/building/EquipmentList.vue` �����ά��񹤾�����
- ֧�ְ��ֶ���ʾ/�����У������ֶβ������ء�
- ֧���豸���͡�λ�á�״̬�����ҵ��ֶη��顣
- ֧�ְ��豸���ơ���š����͡�λ�á�״̬�����ҵ��ֶ�����/��������
- ֧�ֽ�ա���׼����չ�����иߡ�
- ֧�ֲ鿴��ǰɸѡ�����������ͼƫ�ñ��浽 `localStorage` �� `dhic-equipment-table-view-v1`��
- �������豸���ƶ�����񹤾����У����ֶΡ�ɸѡ����ͼ����������ͬһ����������

��֤�����
- `node --test dehui-property-admin/src/utils/listSummary.test.js dehui-property-admin/src/utils/tableViewPrefs.test.js` ͨ����
- `cd dehui-property-admin && npm run build` ͨ����
- `http://localhost:5173/equipment` ���� 200��

����˵����
- ����δ�ĺ�˽ӿں����ݿ�ṹ��
- ���ǵ�һ�׶��Ե㣬����һ�����滻����ģ�飻�����ɰ�ͬһ `DhicDataToolbar` �����Ǩ�Ʒ��䡢�⻧����ͬ���˵���������ͣ�������顢�ܺĵ��б�ҳ��

## 2026-06-28 ��ά������������̿�

�������豸̨���Ե�����ϼ������󲢽�������̨�б�ҳ��

����������
- ���� `dehui-property-admin/src/composables/useDhicTableView.js`��ͳһ��������ͼƫ�á��ֶ���ʾ�����򡢷��顢ɸѡ��ǩ���иߡ������кϲ��ͱ��ر��档
- `dehui-property-admin/src/styles/dhic-theme.css` ���� `.dhic-multitable` ȫ����ʽ��ͳһ��ά����иߡ������С�����������ͼ��ʾ��ʽ��

�Ѽ�������ҳ�棺
- `dehui-property-admin/src/views/parking/ParkingSpaceList.vue`����λ����֧���ֶ����á�״̬���顢�����иߺ͵�ǰɸѡ�鿴����������λ�������ά��������
- `dehui-property-admin/src/views/meeting/MeetingRoomList.vue`�������ҹ���֧���ֶ����á�״̬���顢�����иߺ͵�ǰɸѡ�鿴�������������ҡ������ά��������
- `dehui-property-admin/src/views/tenant/TenantList.vue`���⻧����֧���ֶ����á�״̬���顢�����иߣ��������⻧�������ά��������

��֤�����
- `cd dehui-property-admin && npm run build` ͨ����

����˵����
- ������δ�޸ĺ�˽ӿں����ݿ�ṹ��
- ��ǰ���γ�ͨ�ý���ģʽ�������ɼ���Ǩ�ƺ�̨ͬ�ˡ��˵���������������ͣ���˵�������ԤԼ���ܺı�ߡ�����������ͨ�б�ҳ��

## 2026-06-28 ��ά��񹤾���������ǿ

���ΰ��û��Գ�λ����ҳ�������ķ��������Ż� `DhicDataToolbar`��������ӽ������ά���

ǰ�˵�����
- ɾ���������еġ���ͼ���á���ڣ������ظ��͸��š�
- �ֶ����ø�Ϊ�ֶ��б� + ���� + �����ֶΡ�
- �����ֶ�֧�ֳ����ֶ����ͣ��ı������֡���ѡ����ѡ�����ڡ���ѡ�򡢵绰���롢Email�������ӡ�
- ɸѡ��Ϊ�����������ģʽ��֧���ֶΡ�����������ֵ��ϣ�����֧�ְ��������ڡ������ڡ�Ϊ�ա���Ϊ�ա�
- �����Ϊ�����������ã�֧����ӷ����ֶκ�����/����
- �����Ϊ�����������ã�֧����Ӷ�������ֶκ�����/����
- �и߸�Ϊ�����˵����ṩ�͡��еȡ��ߡ������ĵ������ӽ������ά��񽻻���

�ײ�������
- `tableViewPrefs.js` �����Զ����ֶΡ�ɸѡ����������顢������Ĺ�һ����ִ���߼���
- `useDhicTableView.js` ���� `customColumns`��`filters`��`groups`��`sorts` ��ͼƫ�ã�����ǰ���б�����ʵִ��ɸѡ������ͷ��顣
- ��λ����ҳ�ѽ������������������������Զ����ֶο���ʾΪ�У�ɸѡ/����/�����Ӱ�쵱ǰҳ���ݡ�

��Ҫ���ƣ�
- ��ǰ�Զ����ֶ���ǰ�˱�����ͼ�ֶΣ����浽����� `localStorage`������д�������ݿ⡣
- ���衰ÿ��ҵ��ģ�鶼�������ֶβ�����ÿ����¼���ֶ�ֵ������Ҫ��������ֶζ������ֶ�ֵ�������ֻ��ǰ��ʵ�֡�

��֤�����
- `node --test dehui-property-admin/src/utils/listSummary.test.js dehui-property-admin/src/utils/tableViewPrefs.test.js` ͨ����
- `cd dehui-property-admin && npm run build` ͨ����

## 2026-06-28 ��ά��񹤾����������޸�

���ΰ��û����豸̨��ҳ������ɸѡ�����顢����ȹ��ܲ�������ʹ�á������޸���

�������
- `DhicDataToolbar.vue` �����еġ������ֶ� / ������� / ��ӷ��� / �������ʹ���� `text type="primary"`����ȫ������ֻ�ų��� `.is-link` ��ť��������Щ���ְ�ť�� `.el-button--primary:not(.is-link)` ��ȾΪ�������ɫʵ�İ�ť��
- �豸̨��ҳ�Ա���ɰ汾����ͼ״̬��ֻ���˾ɵ� `sortKey/groupKey` ������û�н����°� `filters/groups/sorts/customColumns`����˴�ɸѡ�����顢���򵯲��ȱ�ٿ�ִ��������

�޸����ݣ�
- `DhicDataToolbar.vue` �������ڵ����ֲ�����ť��Ϊ `link type="primary"`�����ⱻȫ������ť��ʽ���ˡ�
- `EquipmentList.vue` ��Ϊ���� `useDhicTableView`�������°��ֶΡ��Զ����ֶΡ�ɸѡ������顢��������и�������
- �豸̨�����������Զ����ֶ�����Ⱦ���ͳ�λ����ҳ����ͬһ�׽�����Ϊ��

��֤�����
- `node --test dehui-property-admin/src/utils/listSummary.test.js dehui-property-admin/src/utils/tableViewPrefs.test.js` ͨ����
- `cd dehui-property-admin && npm run build` ͨ����
- `http://localhost:5173/equipment` ���� 200��
- `http://localhost:5173/parking/spaces` ���� 200��

## 2026-06-28 �û�����༭��ɫ�����޸�

�����޸���̨��ϵͳ���� -> �û����������/�༭�û�������ѡ���ɫ�󱣴治��Ч�����⡣

�������
- ǰ�˱༭�û�����Ѿ��� `roleIds` �Ž� `PUT /api/system/users/{id}` �����塣
- ��� `SystemAdminController.createUser()` �� `updateUser()` ԭ��ֻά�� `sys_user` �����ֶΣ�δ���� `roleIds`����˵�����ѡ��Ľ�ɫ����д�� `sys_user_role`��

�޸����ݣ�
- `dehui-property-management/src/main/java/com/dehui/property/modules/system/controller/SystemAdminController.java`
  - `POST /api/system/users` ʹ����������д�����û�������������� `roleIds` ʱͬ��д�� `sys_user_role`��
  - `PUT /api/system/users/{id}` ����������� `roleIds` ʱ��ͬ���滻���û��� `sys_user_role` ��ϵ��
  - ����ͬһ `replaceUserRoles` �߼���������� `POST /api/system/users/{id}/roles` �����ɫ�ӿڣ���������д���߼��ֲ档
  - �� `roleIds` �������ո��û���ɫ�����ں�̨����ȡ����ɫ��
- `dehui-property-management/src/test/java/com/dehui/property/modules/system/controller/SystemAdminControllerTest.java`
  - �����ع���ԣ����������û��ͱ༭�û��ύ `roleIds` ���д����滻�û���ɫ��

��֤�����
- TDD ��ƣ��������Ժ󣬾ɴ���ִֻ�� `INSERT/UPDATE sys_user`��δ���� `sys_user_role`������ʧ�ܡ�
- TDD �̵ƣ��޸��� `mvn -q "-Dtest=SystemAdminControllerTest#createUserAcceptsRoleIdsFromCreateForm,updateUserAcceptsRoleIdsFromEditForm" test` ͨ����
- `mvn -q -Dtest=SystemAdminControllerTest test` ͨ����
- `cd dehui-property-management && mvn -q -DskipTests compile` ͨ����
- `cd dehui-property-admin && npm run build` ͨ�������� Vite chunk size warning��
- ��� 8080 �������`admin / 123456` ��¼�ɹ���`GET /api/system/roles` ���� 7 ����ɫ��`GET /api/system/users` �����û������Ұ��� `roleIds`��
- ǰ�� `http://localhost:5173/system/users` ���� 200��

## 2026-06-28 �⻧��ϵ���˺�ɾ����ť

�����޸���̨�����޹��� -> �⻧�������ϵ���˺ŵ�����ȱ��ɾ�����������⡣

�޸����ݣ�
- `dehui-property-management/src/main/java/com/dehui/property/modules/tenant/controller/TenantController.java`
  - ���� `DELETE /api/tenant/{tenantId}/contacts/{id}`��
  - ɾ����ϵ�˲�����ɾ����ֻ���� `tenant_contact.deleted = 1`������ `tenant_id` ������������⻧��ɾ��
  - ������ϵ���б��С����󶨲�ѯ��ͨ�� `deleted = 0` ���ˣ�ɾ�����˺Ų��������������ϵ��ά����С�����¼������С�
- `dehui-property-admin/src/views/tenant/TenantList.vue`
  - ��ϵ���˺ŵ���������������ɾ������ť��
  - ���ɾ���ȵ���ȷ�Ͽ�ȷ�Ϻ������ʵ���ɾ���ӿڣ���ˢ����ϵ���б���⻧���������
- `dehui-property-management/src/test/java/com/dehui/property/modules/tenant/TenantControllerContactValidationTest.java`
  - �����ع���ԣ�������ϵ��ɾ�����밴 `tenant_id + contact_id` ��ɾ����

��֤�����
- TDD ��ƣ��������Ժ󣬾ɿ�����û�� `deleteTenantContact` ���������Ա���ʧ�ܡ�
- TDD �̵ƣ��޸��� `mvn -q "-Dtest=TenantControllerContactValidationTest#deleteTenantContactSoftDeletesWithinTenant" test` ͨ����
- `cd dehui-property-management && mvn -q -DskipTests compile` ͨ����
- `cd dehui-property-admin && npm run build` ͨ�������� Vite chunk size warning��

## 2026-06-28 ��ά���������Զ����ֶγ־û��޸�

�������
- �˵�����ҳģ���ѽ��� `DhicDataToolbar`�����ű�û��ͬ������ `useDhicTableView`��`tablePrefs`��`tableRows`��`billRowClassName` ��״̬����������ʱ�� `tablePrefs.visibleColumns` δ���壬����������հס�
- ��ά���ı���ɸѡ�������������δ��д���������������׳��֡���̨������������� No Data�����󵼡�
- �Զ����ֶδ�ǰ������ǰ�� `localStorage` ��ͼ���ã��ͱ༭���/���ҵ�����ݲ���Ӧ��

�޸����ݣ�
- `dehui-property-admin/src/views/tenant/BillList.vue`
  - �����˵�����ҳ��ά���ű����ߡ�
  - �˵��༭�������� `CustomFieldEditor`��֧�ֱ༭�Զ����ֶ�ֵ��
  - �˵��б����ʱ�ϲ�����Զ����ֶ�ֵ���ӿڲ�����ʱ����Ϊ�����ֶ��б�����ٰ�����
- `dehui-property-admin/src/views/parking/ParkingSpaceList.vue`
  - ��λ���������ֶ�ֵ��༭������Ӧ������ʱд���Զ����ֶ�ֵ��
  - �Զ����ֶζ���/ֵ�ӿ��쳣ʱ������ʾ���ó�λ�ֶΡ�
- `dehui-property-admin/src/components/DhicDataToolbar.vue`
  - ����ɸѡ�����顢������и�����ʱ��ʾ��������ͼ��������������ͼ���������⡣
- `dehui-property-admin/src/composables/useDhicTableView.js`
  - ���� `tableEmptyText`�����֡������ݡ��͡���ǰ��ͼɸѡ�޽������
- `dehui-property-admin/src/utils/tableViewPrefs.js`
  - �Զ�����δ��дֵ�ı�����ͼɸѡ�������������������ˡ�
- `dehui-property-management/src/main/java/com/dehui/property/modules/customfield/controller/CustomFieldController.java`
  - ����ͨ���Զ����ֶζ�����ֶ�ֵ�ӿڡ�
- `dehui-property-management/src/main/resources/db/migration/V18__custom_field_values.sql`
  - ���� `custom_field_definition` �� `custom_field_value`����ģ�� + ��¼ ID �����ֶζ������ֶ�ֵ��
- `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`
  - ���� `/custom-fields/**` ��� RBAC У�飬�������˵���ͣ��������ģ��Ȩ�ޡ�

��֤�����
- `node --test dehui-property-admin/src/utils/customFieldValues.test.js src/utils/tableViewPrefs.test.js` ͨ����
- `cd dehui-property-admin && npm run build` ͨ�������� Vite chunk size warning��
- `cd dehui-property-management && mvn -q -DskipTests compile` ͨ����
- `cd dehui-property-management && mvn -q "-Dtest=CustomFieldControllerTest" test` ͨ����
- ��� 8080 �������Flyway ��ִ�е� V18��
- `GET /api/custom-fields/bills` �� `GET /api/custom-fields/bills/values?recordIds=1,2` ���� 200��
- �������֤ `/bills`��`/equipment`��`/parking/spaces`��`/floors` �����ݺͱ���������Ⱦ�����δ����µ� Vue error/warn��

## 2026-06-28 �˵���άɸѡҵ��ѡ���޸�

�������
- ���˵�������Ϸ�ҵ���ѯ��ʹ����ʵ�⻧�б���⻧��������ʾ `�⻧���ƣ�ID:x��`��
- �·� `DhicDataToolbar` ��άɸѡֻ֪���˵������ı��ֶΣ�δ�յ��⻧���˵����͡���Դ����ˡ��տ�״̬����Ʊ״̬��ҵ��ѡ����ֻ����ʾ��ͨ�����
- ��άɸѡ���������������������һ����ֵ�������� `normalizeTableViewPrefs` ������ɾ��δ��дֵ�������������û�û�л�����ѡ�ֶ�����ɸѡֵ��

�޸����ݣ�
- `dehui-property-admin/src/components/DhicDataToolbar.vue`
  - ɸѡ�ֶ�֧�� `options` ���������
  - �ֶδ���ҵ��ѡ��ʱ��ɸѡֵ�ؼ���ʾ�����������򣻶�ѡ�ֶ�֧�ֶ�ѡ��
  - �л�ɸѡ�ֶ�ʱ�Զ���վ�ֵ��������ֶβ������ɸѡֵ��
- `dehui-property-admin/src/views/tenant/BillList.vue`
  - �˵���άɸѡ�����⻧���˵����͡���Դ�����״̬���տ�״̬����Ʊ״̬ѡ�
  - �⻧ɸѡֵͳһΪ `�⻧���ƣ�ID:x��`�����Ϸ�ҵ���ѯ����չʾ����һ�¡�
  - �տ�״̬ɸѡʹ���˵�ʵ��չʾ�߼�������δ���˵���ʾ����ɸѡΪ�������ڡ���
- `dehui-property-admin/src/utils/tableViewPrefs.js`
  - ����δ��д������ɸѡ�������ڵ����༭��
  - ʵ�ʹ��˺Ͷ�����Ч����ͳ��ֻ��������������
  - ֧��������ѡ����ֵ����ɸѡ��
- `dehui-property-admin/src/composables/useDhicTableView.js`
  - ������ͼɸѡ��ǩֻչʾ��ʵ��Ч�����������������Ӱ��ͳ�ơ�
- `dehui-property-admin/src/utils/tableViewPrefs.test.js`
  - �����ع���ԣ�����δ��д��������༭��ʵ�ʹ��˺��Կ�����������ѡ��ɸѡ��

��֤�����
- TDD ��ƣ��������Ժ󣬾ɴ����ɾ����������������ɸѡ�޷����м�¼��
- TDD �̵ƣ�`cd dehui-property-admin && node --test src/utils/tableViewPrefs.test.js src/utils/customFieldValues.test.js` ͨ����
- `cd dehui-property-admin && npm run build` ͨ�������� Vite chunk size warning��
- �������֤ `http://localhost:5173/bills` ������Ⱦ��
- �������֤�˵���άɸѡ�������ɸѡ�� -> ������������������пɱ�����ֶ��л�Ϊ���⻧����ֵ�ؼ���ʾ�����򣬲���ʾ `�Ϻ���ľ�����������ID:7��`��`�Ϻ�����Ƽ����޹�˾��ID:6��`��

## 2026-06-28 �û�����ʱ�û�����Ĭ�Ͻ�ɫ����

�������
- ��� `POST /api/system/users` ����������� `roleIds` ʱ������ȷд�� `sys_user_role`���ӿ���·����֤ͨ����
- ��ǰ�û�����ҳ�ġ��û����͡��͡���ɫ���������ֶΡ��û�ѡ���û�����=������Ա/������Ա/����ά����Ա����ʱ��ǰ��û���Զ���� `roleIds`��
- ��������û����ֻѡ�����û����͡�û�����ֶ���һ�Ρ���ɫ�����ύ����˵� `roleIds` ��Ϊ�գ��б����ʾ��δ���䡱��

�޸����ݣ�
- `dehui-property-admin/src/utils/userRoleDefaults.js`
  - �����û����͵���ʵ RBAC ��ɫ��ӳ�䣺
    - `ADMIN -> ADMIN`
    - `MANAGER -> PROPERTY_MANAGER`
    - `FINANCE -> FINANCE`
    - `ENGINEER -> ENGINEER`
    - `SECURITY -> SECURITY`
    - `TENANT -> TENANT`
    - `STAFF -> STAFF`
    - `CLEANER -> STAFF`
  - ���ݽ�ɫ�ӿڷ��ص� `roleCode` �� `code` �ֶΡ�
- `dehui-property-admin/src/views/system/UserList.vue`
  - �����û�ʱ�Զ���Ĭ���û����� `STAFF` ��������ҵԱ������ɫ��
  - �л��û�����ʱ�Զ��л�����ӦĬ�Ͻ�ɫ��
  - ����ǰ��� `roleIds` ��Ϊ�գ��ᰴ�û����Ͷ��ײ���Ĭ�Ͻ�ɫ��
  - �û��ֶ��Ľ�ɫ���¼�˹�ѡ�񣬱��������ǿ��ˢ�¸����˹����ɫ���á�
- `dehui-property-admin/src/utils/userRoleDefaults.test.js`
  - ����ӳ����ԣ����� `MANAGER -> PROPERTY_MANAGER`��`SECURITY -> SECURITY`��`CLEANER -> STAFF`��

��֤�����
- TDD ��ƣ��������Ժ�`userRoleDefaults.js` �����ڣ�����ʧ�ܡ�
- TDD �̵ƣ�`cd dehui-property-admin && node --test src/utils/userRoleDefaults.test.js` ͨ����
- `cd dehui-property-admin && npm run build` ͨ�������� Vite chunk size warning��
- ��ʵ�ӿ���֤��ֱ�ӵ��� `POST /api/system/users` ���� `roleIds:[7]`����˻᷵�ظ����û���ɫΪ `������Ա`��˵�����д����б������·������
- ע�⣺�Ѿ�������û�н�ɫ��ϵ����ʷ�û����ᱻ�Զ��޸ģ���Ҫ���±༭������ڡ���ɫ����ť�з���һ�Ρ�
## 2026-06-30 招商房源管理独立模块

完成内容：
- 保留房间管理作为资产台账，不再把房间管理改造成招商展示页。
- 新增后台“招商房源”菜单与页面 `/investment/listings`，支持选择空置房间、维护招商标题/标签/亮点/租金说明/物业费说明、封面与相册、图片预览、房源预览、上架/下架、删除。
- 新增 Flyway 迁移 `V24__investment_room_listing.sql`，创建 `investment_room_listing`、`investment_room_image`，并给 `investment_lead` 增加 `room_id`、`listing_id`，用于把小程序预约线索绑定到具体招商房源和房间。
- 后端 `InvestmentController` 新增后台招商房源管理接口，同时将小程序 `/mobile/investment/rooms`、`/mobile/investment/rooms/{id}` 改为读取已发布招商房源，不直接暴露所有房间台账。
- 小程序招商中心改为动态房源卡片、楼层筛选、房源详情页和预约提交；预约时提交 `listingId`、`roomId`，便于后台线索追踪。
- 后台图片上传保存到 `uploads/public/investment/rooms`，通过 `/api/files/public/investment/rooms/...` 访问，和票据/账单附件目录分离。
- 权限拦截补充 `/admin/investment/**` 到招商权限域，避免新增后台接口绕过 RBAC。

验证：
- `cd dehui-property-management && mvn -q -DskipTests compile` 通过。
- `cd dehui-property-management && mvn -q "-Dtest=InvestmentControllerTest,InvestmentRoomListingMigrationTest" test` 通过。
- `cd dehui-property-admin && npm run build` 通过，仅保留 Vite chunk size warning。
- `node --check dehui-property-miniprogram/pages/investment/index.js` 与 `node --check dehui-property-miniprogram/pages/investment/detail/index.js` 通过。

## 2026-07-01 小程序招商中心 UI 与预约合规修正

完成内容：
- 小程序招商中心首页筛选区去掉 `scroll-x` 横向滚动，改为 flex 自动换行，页面增加 `overflow-x: hidden`，避免 iPhone 12/13 宽度下筛选项和按钮撑出屏幕。
- 首页顶部“立即预约 / 电话咨询”和房源卡片“查看详情 / 预约看房”统一使用可收缩 flex 按钮，避免按钮偏移、遮挡、重叠。
- 空状态文案改为“暂无可展示房源”，并保留“提交需求”入口。
- 首页预约弹窗从原生 date/time picker 改为共享 `calendar-date-field` / `calendar-time-field`，并补充联系人、电话、公司、意向房源、用途、备注等 maxlength。
- 房源详情页同步中文枚举映射：`WAREHOUSE/STORAGE -> 仓库`、`RENTED -> 已出租`、`RESERVED -> 预约/不可用`，避免展示英文或旧中文枚举。
- 房源详情页预约弹窗同步使用共享日期/时间组件、maxlength 和与首页一致的预约表单校验，并继续提交 `listingId`、`roomId`。
- 未新增定位、相机、录音、视频、文件选择等微信隐私能力。

验证：
- `cd dehui-property-miniprogram && node --test tests/*.test.js` 通过，43 条测试全部通过。
- `cd dehui-property-admin && npm run build` 通过，仅保留 Vite chunk size warning。
- `cd dehui-property-management && mvn -q -DskipTests compile` 通过。
- 本地后端验证：未带 token 访问 `/api/investment/listings` 返回 401；使用 `admin / 123456` 登录后，`GET /api/investment/listings?page=1&pageSize=20` 返回 200，当前没有复现 500。
- 本地后端验证：`GET /api/investment/rooms/candidates?page=1&pageSize=20` 返回 200；`GET /api/mobile/investment/rooms` 返回 200。

## 2026-07-06 小程序普通用户开放账单、会议、访客入口

完成内容：
- 小程序首页 `pages/index/index.js` 将“账单中心”“会议预约”“访客预约”入口从租户权限调整为 public，普通用户点击不再弹出“需要绑定租户账号”。
- 小程序“我的”页 `pages/mine/index.js` 将“我的账单”“我的会议”“访客预约”入口从租户权限调整为 public；电子合同、能耗、工单等仍保留原权限规则。
- 后端移动端账单接口 `/mobile/bills` 对未绑定租户的普通移动用户返回空账单与空汇总，不暴露其他租户账单。
- 后端移动端会议接口 `/mobile/meetings`、`/mobile/meetings/bookings` 支持普通移动用户访问与预约；未绑定租户时按 `applicant_mobile_user_id` 隔离本人会议预约记录。
- 后端移动端访客接口 `/mobile/visitors`、`/mobile/visitors` 创建接口支持普通移动用户访问；未绑定租户时允许 `tenant_id` 为空，并按当前小程序用户手机号隔离本人访客记录。

验证：
- TDD 红灯：`cd dehui-property-miniprogram && node tests/public-feature-permissions.test.js` 初始失败，确认三个入口仍为 `tenant` 权限。
- TDD 红灯：`cd dehui-property-management && mvn -q "-Dtest=com.dehui.property.modules.mobile.MobileEndpointCompatibilityTest" test` 初始失败，确认后端三个移动端接口仍抛“请先绑定租户账号”。
- TDD 绿灯：`cd dehui-property-management && mvn -q "-Dtest=com.dehui.property.modules.mobile.MobileEndpointCompatibilityTest" test` 通过。
- `cd dehui-property-miniprogram && node --test tests/*.test.js` 通过，45 条测试全部通过。
- `cd dehui-property-management && mvn -q -DskipTests compile` 通过。

## 2026-07-06 小程序访客预约改为匿名可用

完成内容：
- 保留账单中心、会议预约需要登录账号使用；两者仍然不是匿名接口，未登录访问后端仍返回 401。
- 访客预约调整为无需登录即可进入和提交，页面文案改为“无需登录即可登记访客”，不再因为 `/mobile/visitors` 加载失败展示“去登录或绑定身份”。
- 后端 `AuthInterceptor` 将 `GET /api/mobile/visitors`、`POST /api/mobile/visitors` 放入公开接口，但如果请求携带有效 token 仍会写入 `authPrincipal`，避免已登录用户身份丢失。
- 后端 `VisitorController` 的访客列表和创建接口支持匿名请求；匿名列表返回空记录，匿名创建时 `tenant_id` 为空。取消访客预约仍保持需要登录身份校验。

验证：
- TDD 红灯：`cd dehui-property-miniprogram && node tests/public-feature-permissions.test.js` 初始失败，确认访客页仍包含“请先登录后预约访客”与登录绑定入口文案。
- TDD 红灯：`cd dehui-property-management && mvn -q "-Dtest=com.dehui.property.security.AuthInterceptorPublicMobileVisitorTest,com.dehui.property.modules.mobile.MobileEndpointCompatibilityTest" test` 初始失败，确认 `/api/mobile/visitors` 未匿名放开且 Controller 不接受空 principal。
- TDD 绿灯：`cd dehui-property-miniprogram && node tests/public-feature-permissions.test.js` 通过。
- TDD 绿灯：`cd dehui-property-management && mvn -q "-Dtest=com.dehui.property.security.AuthInterceptorPublicMobileVisitorTest,com.dehui.property.modules.mobile.MobileEndpointCompatibilityTest" test` 通过。
- `cd dehui-property-miniprogram && node --test tests/*.test.js` 通过，46 条测试全部通过。
- `cd dehui-property-management && mvn -q -DskipTests compile` 通过。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
