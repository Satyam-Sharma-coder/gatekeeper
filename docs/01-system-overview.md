# 1. System Overview

## 1.1 What Gatekeeper is

Gatekeeper is designed as a multi-tenant API gateway. The design has two halves:

- **Control plane** (`api-management-service`): where administrators define tenants (organizations), their routes and header rules. It stores configuration, versions every change, and keeps an audit trail.
- **Data plane** (`smart-gateway-service`): the process that would receive client traffic and forward it to the right upstream service according to the routes the control plane publishes.

A third service, `analytics-service`, is meant to collect usage data.

**Only the control plane and the admin UI contain real logic today.** The gateway and analytics services start and answer `/health`, nothing more.

## 1.2 Components

| Component | Purpose | Port | Datastore | State |
|---|---|---|---|---|
| `api-management-service` | Control plane: login, tenants, routes, versions, audit | 8081 | MySQL `gatekeeper_manager` | Implemented |
| `smart-gateway-service` | Data plane: route traffic, rate limit, idempotency | 8080 | Redis (dependency only) | Scaffold |
| `analytics-service` | Usage analytics | 8082 | MongoDB (dependency only, auto-config excluded) | Scaffold |
| `gatekeeper-ui` | Admin console for all three roles | 3000 (dev) | none (token in `sessionStorage`) | Implemented |

Technology: Java 21, Spring Boot 4.1.2 (Spring MVC, Spring Data JPA, Flyway, Spring Security with OAuth2 resource server, Bean Validation, Lombok) for the services; React 19, TypeScript, Vite 8, Tailwind CSS 4 and react-router-dom 7 for the UI.

Java package root: `in.org.gatekeeper.<service_name_with_underscores>`, for example `in.org.gatekeeper.api_management_service`.

## 1.3 How the pieces connect

```
                         +------------------------------+
   Browser  ----------->  |  gatekeeper-ui  (React)      |
                         |  http://localhost:3000       |
                         +--------------+---------------+
                                        | /api/*  (Vite dev proxy)
                                        v
                         +------------------------------+        +------------------+
                         |  api-management-service      | -----> | MySQL            |
                         |  :8081  (control plane)      |  JPA   | gatekeeper_manager|
                         +--------------+---------------+  Flyway+------------------+
                                        ^
                  GET /api/gateway/routes (GATEWAY_SERVICE token)
                                        |  (intended; the gateway does not call it yet)
                         +--------------+---------------+
   API clients  ------->  |  smart-gateway-service       |  - - ->  upstream tenant APIs
                         |  :8080  (data plane, scaffold)|
                         +------------------------------+

                         +------------------------------+
                         |  analytics-service :8082     |   (scaffold, not connected)
                         +------------------------------+
```

Everything drawn with a real connection (UI to control plane, control plane to MySQL) works today. The gateway-to-control-plane link and the gateway-to-upstream link are the intended design: the endpoint the gateway should call exists, but the gateway does not call it.

## 1.4 The three roles

Every account has exactly one role. The role decides which part of the API and which part of the UI the account can use.

| Role | Who | Can use | UI area |
|---|---|---|---|
| `PLATFORM_ADMIN` | Operator of the whole platform | `/api/platform/**`: create and manage organizations and their tenant administrator | `/platform/...` |
| `TENANT_ADMIN` | One administrator per organization | `/api/tenant/**`: own organization, routes, version history, audit log | `/tenant/...` |
| `GATEWAY_SERVICE` | The data-plane service account | `/api/gateway/**`: read the list of active routes | `/gateway/...` |

Two accounts are created by database migration: `platform-admin` (PLATFORM_ADMIN) and `gateway-service` (GATEWAY_SERVICE). Tenant administrators are created by a platform admin through the API or the UI.

## 1.5 Core concepts

**Organization (tenant).** A customer of the platform. Has a unique `name` and a unique `slug` (lowercase letters, digits, single hyphens). Status is one of `PENDING`, `ACTIVE`, `SUSPENDED`, `DELETED`. New organizations start `PENDING`. Deleting is a soft delete: the row stays with status `DELETED`.

**Tenant administrator.** The single login account of an organization (the database enforces one per organization). Can only sign in while the organization is `ACTIVE`.

**Route.** A rule telling the gateway how to forward a request:

| Field | Meaning |
|---|---|
| `routeId` | Business identifier, unique inside an organization, cannot be changed after creation |
| `name` | Display name |
| `pathPattern` | Incoming path to match. Must start with `/<organization-slug>/` |
| `targetBaseUrl` | Upstream base URL, `http` or `https` |
| `targetPathPrefix` | Path prefix on the upstream, must start with `/` |
| `allowedMethods` | Set of HTTP methods the route accepts |
| `requestsPerMinute` | Optional rate limit (null means no limit) |
| `responseTimeoutMs` | Upstream timeout in milliseconds |
| `idempotencyEnabled` | Whether idempotency handling is on |
| `active` | Only active routes of active organizations are published to the gateway |
| `headerRules` | List of rules that add or remove headers on the request or the response |
| `configurationVersion` | Counter that increases on every change |

**Configuration version.** Every create, update, activate, deactivate and delete writes a snapshot (the full route as JSON) to `route_configuration_versions`. History is keyed by the business `routeId`, so it survives deleting the route.

**Audit record.** Every mutating action by the control plane writes who did it, what, on which resource, how long it took, the result and an optional `X-Correlation-ID` header value.

## 1.6 What a tenant sees versus what the gateway sees

- A tenant administrator sees **only their own** organization, routes, versions and audit records. The organization is taken from the `organizationId` claim in the JWT, never from the URL or the request body.
- The gateway account sees **all active routes of all `ACTIVE` organizations** in a single list, each tagged with its `organizationId`.
- Deactivating a route, suspending an organization or deleting an organization removes the affected routes from what the gateway receives on its next fetch.
