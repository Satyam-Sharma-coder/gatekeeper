# 4. Data Model

Only `api-management-service` has a database (MySQL, schema `gatekeeper_manager`). Flyway owns the schema; Hibernate only validates it (`ddl-auto=validate`). Every table is mapped by a JPA entity.

## 4.1 Entity relationship overview

```
  roles 1 ----< management_users >---- 0..1 organizations
                                             | 1
                    +------------------------+-------------------------+
                    |                        |                         |
                    v *                      v *                       v *
               api_routes       route_configuration_versions   control_plane_audit_records
                 | 1                (history, keyed by
        +--------+--------+          organization + routeId)
        v *               v *
 route_allowed_methods  route_header_rules
```

`route_configuration_versions` deliberately has **no foreign key to `api_routes`**: it refers to the route by its business `route_id`, so history stays readable after the route is deleted.

## 4.2 Tables

### organizations  (entity `Organization`)
| Column | Notes |
|---|---|
| `id` | primary key, auto-increment |
| `name` | unique |
| `slug` | unique; becomes the mandatory first segment of every route `path_pattern` |
| `status` | `PENDING`, `ACTIVE`, `SUSPENDED`, `DELETED` (stored as text) |
| `created_at`, `updated_at`, `deleted_at` | `deleted_at` is set on soft delete |

### roles  (entity `Role`)
`id`, `name` (unique). Seeded: `PLATFORM_ADMIN`, `TENANT_ADMIN`, `GATEWAY_SERVICE`.

### management_users  (entity `ManagementUser`)
| Column | Notes |
|---|---|
| `id`, `username` (unique) | |
| `password_hash` | BCrypt |
| `enabled` | disabled users cannot sign in |
| `organization_id` | nullable and **unique**: at most one user per organization; `NULL` for platform and gateway accounts |
| `role_id` | required |
| `created_at`, `updated_at` | |

### api_routes  (entity `ApiRoute`)
| Column | Notes |
|---|---|
| `id` | primary key |
| `organization_id` | owner; `(organization_id, route_id)` is unique |
| `route_id`, `name`, `path_pattern`, `target_base_url`, `target_path_prefix` | see [01 - Core concepts](01-system-overview.md#15-core-concepts) |
| `requests_per_minute` | nullable |
| `response_timeout_ms` | required |
| `idempotency_enabled`, `active` | both default false |
| `configuration_version` | starts at 1, +1 on every change |
| `created_at`, `updated_at` | |

### route_allowed_methods  (element collection of `ApiRoute`)
Primary key `(route_pk, http_method)`. Deleted with the route (`ON DELETE CASCADE`). Loaded eagerly.

### route_header_rules  (entity `RouteHeaderRule`)
`id`, `route_pk` (cascade delete), `direction` (`REQUEST`/`RESPONSE`), `action` (`ADD`/`REMOVE`), `header_name`, `header_value` (nullable), timestamps. On every route update the rule list is cleared and rebuilt from the request (`orphanRemoval`), so rule ids change on each save.

### route_configuration_versions  (entity `RouteConfigurationVersion`)
| Column | Notes |
|---|---|
| `organization_id`, `route_id` | which route |
| `version_number` | unique per `(organization_id, route_id)` |
| `change_type` | `CREATED`, `UPDATED`, `ACTIVATED`, `DEACTIVATED`, `DELETED` |
| `configuration_snapshot` | `LONGTEXT`: the complete `RouteResponse` as JSON at that moment |
| `created_at` | |

### control_plane_audit_records  (entity `ControlPlaneAuditRecord`)
`organization_id` (nullable), `actor` (username), `action`, `resource_type`, `resource_id`, `correlation_id` (from `X-Correlation-ID`), `duration_ms`, `result`, `details`, `created_at`.

## 4.3 Seed data

| Migration | Content |
|---|---|
| `V1__create_api_management_schema.sql` | All tables above |
| `V2__seed_management_roles.sql` | The three roles |
| `V3__seed_platform_accounts.sql` | `platform-admin` and `gateway-service`, both with `organization_id = NULL` |

## 4.4 Versioning rules

| Action | `configuration_version` | Version row written |
|---|---|---|
| Create route | set to 1 | `CREATED` |
| Update route (PUT) | +1 | `UPDATED` |
| Activate (PATCH status, true) | +1 | `ACTIVATED` |
| Deactivate (PATCH status, false) | +1 | `DEACTIVATED` |
| Delete route | +1 | `DELETED` (final snapshot), then the route row is removed |

Version numbers therefore never repeat for a route, and the highest number always matches `configuration_version` on the live route (or the final `DELETED` version for a removed route). Re-creating a route with the same `routeId` after deleting it hits the unique `(organization, route, version)` constraint, because the new route starts again at version 1; see [08 - Known issues](08-known-issues-and-roadmap.md).

## 4.5 Tenant isolation in queries

Route, version and audit queries always include the organization id taken from the JWT (`findByIdAndOrganizationId`, `findAllByOrganizationId`, `findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc`, `findAllByOrganizationIdOrderByCreatedAtDesc`). The only cross-tenant query is `findAllByActiveTrueAndOrganizationStatus(ACTIVE)`, used by the gateway endpoint.
