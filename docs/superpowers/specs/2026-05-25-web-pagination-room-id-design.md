# WEB table pagination and room identifier design

## Background

The admin WEB app has several table-style pages that currently either load all rows or use inconsistent pagination response shapes. As data grows, these pages should use backend pagination consistently instead of client-side slicing.

The room management page also must not expose the database primary key as a business identifier. Database IDs may skip after deletes and are only for internal operations.

## Scope

This change covers the first batch of high-traffic table pages:

- Room management
- Equipment ledger
- Space assets
- Investment content
- Investment leads
- Work orders
- Inspections
- Finance bill management
- Parking spaces
- Energy meter readings

Pages outside this list can keep their current behavior for this pass unless they share the same backend helper and can be updated without expanding risk.

## Pagination contract

Frontend requests use one-based page numbers:

```text
page=1
pageSize=20
```

Backend responses use the standard API envelope and a unified page payload:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "pageSize": 20
  }
}
```

Rules:

- `page` is the current one-based page number.
- `pageSize` defaults to 20 unless the page already has a strong reason to use 10.
- `total` is the total number of matching rows before pagination.
- `records` contains only the current page rows.
- The backend performs SQL pagination with `LIMIT` and `OFFSET`.
- Query condition changes reset the frontend page to `1`.
- The frontend must not fetch all rows and paginate locally.

For existing endpoints that already return Spring-style page data such as `content` and `totalElements`, the frontend adapter may temporarily support both shapes while the endpoint is migrated to `records` and `total`.

## Backend design

Add a small common pagination model/helper under the backend common layer:

- `PageResponse<T>` for `{ records, total, page, pageSize }`.
- A JDBC pagination helper or local utility method for:
  - normalizing `page` and `pageSize`,
  - calculating offset,
  - executing count SQL,
  - executing paged query SQL.

Each target list endpoint should:

- accept `page` and `pageSize`,
- keep existing filter parameters,
- return `ApiResponse<PageResponse<...>>`,
- preserve current create/update/delete behavior,
- avoid changing table schemas only for pagination.

## Frontend design

Add or reuse a shared pagination pattern in the admin app:

- local reactive state: `currentPage`, `pageSize`, `total`,
- request params: `{ page: currentPage, pageSize }`,
- response mapping from `data.records` and `data.total`,
- `<el-pagination>` layout consistent with energy meter readings:
  - `total`
  - `sizes`
  - `prev`
  - `pager`
  - `next`
  - `jumper`

Every target table page should:

- render pagination below the table,
- reload when page or page size changes,
- reset to page 1 when filters change,
- keep the existing UI style and table columns unless this spec says otherwise.

## Room management identifier rule

Room management must not display the database `id` column.

The table should display:

- Sequence number
- Building
- Floor
- Room number
- Room name or purpose
- Area
- Status
- Operations

The sequence number is calculated in the frontend:

```js
(currentPage - 1) * pageSize + rowIndex + 1
```

The real `id` remains in row data for edit, delete, and detail API calls.

## Error handling

Existing global API error handling remains in place. Endpoint-specific failures should keep showing the same user-facing error style already used by the page.

If pagination parameters are missing or invalid, the backend should fall back to safe defaults:

- `page = 1`
- `pageSize = 20`

The backend should cap excessive page sizes if the existing codebase already has such a convention. If not, this pass can use a conservative maximum of `100`.

## Testing plan

Backend:

- Add focused tests for at least one representative paginated endpoint.
- Verify the response contains `records`, `total`, `page`, and `pageSize`.
- Verify filters still affect both count and records.

Frontend:

- Run the admin build.
- Browser-check room management:
  - pagination is visible,
  - database ID is not shown,
  - sequence number is stable across pages.
- Browser-check at least one non-room target page to confirm shared pagination behavior.

## Out of scope

- Full redesign of the admin UI.
- Database schema redesign.
- Permission model changes.
- Replacing all secondary/nested tables not listed in the first batch.
