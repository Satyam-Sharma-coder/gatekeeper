# 9. Testing

## 9.1 Running the tests

```
cd api-management-service
./mvnw test              # Windows: mvnw.cmd test

./mvnw test -Dtest=RouteServiceTest        # one class
./mvnw test -Dtest='*RepositoryTest'       # by pattern
```

The same command works in `smart-gateway-service` and `analytics-service`.

No MySQL, Kafka, Redis or MongoDB is needed. Tests use an in-memory H2 database and mocks. The first run needs the Maven dependencies; add `-o` to work offline once they are cached.

| Service | Tests | Result at time of writing |
|---|---|---|
| `api-management-service` | 379 | all passing |
| `smart-gateway-service` | 7 | all passing |
| `analytics-service` | 7 | all passing |

## 9.2 Test layers in api-management-service

All files are under `src/test/java/in/org/gatekeeper/api_management_service/`.

| Layer | Package and classes | Style | What it proves |
|---|---|---|---|
| Service unit tests | `service/`: `RouteServiceTest`, `OrganizationServiceTest`, `AuthenticationServiceTest`, `AuditServiceTest`, `TenantContextTest`, `HealthServiceTest` | JUnit 5 + Mockito, no Spring | Business rules: validation, version numbers, audit actions, status changes, conflicts, tenant resolution |
| DTO tests | `dto/`: `RequestValidationTest`, `ResponseMappingTest` | plain JUnit with a real Bean Validation validator | Field limits and patterns on every request; response mapping; password hash never exposed |
| Repository tests | `repository/`: Organization, ManagementUser (and Role), ApiRoute, Version and Audit | `@DataJpaTest` on H2 | Derived queries, tenant scoping, ordering, cascade and orphan removal, gateway feed query |
| Controller tests | `controller/`: tenant routes, platform organizations, tenant organization, gateway, authentication and health | `@WebMvcTest` + MockMvc, services mocked | URLs, status codes, JSON shapes, request validation (400), 401 and 403 for every endpoint and role |
| Security tests | `config/SecurityConfigTest` | `@WebMvcTest` with real JWT encoder and decoder | Role claim mapping, expired, forged and tampered tokens, public endpoints, CSRF and session behavior, BCrypt |
| Integration tests | `integration/ControlPlaneFlowIntegrationTest` | `@SpringBootTest` + MockMvc + H2, real security and services | Whole flows: onboarding, route lifecycle with versions and feed, audit log, tenant isolation, suspension and deletion effects |

Shared helpers live in `support/`: `TestData` (entity and request builders), `Roles` (MockMvc JWT post-processors), `ApiExceptionAssertions`.

## 9.3 Test configuration

`src/test/resources/application.properties` replaces the main file during tests:

- H2 in-memory database in MySQL compatibility mode.
- `spring.jpa.hibernate.ddl-auto=create-drop` and `spring.flyway.enabled=false`: the schema is generated from the entities, so the Flyway SQL migrations are **not** exercised by these tests.
- A fixed test-only JWT secret.

The original `contextLoads` test now also runs against H2, so it no longer needs MySQL or the `.env` file.

## 9.4 What the integration tests cover

| Scenario | Checks |
|---|---|
| Onboarding | Tenant cannot sign in while the organization is `PENDING`; can once `ACTIVE`; token carries role and organization; duplicate name or slug gives 409; `DELETED` cannot be set through PATCH; one admin per organization; password change, disable and delete affect login; wrong password and unknown user give identical 401 |
| Route lifecycle | Create (version 1, inactive), activate, update (header rules replaced), deactivate, delete; versions 5 to 1 with change types and readable snapshots; gateway feed changes at each step; route survives in history after deletion |
| Audit | Actions, actors, `X-Correlation-ID`, platform actions visible to the tenant, all results `SUCCESS` |
| Validation | Wrong path prefix, non-http URL, `ADD` rule without value, empty body: all 400 with nothing stored; route ID cannot change on update; duplicate route ID gives 409 |
| Tenant isolation | Another tenant gets 404 on read, update, status change and delete; empty version history; same route ID allowed in two organizations; cannot create routes under another tenant's prefix; separate audit logs; merged gateway feed |
| Organization lifecycle | Suspended: gateway feed empties, existing tenant token gets 403, login refused, all restored on reactivation. Deleted: hidden from the platform, locked out, rows kept. `PENDING`: routes not published |
| Role boundaries | Every role is refused in the other two areas (403); anonymous is refused (401); `/health` is public and reports the real database as up |

## 9.5 Handling the missing error handler

The application has no `@ControllerAdvice` (see [08 - Known issues](08-known-issues-and-roadmap.md)), so `ApiException` escapes the controller. MockMvc then throws it from `perform(...)` instead of returning a status. The integration tests use a small `call(...)` helper that accepts both outcomes: it returns the HTTP status when a response exists, or the `ApiException` status when the exception escapes. The assertions therefore describe the intended behavior and keep passing after a global handler is added.

The controller tests avoid the issue by mocking the services, and the service tests assert on `ApiException` and its status directly.

## 9.6 Bug found by these tests

`ApiRoute` was given Lombok `@Builder` and `RouteService.create` started using `ApiRoute.builder()`. Lombok builders ignore field initializers, so `headerRules` and `allowedMethods` were `null` and every route creation failed with a `NullPointerException`. Fixed by marking both fields `@Builder.Default`. Regression tests: `RouteServiceTest` (create) and `ApiRouteRepositoryTest.builderCreatedRouteStartsWithEmptyRuleAndMethodCollections`.

## 9.7 Not covered

- Flyway migrations (`V1` to `V3`) against a real MySQL. Consider adding Testcontainers with MySQL to catch dialect or constraint differences. Note that the unique constraint on `(organization_id, route_id, version_number)` exists only in the Flyway schema, so the "re-create a deleted route ID" problem in [08 - Known issues](08-known-issues-and-roadmap.md) is not reproducible on H2.
- Code coverage is not measured; no JaCoCo plugin is configured.
- The gateway proxy, Kafka, Redis and MongoDB paths do not exist yet, so there is nothing to test. `smart-gateway-service` and `analytics-service` have health and access-rule tests only.
- The React UI has no tests and no test runner.
