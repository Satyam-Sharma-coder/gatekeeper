# Gatekeeper Documentation

Gatekeeper is a multi-tenant API gateway platform made of three Spring Boot services and a React admin UI. This folder explains what exists **today**, how the pieces fit together, and how a request or a configuration change flows through the system.

> Last verified against the source code on 2026-10-29. Secrets (database password, JWT signing secret, seeded password hashes) are intentionally not reproduced anywhere in this documentation.

## Start here

| If you want to... | Read |
|---|---|
| See the whole picture in one visual page | [index.html](index.html) (open it in a browser) |
| Read everything as a single file | [Gatekeeper-Documentation.pdf](Gatekeeper-Documentation.pdf) |
| Understand what each service does | [01 - System overview](01-system-overview.md) |
| Run the project locally | [02 - Getting started](02-getting-started.md) |
| Call the backend | [03 - Backend API reference](03-backend-api-reference.md) |
| Understand tables, entities and versioning | [04 - Data model](04-data-model.md) |
| Understand login, JWT, roles and tenant isolation | [05 - Security and authentication](05-security-and-auth.md) |
| Work on the React UI | [06 - Frontend](06-frontend.md) |
| Follow a scenario step by step | [07 - End-to-end flows](07-end-to-end-flows.md) |
| Know what is missing or risky | [08 - Known issues and roadmap](08-known-issues-and-roadmap.md) |
| Run or extend the tests | [09 - Testing](09-testing.md) |

`architecture.md` in this folder is an earlier machine-generated snapshot. It predates the route-management work and the finished UI, so where it disagrees with the files above, the files above are correct.

## Status at a glance

| Area | Status |
|---|---|
| Login and JWT issuing | Implemented |
| Platform admin: organizations (tenants) and tenant administrators | Implemented |
| Tenant admin: own organization, routes, header rules, version history, audit log | Implemented |
| Gateway-facing endpoint listing active routes | Implemented |
| Admin UI for the platform, tenant and gateway roles | Implemented (users and gateway registration pages are placeholders) |
| smart-gateway-service (the actual traffic proxy) | Scaffold: `/health` only |
| analytics-service | Scaffold: `/health` only |
| Kafka, Redis, MongoDB usage | Dependencies only, no code |
| Tenant user management, gateway registration | Not available in the backend |

## Regenerating the PDF

The PDF is built from the Markdown files in this folder.

```
cd docs/tools
npm install
npm run build:pdf
```

This needs Google Chrome or Microsoft Edge installed. See [02 - Getting started](02-getting-started.md), section 2.6.
