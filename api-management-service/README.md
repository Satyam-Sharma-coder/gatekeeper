# api-management-service

The Gatekeeper control plane. Spring Boot 4.1, Java 21, Spring Data JPA, Flyway, Spring Security (JWT resource server), MySQL. Port **8081**.

## What it does

- `POST /api/auth/login`: issues HS256 JWTs.
- `/api/platform/**` (role `PLATFORM_ADMIN`): organizations (tenants) and their tenant administrator.
- `/api/tenant/**` (role `TENANT_ADMIN`): own organization, routes with header rules, version history, audit log. Tenant is taken from the JWT, never from the request.
- `/api/gateway/**` (role `GATEWAY_SERVICE`): feed of all active routes of active organizations.
- `GET /health`: service and database status.

Endpoint details: [docs/03-backend-api-reference.md](../docs/03-backend-api-reference.md).

## Run

1. Create the database: `CREATE DATABASE gatekeeper_manager;`
2. Create your settings file:
   ```bash
   cp .env.example .env     # edit DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
   ```
3. Start from **this folder** (the `.env` file is read from the working directory):
   ```bash
   ./mvnw spring-boot:run   # Windows: mvnw.cmd spring-boot:run
   ```

Flyway applies `db/migration/V1` to `V3` on first start (schema, roles, seeded `platform-admin` and `gateway-service` accounts). Hibernate only validates the schema.

In IntelliJ, set the run configuration's working directory to this folder, or define the four variables as environment variables there.

## Configuration

| Key | Source | Purpose |
|---|---|---|
| `DB_URL` | `.env` or environment | JDBC URL, for example `jdbc:mysql://localhost:3306/gatekeeper_manager` (must start with `jdbc:`) |
| `DB_USERNAME` | `.env` or environment | MySQL user |
| `DB_PASSWORD` | `.env` or environment | MySQL password |
| `JWT_SECRET` | `.env` or environment | HS256 signing key, at least 32 characters |
| `security.jwt.expiration-seconds` | `application.properties` | Token lifetime (3600) |

The application will not start without all four variables. The tests do not need them (they use H2 and `src/test/resources/application.properties`). A value that does not start with `jdbc:` fails with `'url' must start with "jdbc"`, and a placeholder written without the `$` (`{DB_URL}` instead of `${DB_URL}`) causes the same error.

## Build and test

```bash
./mvnw clean package
./mvnw test        # 379 tests, no database needed; see docs/09-testing.md
```
