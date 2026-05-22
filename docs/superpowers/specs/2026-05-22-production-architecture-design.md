# Production Architecture Design

## Goal

Rebuild the project around a long-term production architecture while the product is still in test stage. Existing code is a feature reference, not an architecture constraint.

## Architecture

```text
WeChat Mini Program
  -> HTTPS domain / Nginx / SSL
  -> Spring Boot 3 API
  -> MySQL business database
  -> Redis login state, captcha, permission cache, dashboard cache, rate limit, locks
```

The backend remains a modular monolith. MySQL is the only formal business data store. Redis is auxiliary and must never become the source of truth for tenants, contracts, bills, payments, work orders, or financial records.

## Backend Modules

```text
common      response, exception, base model, utilities
config      web, CORS, Redis, MySQL, file, security wiring
security    token, login state, permission checks
modules
  system      backend users, roles, permissions, menus
  mobile      mini program login, binding, mobile API aggregation
  building    buildings, floors, rooms
  tenant      tenants, contacts
  contract    contracts, contract attachments
  bill        bills, payments, invoices
  meeting     meeting rooms, bookings, meeting bills
  parking     parking spaces, assignments, parking bills
  workorder   repair orders, records, attachments
  visitor     visitor appointments, pass records
  investment  displays, leads, follow records
  checkin     staff checkins, checkin stats
  file        uploaded file records
  log         operation and login logs
  notice      notices and read records
```

## API Rules

- Mini program APIs live under `/api/mobile/**`.
- Admin APIs live under `/api/**` by module.
- Frontends never access MySQL or Redis directly.
- Secrets such as database password, Redis password, and WeChat AppSecret stay on the backend and are supplied through environment variables or server-local config.
- All responses use `{ code, message, data }`.
- Token is sent through `Authorization` with raw token value. Bearer input may be tolerated by the backend, but the frontend standard is raw token.

## MySQL Rules

All formal business data goes to MySQL. Tables use `BIGINT` primary keys, business codes for important business records, status fields, audit fields, logical deletion, comments, indexes, and unique constraints.

The baseline schema covers system, mobile, building, tenant, contract, bill, meeting, parking, workorder, visitor, investment, checkin, file, log, and notice modules.

## Redis Rules

Allowed Redis keys:

```text
dehui:auth:token:{token}
dehui:auth:captcha:{phone}
dehui:wechat:session:{openid}
dehui:permission:user:{userId}
dehui:dashboard:stats:{tenantId}
dehui:rate-limit:{ip}:{api}
dehui:lock:{biz}:{id}
```

Redis may cache derived or temporary data only. If Redis is flushed, the backend must recover from MySQL.

## Mini Program

The mini program UI should not be pointlessly rebuilt. This change focuses on moving the checked independent mini program source into the main repository and aligning its request layer to the backend response and token rules.

## Deployment

Production deployment uses:

- `application-prod.yml` for server-side settings.
- Environment variables for secrets.
- Nginx SSL termination and `/api` reverse proxy to Spring Boot.
- Static admin frontend served by Nginx.

## Verification

- Backend compile/test through Maven.
- Admin frontend build through `npm run build`.
- Mini program request layer checked by source inspection and WeChat DevTools after import.
- Schema checked by automated tests that assert required tables exist in the baseline migration.
