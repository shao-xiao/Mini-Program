# Investment Content Sections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert investment content from a generic table into mini-program display section management with admin preview and dynamic mini-program rendering.

**Architecture:** Keep `investment_display` as the source table and extend it with section fields so old data remains. Admin APIs expose pageable maintenance rows and preview grouping; mobile APIs expose only published rows grouped by `sectionKey`. WEB admin uses the same backend data for list/edit/preview, while the mini-program renders the grouped mobile payload.

**Tech Stack:** Spring Boot 3, Java 21, JdbcTemplate, Flyway SQL migrations, Vue 3, Element Plus, native WeChat Mini Program.

---

### Task 1: Backend Contract Tests

**Files:**
- Create: `dehui-property-management/src/test/java/com/dehui/property/modules/investment/InvestmentControllerTest.java`

- [ ] **Step 1: Write failing tests**

Add tests that expect:
- `/investment/contents` rows include `sectionKey`, `sectionName`, `subtitle`, `imageUrl`, and `updatedAt`.
- `/mobile/investment/overview` returns grouped sections and filters unpublished rows.
- `/investment/contents/{id}/disable` and `DELETE /investment/contents/{id}` exist and update the database.

- [ ] **Step 2: Run tests and verify red**

Run:

```powershell
cd dehui-property-management
& "C:\Users\Administrator\Desktop\dehui_\.tools\apache-maven-3.9.11\bin\mvn.cmd" -Dtest=InvestmentControllerTest test
```

Expected: fail because the current controller does not expose the new fields/grouping/disable/delete behavior.

### Task 2: Database Migration

**Files:**
- Create: `dehui-property-management/src/main/resources/db/migration/V4__investment_content_sections.sql`

- [ ] **Step 1: Add migration**

Add `section_key`, `subtitle`, and `image_url` to `investment_display`, add section/publish/sort index, and map existing rows to `highlight` by default while preserving old titles and content.

- [ ] **Step 2: Add schema assertion**

Extend schema migration tests or add a migration test that asserts the new migration contains the new fields and index.

### Task 3: Backend Implementation

**Files:**
- Modify: `dehui-property-management/src/main/java/com/dehui/property/modules/investment/controller/InvestmentController.java`

- [ ] **Step 1: Select normalized admin rows**

Return `sectionKey`, derived `sectionName`, `subtitle`, `imageUrl`, `publishStatus`, `status`, `sortOrder`, `updatedAt`, and `remark`.

- [ ] **Step 2: Support create/update fields**

Accept `sectionKey`, `title`, `subtitle`, `content`, `imageUrl`, `sortOrder`, `status`/`publishStatus`, and `remark`.

- [ ] **Step 3: Support lifecycle endpoints**

Implement publish, disable, and soft delete endpoints.

- [ ] **Step 4: Support grouped preview/mobile payloads**

Return all seven section keys. Admin preview can include all non-deleted rows; mobile overview only returns `publish_status = 'PUBLISHED'`.

- [ ] **Step 5: Run backend test**

Run `mvn -Dtest=InvestmentControllerTest test`, then full `mvn test`.

### Task 4: WEB Admin Page

**Files:**
- Modify: `dehui-property-admin/src/views/investment/InvestmentContentList.vue`

- [ ] **Step 1: Update list columns**

Show sequence, display section, title, subtitle, sort, status, updated time, and actions. Do not display DB ID or full content.

- [ ] **Step 2: Update form**

Fields: section, title, subtitle, content, image URL, sort, status, remark.

- [ ] **Step 3: Add preview**

Add a right-side mini-program-style preview panel and a row preview dialog. Preview groups rows by `sectionKey`.

- [ ] **Step 4: Add operations**

Support edit, preview, publish, disable, and delete through backend APIs.

### Task 5: Mini Program Investment Page

**Files:**
- Modify: `dehui-property-miniprogram/pages/investment/index.js`
- Modify: `dehui-property-miniprogram/pages/investment/index.wxml`
- Modify: `dehui-property-miniprogram/pages/investment/index.wxss`

- [ ] **Step 1: Fetch grouped content**

Use `/mobile/investment/overview` and normalize all section arrays.

- [ ] **Step 2: Render dynamic sections**

Render hero, highlights, policies, introduction, location, contact, and notice from backend data only.

- [ ] **Step 3: Keep lead submission**

Leave the lead form behavior intact.

### Task 6: Docs and Verification

**Files:**
- Modify: `CONTEXT_SUMMARY.md`

- [ ] **Step 1: Record the change**

Document the new investment content section mapping, admin preview, and mobile API behavior.

- [ ] **Step 2: Verify**

Run:

```powershell
cd dehui-property-management
& "C:\Users\Administrator\Desktop\dehui_\.tools\apache-maven-3.9.11\bin\mvn.cmd" test
cd ..\dehui-property-admin
npm run build
```

Use the browser to verify `/investment/contents` after the frontend server reloads.
