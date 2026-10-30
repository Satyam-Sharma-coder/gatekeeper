# Gatekeeper

A multi-tenant API gateway platform. Administrators define tenants (organizations) and their routes in a **control plane**; a **data plane** gateway is meant to serve them. This repository is a monorepo holding every part.

| Folder | What it is | Port | Status |
|---|---|---|---|
| [`api-management-service`](api-management-service) | Control plane: login and JWT, tenants, routes, versions, audit log, gateway route feed (Spring Boot, MySQL) | 8081 | Implemented |
| [`gatekeeper-ui`](gatekeeper-ui) | Admin console for platform admins, tenant admins and the gateway account (React, TypeScript, Vite) | 3000 | Implemented |
| [`smart-gateway-service`](smart-gateway-service) | Data-plane gateway (Spring Boot) | 8080 | Scaffold, `/health` only |
| [`analytics-service`](analytics-service) | Usage analytics (Spring Boot) | 8082 | Scaffold, `/health` only |
| [`docs`](docs) | Full documentation, PDF and a visual overview (`docs/index.html`) | - | - |

## Quick start

Prerequisites: JDK 21, MySQL 8, Node.js (current LTS).

```bash
# 1. Database: create an empty schema (Flyway creates the tables on first start)
mysql -u root -p -e "CREATE DATABASE gatekeeper_manager"

# 2. Control plane
cd api-management-service
cp .env.example .env            # then edit .env (DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET)
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. Admin UI (new terminal)
cd gatekeeper-ui
npm install
npm run dev                     # http://localhost:3000
```

Sign in as `platform-admin` (seeded by migration `V3`; its password is stored only as a BCrypt hash). Create an organization and its tenant admin, activate the organization, then sign in as the tenant admin to manage routes. The full walkthrough is in [docs/02-getting-started.md](docs/02-getting-started.md).

## Documentation

Open [`docs/index.html`](docs/index.html) in a browser for the visual overview, or read [`docs/README.md`](docs/README.md). A single-file version is [`docs/Gatekeeper-Documentation.pdf`](docs/Gatekeeper-Documentation.pdf).

## Configuration and secrets

Secrets are never committed. `api-management-service` reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET` from a local `.env` file (git-ignored) or from environment variables. Copy `.env.example` to `.env` and fill it in. `gatekeeper-ui` has an optional `.env` too (see its README).

## Repository layout

```
gatekeeper/
  api-management-service/   Spring Boot control plane
  smart-gateway-service/    Spring Boot data plane (scaffold)
  analytics-service/        Spring Boot analytics (scaffold)
  gatekeeper-ui/            React admin console
  docs/                     Markdown docs, PDF, index.html, PDF build tool
```
