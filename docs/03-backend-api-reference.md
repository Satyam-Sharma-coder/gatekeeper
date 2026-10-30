# 3. Backend API Reference

All endpoints belong to `api-management-service` (base URL `http://localhost:8081`). The other two services expose only `GET /health`.

## 3.1 Conventions

- **Format:** JSON request and response bodies.
- **Authentication:** `Authorization: Bearer <token>` from `POST /api/auth/login`. See [05 - Security and authentication](05-security-and-auth.md).
- **Authorization** is decided by URL prefix:

| URL prefix | Required role |
|---|---|
| `/health`, `/api/auth/login` | none (public) |
| `/api/platform/**` | `PLATFORM_ADMIN` |
| `/api/tenant/**` | `TENANT_ADMIN` |
| `/api/gateway/**` | `GATEWAY_SERVICE` |
| anything else | any authenticated user |

- **Timestamps** are ISO local date-times without a zone, for example `2026-10-29T14:48:03.123456`.
- **Validation** uses Bean Validation (`@Valid`). A body that fails validation is rejected with HTTP 400.
- **Error statuses.** The code throws `ApiException(status, message)` for business errors. The statuses listed below as "Intended" are what the code asks for. Because no `@ControllerAdvice` exists yet, see [08 - Known issues](08-known-issues-and-roadmap.md#1-business-errors-are-not-translated-to-http-statuses), clients may receive a generic 500 instead.

## 3.2 Health

### GET /health  (public)
Response `200`:
```json
{ "serviceStatus": "UP", "dbStatus": "UP", "timestamp": "2026-10-29T14:48:03" }
```
`dbStatus` is `DOWN` when a `SELECT 1` fails.

## 3.3 Authentication

### POST /api/auth/login  (public)
Request:
```json
{ "username": "string (required)", "password": "string (required)" }
```
Response `200`:
```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "role": "TENANT_ADMIN",
  "organizationId": 7
}
```
`organizationId` is `null` for platform and gateway accounts.

Intended errors: `401` unknown user, disabled user or wrong password (same message for all three: "Invalid username or password"); `403` the user's organization is not `ACTIVE`.

## 3.4 Platform: organizations  (role PLATFORM_ADMIN)

`OrganizationResponse`:
```json
{ "id": 7, "name": "Acme Corp", "slug": "acme-corp", "status": "PENDING",
  "createdAt": "...", "updatedAt": "..." }
```

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/api/platform/organizations` | `OrganizationRequest` | 201 `OrganizationResponse` | Created as `PENDING`. Intended 409 if name or slug already exists (case-insensitive) |
| GET | `/api/platform/organizations` | - | 200 list | Excludes `DELETED`. Not paginated |
| GET | `/api/platform/organizations/{id}` | - | 200 | Intended 404 if missing or `DELETED` |
| PUT | `/api/platform/organizations/{id}` | `OrganizationRequest` | 200 | Uniqueness check ignores the organization itself |
| PATCH | `/api/platform/organizations/{id}/status` | `OrganizationStatusRequest` | 200 | Any of `PENDING`, `ACTIVE`, `SUSPENDED`; `DELETED` is rejected (intended 400) |
| DELETE | `/api/platform/organizations/{id}` | - | 204 | Soft delete |

`OrganizationRequest`: `name` (required, at most 150 characters), `slug` (required, at most 100, pattern `^[a-z0-9]+(?:-[a-z0-9]+)*$`).
`OrganizationStatusRequest`: `{ "status": "ACTIVE" }`, required.

## 3.5 Platform: tenant administrator  (role PLATFORM_ADMIN)

An organization has at most one tenant administrator.

`TenantAdminRequest`: `username` (required, at most 100), `password` (required, 8 to 100 characters), `enabled` (default `true`).
`TenantAdminResponse`: `{ "id": 3, "organizationId": 7, "username": "acme-admin", "enabled": true }`. The password hash is never returned.

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/platform/organizations/{organizationId}/tenant-admin` | 201 | Intended 409 if the organization already has an admin or the username is taken |
| GET | `/api/platform/organizations/{organizationId}/tenant-admin` | 200 | Intended 404 if none |
| PUT | `/api/platform/organizations/{organizationId}/tenant-admin` | 200 | Password is required on every update and is re-hashed |
| DELETE | `/api/platform/organizations/{organizationId}/tenant-admin` | 204 | Hard delete of the user |

## 3.6 Tenant: own organization  (role TENANT_ADMIN)

The organization is resolved from the token, not from the URL.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/api/tenant/organization` | - | 200 `OrganizationResponse` | |
| PUT | `/api/tenant/organization` | `TenantOrganizationRequest` | 200 | Only the **name** can be changed. Intended 409 if another organization already has that name |

`TenantOrganizationRequest`: `{ "name": "required, at most 150 characters" }`.

## 3.7 Tenant: routes  (role TENANT_ADMIN)

All operations are limited to the caller's organization. A route that belongs to another organization is reported as not found.

### Request body: `RouteRequest`

| Field | Type | Rules |
|---|---|---|
| `routeId` | string | required, at most 100. On update it must equal the stored value |
| `name` | string | required, at most 200 |
| `pathPattern` | string | required, at most 500, must start with `/<organization-slug>/` |
| `targetBaseUrl` | string | required, at most 1000, must be a valid `http` or `https` URL |
| `targetPathPrefix` | string | required, at most 500, must start with `/` |
| `requestsPerMinute` | integer or null | at least 1 when present |
| `responseTimeoutMs` | integer | required, at least 1 |
| `idempotencyEnabled` | boolean | default `false` |
| `active` | boolean | default `false` |
| `allowedMethods` | array of strings | required, not empty; upper-cased on save |
| `headerRules` | array of `HeaderRuleRequest` | required (may be empty) |

`HeaderRuleRequest`:

| Field | Rules |
|---|---|
| `direction` | required, `REQUEST` or `RESPONSE` (case-insensitive, stored upper-case) |
| `action` | required, `ADD` or `REMOVE` (case-insensitive, stored upper-case) |
| `headerName` | required, at most 200 |
| `headerValue` | at most 2000. **Required when action is `ADD`** |

### Response body: `RouteResponse`
```json
{
  "id": 12, "organizationId": 7, "routeId": "orders-v1", "name": "Orders API",
  "pathPattern": "/acme-corp/orders", "targetBaseUrl": "https://orders.internal",
  "targetPathPrefix": "/v1", "requestsPerMinute": 600, "responseTimeoutMs": 5000,
  "idempotencyEnabled": true, "active": true, "configurationVersion": 3,
  "allowedMethods": ["GET", "POST"],
  "headerRules": [ { "id": 1, "direction": "REQUEST", "action": "ADD",
                     "headerName": "X-Tenant", "headerValue": "acme" } ],
  "createdAt": "...", "updatedAt": "..."
}
```

### Endpoints

| Method | Path | Body | Success | What it does |
|---|---|---|---|---|
| POST | `/api/tenant/routes` | `RouteRequest` | 201 `RouteResponse` | Creates the route at version 1. Intended 409 if the `routeId` already exists in the organization |
| GET | `/api/tenant/routes` | - | 200 list | All routes of the organization, any status |
| GET | `/api/tenant/routes/{id}` | - | 200 | `{id}` is the numeric database id |
| PUT | `/api/tenant/routes/{id}` | `RouteRequest` | 200 | Replaces the whole route including header rules; version + 1. Intended 400 if `routeId` differs |
| PATCH | `/api/tenant/routes/{id}/status` | `{ "active": true }` | 200 | Activates or deactivates; version + 1 |
| DELETE | `/api/tenant/routes/{id}` | - | 204 | Records a final `DELETED` version, then removes the route |
| GET | `/api/tenant/route-versions/{routeId}` | - | 200 list | `{routeId}` is the **business** id (for example `orders-v1`), newest first. Still works after the route was deleted |
| GET | `/api/tenant/audit-records` | - | 200 list | All audit records of the organization, newest first |

`RouteVersionResponse`:
```json
{ "id": 40, "routeId": "orders-v1", "versionNumber": 3, "changeType": "UPDATED",
  "configurationSnapshot": "{\"id\":12,...}", "createdAt": "..." }
```
`changeType` is one of `CREATED`, `UPDATED`, `ACTIVATED`, `DEACTIVATED`, `DELETED`. `configurationSnapshot` is a **string** containing the route as JSON; parse it to read it.

`AuditRecordResponse`:
```json
{ "id": 90, "actor": "acme-admin", "action": "UPDATE_ROUTE", "resourceType": "API_ROUTE",
  "resourceId": "orders-v1", "correlationId": null, "durationMs": 14, "result": "SUCCESS",
  "details": "Route configuration version 3 created", "createdAt": "..." }
```

Audit actions written today:

| Action | Written by |
|---|---|
| `CREATE_ROUTE`, `UPDATE_ROUTE`, `ACTIVATE_ROUTE`, `DEACTIVATE_ROUTE`, `DELETE_ROUTE` | Tenant route operations |
| `UPDATE_OWN_ORGANIZATION` | Tenant renames its organization |
| `CREATE_ORGANIZATION`, `UPDATE_ORGANIZATION`, `CHANGE_ORGANIZATION_STATUS`, `DELETE_ORGANIZATION` | Platform organization operations |
| `CREATE_TENANT_ADMIN`, `UPDATE_TENANT_ADMIN`, `DELETE_TENANT_ADMIN` | Platform tenant-admin operations |

Platform actions are recorded against the affected organization, so a tenant's audit log also shows what platform administrators did to it. `result` is always `SUCCESS` today; failed operations are not recorded. If the request carries an `X-Correlation-ID` header, its value is stored in `correlationId`.

## 3.8 Gateway  (role GATEWAY_SERVICE)

### GET /api/gateway/routes
Returns a list of `RouteResponse` for every route where `active = true` **and** the owning organization is `ACTIVE`. Each item carries its `organizationId`. This is the configuration feed the data plane is meant to poll.

## 3.9 Other services

| Service | Endpoint | Response |
|---|---|---|
| smart-gateway-service (8080) | `GET /health` | `serviceName`, `serviceDescription`, `appStatus: "UP"` |
| analytics-service (8082) | `GET /health` | `appName`, `appDescription`, `appHealth: "UP"` |

The field names differ per service. Everything else on these two services requires authentication, and no token validation is configured yet, so every non-health request is rejected.

## 3.10 Quick examples

```
# 1. sign in
curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"acme-admin","password":"<password>"}'

# 2. create a route (use the token from step 1)
curl -s -X POST http://localhost:8081/api/tenant/routes \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"routeId":"orders-v1","name":"Orders API","pathPattern":"/acme-corp/orders",
       "targetBaseUrl":"https://orders.internal","targetPathPrefix":"/v1",
       "requestsPerMinute":600,"responseTimeoutMs":5000,"idempotencyEnabled":false,
       "active":false,"allowedMethods":["GET","POST"],
       "headerRules":[{"direction":"REQUEST","action":"ADD","headerName":"X-Tenant","headerValue":"acme"}]}'

# 3. activate it
curl -s -X PATCH http://localhost:8081/api/tenant/routes/12/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"active":true}'
```
