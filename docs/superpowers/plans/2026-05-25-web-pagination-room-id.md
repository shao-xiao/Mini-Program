# WEB Pagination and Room Identifier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend pagination to the first batch of WEB table pages and stop exposing room database IDs as user-facing identifiers.

**Architecture:** Introduce a backend `PageResponse<T>` payload and `JdbcPagination` helper, then migrate target list endpoints to return `{ records, total, page, pageSize }`. Add a small frontend pagination adapter so pages consume the same response shape and reset to page 1 when query criteria change.

**Tech Stack:** Spring Boot 3, JdbcTemplate, JUnit, Vue 3 Composition API, Element Plus, Vite.

---

### Task 1: Backend Pagination Contract

**Files:**
- Create: `dehui-property-management/src/main/java/com/dehui/property/common/PageResponse.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/common/JdbcPagination.java`
- Test: `dehui-property-management/src/test/java/com/dehui/property/common/JdbcPaginationTest.java`

- [ ] **Step 1: Write the failing helper tests**

Create `JdbcPaginationTest` that builds a test-only in-memory table, inserts 25 rows, calls `JdbcPagination.query(...)` with page `2` and page size `10`, and asserts `total == 25`, `page == 2`, `pageSize == 10`, and the first returned record is row `11`. Runtime dev/prod databases remain MySQL; this test fixture must not be described as the project database.

- [ ] **Step 2: Run the failing test**

Run: `./mvnw -pl dehui-property-management -Dtest=JdbcPaginationTest test`

Expected: compile failure because `JdbcPagination` and `PageResponse` do not exist.

- [ ] **Step 3: Implement the helper**

Create `PageResponse<T>` as a Java record containing `records`, `total`, `page`, and `pageSize`.

Create `JdbcPagination` with:

```java
public static <T> PageResponse<T> query(
        JdbcTemplate jdbcTemplate,
        String recordSql,
        String countSql,
        List<Object> args,
        int page,
        int pageSize,
        RowMapper<T> rowMapper)
```

Normalize invalid page to `1`, invalid page size to `20`, cap page size at `100`, and append `LIMIT ? OFFSET ?` to `recordSql`.

- [ ] **Step 4: Run the helper test**

Run: `./mvnw -pl dehui-property-management -Dtest=JdbcPaginationTest test`

Expected: pass.

### Task 2: Migrate Backend Target Endpoints

**Files:**
- Modify: `dehui-property-management/src/main/java/com/dehui/property/modules/building/controller/BuildingController.java`
- Modify: `dehui-property-management/src/main/java/com/dehui/property/modules/bill/controller/BillController.java`
- Modify: `dehui-property-management/src/main/java/com/dehui/property/modules/investment/controller/InvestmentController.java`
- Modify: `dehui-property-management/src/main/java/com/dehui/property/modules/workorder/controller/WorkOrderController.java`
- Modify: `dehui-property-management/src/main/java/com/dehui/property/modules/parking/controller/ParkingController.java`
- Modify: `dehui-property-management/src/main/java/com/dehui/property/modules/legacy/LegacyWriteController.java`
- Test: `dehui-property-management/src/test/java/com/dehui/property/modules/building/BuildingControllerPaginationTest.java`

- [ ] **Step 1: Write a representative controller test**

Create `BuildingControllerPaginationTest` using MockMvc. Insert at least 12 rooms for one building and floor, request `/rooms?page=2&pageSize=10`, and assert `data.records` has 2 items, `data.total` is 12, `data.page` is 2, and `data.pageSize` is 10.

- [ ] **Step 2: Run the failing controller test**

Run: `./mvnw -pl dehui-property-management -Dtest=BuildingControllerPaginationTest test`

Expected: fail because `/rooms` still returns an array.

- [ ] **Step 3: Update real list endpoints**

Return `ApiResponse<PageResponse<Map<String, Object>>>` from:

- `/rooms`
- `/bills`
- `/investment/contents`
- `/investment/displays`
- `/investment/leads`
- `/workorders`
- `/parking/spaces`

