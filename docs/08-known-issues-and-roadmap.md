# 8. Known Issues and Roadmap

Items are marked **Verified** when confirmed by reading the code or schema, and **Inferred** when they follow from the code but were not executed. Severity is a suggestion.

## 8.1 Known issues

### 1. Business errors are not translated to HTTP statuses
**Verified:** there is no `@ControllerAdvice`, `@ExceptionHandler` or `ResponseStatusException` in the repository. **Inferred:** `ApiException` (401, 403, 404, 409, 400 for business rules) therefore surfaces as a generic 500 instead of the intended status and message. Effects: wrong credentials, "organization not active", duplicate route id and "route not found" are all reported as server errors; the UI falls back to its own per-call messages for some statuses but cannot tell these cases apart when it receives 500. Bean-validation failures do return 400. **Severity: high.** Fix: add a `@RestControllerAdvice` mapping `ApiException` to `ResponseEntity` with the exception's status and a `{message}` body.

### 2. Re-creating a deleted route fails
**Verified:** `route_configuration_versions` is unique on `(organization_id, route_id, version_number)` and survives route deletion by design; a new route with the same `routeId` starts at version 1 and writes a `CREATED` version 1 again. **Inferred:** the second create fails with a constraint violation. Workaround: use a different route id. Fix: derive the next version from the highest stored version for that route id.

### 3. Gateway and analytics services cannot validate tokens
**Verified:** their `SecurityConfig` has no `oauth2ResourceServer` and no `JwtDecoder` or `security.jwt.*` property exists. Any endpoint added there would reject valid tokens. **Severity: high** before adding features.

### 4. Seeded accounts and early secrets
**Verified:** the database password and JWT secret now come from `DB_PASSWORD` and `JWT_SECRET` (git-ignored `.env` or environment). The seeded `platform-admin` and `gateway-service` password hashes are still committed in `V3`. Change those passwords after first start, and use a fresh `JWT_SECRET` for any shared deployment because the earlier value was stored in plain text during development. **Severity: medium.**

### 5. Tokens cannot be revoked
No refresh, logout or revocation. A disabled user keeps a working token until it expires (tenant data access is still blocked if the organization stops being active). Lifetime is one hour.

### 6. Shared symmetric signing key
HS256 means any service that verifies tokens holds the key that can also forge them. Consider RS256/ES256 with a published public key once other services validate tokens.

### 7. Allowed-method validation is weak
**Inferred:** the service checks methods with Spring's `HttpMethod.valueOf`, which does not reject unknown names in current Spring versions, so a request can save a non-standard method. The UI only offers the seven standard methods.

### 8. Platform actions appear in the tenant's audit log
**Verified:** platform operations are recorded against the affected organization, so tenants see entries written by platform administrators (actor shown). This may be desirable (transparency) but is not documented as a decision.

### 9. Audit records only success
**Verified:** `AuditService.record` always writes `result = SUCCESS`, and only after the operation succeeds. Failures are not audited. The audit list is not paginated.

### 10. No pagination anywhere
Organizations, routes, versions, audit records and the gateway feed return full lists. Audit records and versions grow without bound.

### 11. Organization status is unrestricted
Any of `PENDING`, `ACTIVE`, `SUSPENDED` can be set from any other; there is no state machine or reason field.

### 12. Tenant administrator update requires a password
`PUT .../tenant-admin` needs a password every time; there is no partial update or dedicated password-change call.

### 13. One tenant administrator per organization
Enforced by a unique constraint. Tenant user management (the **Users** page) is a placeholder because the backend has no endpoints for it.

### 14. Inconsistent health responses and mislabelled config
The three `/health` responses use different field names, and `info.app.*` in `api-management-service` and `analytics-service` is copied from the gateway ("Custom API Gateway for routing, security and processing"). `HealthService` swallows database errors with `System.out.println` rather than a logger.

### 15. Tests
Each service has only the generated `contextLoads` test, which needs a reachable MySQL and the committed configuration. The UI has no test runner. There are no automated tests for route management, tenant isolation or the UI.

### 16. Repository hygiene
The workspace has no `.git` directory, no CI configuration, no Docker or compose file, and no root build file. The UI components folder is spelled `componenets`.

## 8.2 Roadmap suggestions

Ordered by dependency, not by effort.

1. **Fix error handling** (issue 1) and add controller tests for the tenant endpoints. This unblocks meaningful UI error messages.
2. **Fix route re-creation** (issue 2) and add a test for create, delete, create.
3. **Externalize secrets** (issue 4).
4. **Implement the data plane.** Gateway logs in as `gateway-service`, polls `GET /api/gateway/routes`, builds a routing table, proxies with `RestClient`, applies header rules, rate limits and idempotency with Redis. Requires JWT validation (issue 3).
5. **Add events and analytics.** Define Kafka topics for request outcomes from the gateway, consume them in `analytics-service`, store in MongoDB, expose query endpoints and add an analytics page to the UI.
6. **Tenant user management and gateway registration** endpoints, then replace the two UI placeholders.
7. **Operational quality:** pagination, audit of failures, token revocation or shorter lifetimes with refresh, CI pipeline, container images, UI tests.

## 8.3 Documentation maintenance

When the code changes, update the matching file: endpoints in `03`, tables in `04`, screens in `06`, scenarios in `07`, and the status table in `README.md`. Rebuild the PDF afterwards (`docs/tools`, see [02 - Getting started](02-getting-started.md)). `docs/index.html` is edited by hand.
