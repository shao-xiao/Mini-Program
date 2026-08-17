# Mobile User Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend admin page under System Management for WeChat mini-program phone-login users, with manual tenant-company assignment and tenant unbinding.

**Architecture:** Keep mini-program users separate from backend `sys_user` accounts. Add admin-only `/api/mobile-users` endpoints that operate on `mobile_user` and `mobile_user_bind`, then add a Vue page at `/system/mobile-users` under the existing System Management menu.

**Tech Stack:** Spring Boot 3, Java 21, JdbcTemplate, Vue 3, Vite, Element Plus, existing `request.js`.

---

### Task 1: Backend Mobile User Admin API

**Files:**
- Create: `dehui-property-management/src/main/java/com/dehui/property/modules/mobile/controller/MobileUserAdminController.java`
- Test: `dehui-property-management/src/test/java/com/dehui/property/modules/mobile/MobileUserAdminControllerTest.java`
- Modify: `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`

- [x] Add failing tests for listing mobile users, binding a mobile user to a tenant, and unbinding a tenant.
- [x] Implement `GET /api/mobile-users`, `POST /api/mobile-users/{id}/bind-tenant`, and `POST /api/mobile-users/{id}/unbind-tenant`.
- [x] Map `/api/mobile-users` to `system:user:view` for GET and `system:user:update` for POST in `AuthInterceptor`.
- [x] Run `mvn -q -Dtest=MobileUserAdminControllerTest test`.

### Task 2: Admin Frontend Page

**Files:**
- Create: `dehui-property-admin/src/views/system/MobileUserList.vue`
- Test: `dehui-property-admin/tests/mobileUserAdmin.test.mjs`
- Modify: `dehui-property-admin/src/router/index.js`
- Modify: `dehui-property-admin/src/config/access.js`

- [x] Add a failing source-level test that verifies the new route, fallback menu entry, endpoint usage, tenant selector, bind action, and unbind action.
- [x] Implement the page with filters for phone/user type/status and a table showing phone, nickname/name, identity, bound tenant, bound contact, status, and created time.
- [x] Add an Element Plus dialog to select tenant and optional tenant contact before posting `/mobile-users/{id}/bind-tenant`.
- [x] Add unbind confirmation that posts `/mobile-users/{id}/unbind-tenant`.
- [x] Run `node --test tests/mobileUserAdmin.test.mjs` and `npm run build`.

### Task 3: Documentation And Final Verification

**Files:**
- Modify: `CONTEXT_SUMMARY.md`

- [x] Record where phone-login users are stored and how the manual tenant assignment works.
- [x] Run backend compile or targeted tests.
- [x] Run frontend build.
- [x] Summarize the storage tables, new backend module, new page location, and verification results.

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
