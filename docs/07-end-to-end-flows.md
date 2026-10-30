# 7. End-to-End Flows

Each flow lists what the user does, which API call the UI makes, and what the backend does. "Intended" outcomes are described in [08 - Known issues](08-known-issues-and-roadmap.md).

## 7.1 Onboarding a new tenant (platform admin)

| # | Actor | Action | API call | Backend effect |
|---|---|---|---|---|
| 1 | Platform admin | Signs in | `POST /api/auth/login` | JWT with role `PLATFORM_ADMIN`, no organization |
| 2 | Platform admin | Create organization (name, slug) | `POST /api/platform/organizations` | Row in `organizations`, status `PENDING`. Audit `CREATE_ORGANIZATION` |
| 3 | Platform admin | On the organization page, fills the **Tenant admin** form | `POST /api/platform/organizations/{id}/tenant-admin` | Row in `management_users` (role `TENANT_ADMIN`, BCrypt hash, linked to the organization). Audit `CREATE_TENANT_ADMIN` |
| 4 | Platform admin | Change status to `ACTIVE` | `PATCH /api/platform/organizations/{id}/status` | Status updated. Audit `CHANGE_ORGANIZATION_STATUS` |
| 5 | Tenant admin | Signs in | `POST /api/auth/login` | Allowed now because the organization is `ACTIVE`. JWT carries `organizationId` |

If step 5 happens before step 4, login is refused with "The organization is not active".

## 7.2 Creating and publishing a route (tenant admin)

```
Tenant admin            gatekeeper-ui                    api-management-service
     |  Create route         |                                     |
     |---------------------->|  validate form (same limits)        |
     |                       |  POST /api/tenant/routes ---------->|  TenantContext: org from JWT
     |                       |                                     |  validate prefix, URL, headers
     |                       |                                     |  reject duplicate routeId
     |                       |                                     |  save route (version 1, inactive)
     |                       |                                     |  save version CREATED (snapshot)
     |                       |                                     |  save audit CREATE_ROUTE
     |                       |<------------- 201 RouteResponse ----|
     |  Activate             |  PATCH /routes/{id}/status {true} ->|  version 2, ACTIVATED, audit
     |                       |<------------------- 200 -----------|
```

Validation performed by the backend on create and update:

1. `pathPattern` starts with `/<organization-slug>/`.
2. `targetPathPrefix` starts with `/`.
3. `targetBaseUrl` parses as a URL with scheme `http` or `https`.
4. Every header rule has direction `REQUEST`/`RESPONSE` and action `ADD`/`REMOVE`; `ADD` rules have a non-blank value.
5. The route id is not already used in the organization (create), or is unchanged (update).

The whole operation (route, versions, audit record) runs in one database transaction.

## 7.3 Changing a route and reading its history

1. Tenant opens `/tenant/routes/{id}` and chooses **Edit**. The form is pre-filled from `GET /api/tenant/routes/{id}`.
2. Saving sends `PUT /api/tenant/routes/{id}` with the full route. The backend increments the version, replaces the allowed methods and rebuilds the header rules, saves an `UPDATED` snapshot and an `UPDATE_ROUTE` audit record.
3. The detail page reloads. The **Version history** card refreshes because the route's `configurationVersion` changed, and lists versions newest first. **View snapshot** shows the full route as it was at that version, as formatted JSON.

To see what changed between two versions, open both snapshots and compare them. The UI does not compute diffs.

## 7.4 Deactivating and deleting a route

- **Deactivate:** `PATCH .../status {"active": false}`. Version +1 (`DEACTIVATED`). The route disappears from the gateway feed on its next fetch but remains fully editable.
- **Delete:** `DELETE /api/tenant/routes/{id}`. A final `DELETED` version with the last configuration is saved, an audit record `DELETE_ROUTE` is written, and the route (with its methods and header rules) is removed. The history stays available through `GET /api/tenant/route-versions/{routeId}` and the audit log.

## 7.5 How the gateway gets its configuration

```
smart-gateway-service (intended)               api-management-service
      |  POST /api/auth/login (gateway-service) ------->|
      |<----------------------------- JWT role GATEWAY_SERVICE
      |  GET /api/gateway/routes + Bearer ------------->|  routes WHERE active = true
      |<------------------- list of RouteResponse ------|        AND organization.status = ACTIVE
      |  build routing table, apply per-route rules     |
```

The endpoint and the account exist, and the UI's **Active routes** page shows exactly this feed. The gateway service does not call it yet. A route reaches the feed only when all three are true: the route is active, its organization is `ACTIVE`, and the route has not been deleted.

What the gateway is expected to do with each route (design intent, not implemented): match the incoming path against `pathPattern`, check `allowedMethods`, enforce `requestsPerMinute` and `idempotencyEnabled` (Redis), apply `headerRules`, forward to `targetBaseUrl + targetPathPrefix` with `responseTimeoutMs`, and publish usage events.

## 7.6 Suspending a tenant

| Step | Effect |
|---|---|
| Platform admin sets the organization to `SUSPENDED` | Audit `CHANGE_ORGANIZATION_STATUS` |
| Gateway fetches routes | The tenant's routes are no longer returned |
| Tenant admin with an existing token calls any tenant endpoint | Refused: "The organization is not active" |
| Tenant admin tries to sign in | Refused: "The organization is not active" |
| Platform admin sets the organization back to `ACTIVE` | Everything works again, no data was removed |

## 7.7 Deleting a tenant

`DELETE /api/platform/organizations/{id}` sets status `DELETED` and `deleted_at`. Nothing else is removed: routes, versions, audit records and the tenant administrator remain in the database. Because the organization is no longer `ACTIVE`, the tenant cannot sign in and the gateway stops receiving its routes. The organization disappears from the platform list and from `GET .../{id}`.

## 7.8 Session lifecycle in the UI

1. Login stores the session in `sessionStorage` and navigates to the role's home page.
2. A timer is set for the session expiry. A countdown is shown in the **Your session** card.
3. At expiry, or on any 401 from the API, the session is cleared and the login page shows "Your session has expired. Please sign in again."
4. **Sign out** clears the session immediately. Closing the tab also ends the session.