Each endpoint keeps existing filters and ordering while applying the same WHERE clause to count and records queries.

- [ ] **Step 4: Update legacy list endpoints**

Return an empty `PageResponse` from `/assets`, `/equipments`, `/energy/readings`, `/energy/meters`, `/energy/stats`, `/feerules`, and `/inspections` instead of a raw empty array.

- [ ] **Step 5: Run backend tests**

Run:

```bash
./mvnw -pl dehui-property-management -Dtest=JdbcPaginationTest,BuildingControllerPaginationTest test
```

Expected: pass.

### Task 3: Frontend Pagination Adapter

**Files:**
- Create: `dehui-property-admin/src/utils/pagination.js`

- [ ] **Step 1: Add adapter functions**

Create:

```js
export function createPagination(pageSize = 20) {
  return { currentPage: 1, pageSize, total: 0 }
}

export function pageParams(pagination) {
  return { page: pagination.currentPage, pageSize: pagination.pageSize }
}

export function readPage(data) {
  if (Array.isArray(data)) return { records: data, total: data.length }
  return {
    records: data?.records || data?.content || [],
    total: data?.total ?? data?.totalElements ?? 0
  }
}
```

### Task 4: Frontend Target Pages

**Files:**
- Modify: `dehui-property-admin/src/views/building/RoomList.vue`
- Modify: `dehui-property-admin/src/views/building/EquipmentList.vue`
- Modify: `dehui-property-admin/src/views/building/AssetList.vue`
- Modify: `dehui-property-admin/src/views/investment/InvestmentContentList.vue`
- Modify: `dehui-property-admin/src/views/investment/InvestmentLeadList.vue`
- Modify: `dehui-property-admin/src/views/operation/WorkOrderList.vue`
- Modify: `dehui-property-admin/src/views/operation/InspectionList.vue`
- Modify: `dehui-property-admin/src/views/tenant/BillList.vue`
- Modify: `dehui-property-admin/src/views/parking/ParkingSpaceList.vue`
- Modify: `dehui-property-admin/src/views/energy/EnergyRecordList.vue`

- [ ] **Step 1: Add pagination state and adapter usage**

For each page, import `createPagination`, `pageParams`, and `readPage`, include page params in list requests, map `records` and `total`, and add a common `handleSizeChange` that resets `currentPage` to `1`.

- [ ] **Step 2: Add Element Plus pagination footer**

Render:

```vue
<div class="pagination-wrapper">
  <el-pagination
    v-model:current-page="pagination.currentPage"
    v-model:page-size="pagination.pageSize"
    :total="pagination.total"
    :page-sizes="[10, 20, 50, 100]"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="loadList"
    @size-change="handleSizeChange"
  />
</div>
```

Use each page's existing load function name instead of `loadList`.

- [ ] **Step 3: Fix room visible identifier**

In `RoomList.vue`, replace `type="index"` with a calculated sequence:

```vue
<template #default="{ $index }">
  {{ (pagination.currentPage - 1) * pagination.pageSize + $index + 1 }}
</template>
```

Keep `row.id` only for edit and delete calls.

- [ ] **Step 4: Run frontend build**

Run: `npm --prefix dehui-property-admin run build`

Expected: pass.

### Task 5: End-to-End Verification

**Files:**
- No new source files unless a verification issue requires a targeted fix.

- [ ] **Step 1: Run backend focused tests**

Run:

```bash
./mvnw -pl dehui-property-management -Dtest=JdbcPaginationTest,BuildingControllerPaginationTest test
```

Expected: pass.

- [ ] **Step 2: Browser verify room page**

Open `http://localhost:5173/rooms`. Confirm the room table shows a sequence column, does not show database `id`, and has a pagination footer.

- [ ] **Step 3: Browser verify a second target page**

Open one additional target page, such as `http://localhost:5173/bills` or `http://localhost:5173/parking/spaces`, and confirm the pagination footer is visible and the page loads without API shape errors.
