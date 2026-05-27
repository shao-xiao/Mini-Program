# Production Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the project foundation around Spring Boot 3, MySQL, Redis, Nginx, and a unified mini program/admin API structure.

**Architecture:** Use a modular Spring Boot monolith with MySQL as the source of truth and Redis as an auxiliary cache/session layer. Keep the mini program UI largely intact while moving it into the main repository and standardizing request behavior.

**Tech Stack:** Java 21, Spring Boot 3.2, Maven, MySQL 8, Redis, Flyway, Vue 3/Vite, native WeChat Mini Program, Nginx.

---

### Task 1: Baseline Tests For Architecture Utilities

**Files:**
- Create: `dehui-property-management/src/test/java/com/dehui/property/common/ApiResponseTest.java`
- Create: `dehui-property-management/src/test/java/com/dehui/property/security/RedisKeysTest.java`
- Create: `dehui-property-management/src/test/java/com/dehui/property/schema/SchemaMigrationTest.java`

- [ ] **Step 1: Write failing tests**

Create tests that expect `ApiResponse.success`, `RedisKeys`, and the baseline Flyway migration to exist.

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn test`

Expected: FAIL because the new utility classes and migration do not exist yet.

### Task 2: Rebuild Backend Dependencies And Configuration

**Files:**
- Modify: `dehui-property-management/pom.xml`
- Modify: `dehui-property-management/src/main/resources/application.yml`
- Create: `dehui-property-management/src/main/resources/application-dev.yml`
- Create: `dehui-property-management/src/main/resources/application-prod.yml`

- [ ] **Step 1: Add dependencies**

Add MySQL driver, Redis starter, Flyway, validation, JPA, Spring Security crypto, Lombok, and tests.

- [ ] **Step 2: Add environment-first config**

Configure MySQL and Redis with environment variables. Keep dev/prod split and avoid embedded development databases as runtime storage.

### Task 3: Add Common, Security, And Config Foundation

**Files:**
- Create: `dehui-property-management/src/main/java/com/dehui/property/common/ApiResponse.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/common/BaseEntity.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/common/BusinessException.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/common/GlobalExceptionHandler.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/security/AuthPrincipal.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/security/RedisKeys.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/security/TokenService.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/security/AuthInterceptor.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/config/WebConfig.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/config/RedisConfig.java`
- Create: `dehui-property-management/src/main/java/com/dehui/property/config/FileStorageProperties.java`

- [ ] **Step 1: Implement response and exception handling**

Return `{ code, message, data }` consistently.

- [ ] **Step 2: Implement token and Redis key utilities**

Use Redis key prefixes exactly as documented in the design.

- [ ] **Step 3: Implement auth interceptor**

Allow public login, health, mobile public notice/investment endpoints, and file preview paths. Require token for protected APIs.

### Task 4: Add MySQL Baseline Schema

**Files:**
- Create: `dehui-property-management/src/main/resources/db/migration/V1__baseline_schema.sql`

- [ ] **Step 1: Create baseline schema**

Create tables for system, mobile, building, tenant, contract, bill, meeting, parking, workorder, visitor, investment, checkin, file, log, and notice modules.

- [ ] **Step 2: Include comments, indexes, and unique constraints**

Every formal business table includes primary key, status, audit fields, logical deletion, and important indexes.

### Task 5: Add API Skeletons

**Files:**
- Create: `dehui-property-management/src/main/java/com/dehui/property/modules/common/HealthController.java`
- Create: controllers under each `modules/*/controller` package

- [ ] **Step 1: Add health and auth endpoints**

Expose `/api/ping`, `/api/system/login`, `/api/auth/me`, `/api/mobile/auth/wechat-login`, and `/api/mobile/auth/me`.

- [ ] **Step 2: Add protected module route placeholders**

Expose route skeletons for the planned modules without returning fake business data.

### Task 6: Merge Mini Program Into Main Repository

**Files:**
- Modify: `dehui-property-miniprogram/**`

- [ ] **Step 1: Copy the current independent mini program source**

Copy from `C:\Users\Administrator\Desktop\代码\5.22\dehui-property-miniprogram`, excluding `.git` and private WeChat config.

- [ ] **Step 2: Normalize request behavior**

Keep `/mobile/**` calls, dynamic backend base URL, raw `Authorization` token, and unified response handling.

### Task 7: Add Deployment Assets And Verification Docs

**Files:**
- Create: `deploy/nginx/dehui-property.conf`
- Create: `deploy/env/dehui-property.env.example`
- Modify: `README.md`

- [ ] **Step 1: Add Nginx reverse proxy example**

Serve admin static files and proxy `/api` to Spring Boot.

- [ ] **Step 2: Add environment example**

Document MySQL, Redis, upload, and WeChat secret environment variables.

### Task 8: Verify

**Files:**
- No new files.

- [ ] **Step 1: Run backend tests**

Run: `mvn test`

Expected: PASS. If Maven is unavailable locally, use a downloaded local Maven distribution and document that.

- [ ] **Step 2: Run admin frontend build**

Run: `npm run build`

Expected: PASS.

- [ ] **Step 3: Review git diff**

Run: `git status --short` and inspect the changed files.
