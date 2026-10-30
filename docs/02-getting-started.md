# 2. Getting Started

## 2.1 Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 | The three Spring Boot services |
| MySQL | 8.x | `api-management-service` (the only service that needs a database) |
| Node.js and npm | a current LTS | `gatekeeper-ui` |
| Chrome or Edge | any recent | Only for rebuilding the PDF |

Maven is not required: each service ships a Maven wrapper (`mvnw`, `mvnw.cmd`).

## 2.2 Database

`api-management-service` connects to `jdbc:mysql://localhost:3306/gatekeeper_manager` as configured in `api-management-service/src/main/resources/application.properties`.

1. Create an empty database named `gatekeeper_manager`. The JDBC URL does not create it for you.
2. Copy `api-management-service/.env.example` to `.env` and set `DB_URL` (a `jdbc:mysql://...` URL), `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET` (at least 32 random characters). `.env` is git-ignored and is read from the folder you start the service in. Real environment variables work too and take precedence.
3. Start the service from the `api-management-service` folder. Flyway runs automatically and applies:
   - `V1__create_api_management_schema.sql`: all tables
   - `V2__seed_management_roles.sql`: the three roles
   - `V3__seed_platform_accounts.sql`: the `platform-admin` and `gateway-service` accounts

Hibernate is set to `ddl-auto=validate`, so Flyway owns the schema and the application refuses to start if an entity does not match a table.

The seeded accounts' passwords are not documented here. They are stored only as BCrypt hashes in `V3__seed_platform_accounts.sql`; ask the team that created the migration, or insert your own hash.

## 2.3 Running the services

From each service folder:

```
cd api-management-service
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

| Service | Port | Check |
|---|---|---|
| api-management-service | 8081 | `GET http://localhost:8081/health` returns service and database status |
| smart-gateway-service | 8080 | `GET http://localhost:8080/health` |
| analytics-service | 8082 | `GET http://localhost:8082/health` |

Only `api-management-service` is needed to use the UI. The other two start without any external dependency.

## 2.4 Running the UI

```
cd gatekeeper-ui
npm install
npm run dev
```

Open http://localhost:3000. The dev server proxies every `/api` request to `http://localhost:8081`, so no CORS setup is needed. The port is strict: if 3000 is taken, Vite refuses to start rather than moving to another port.

| Script | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | `tsc -b` type check, then production bundle in `dist/` |
| `npm run lint` | oxlint |
| `npm run preview` | Serve the production bundle locally |

To point the UI at a backend that is not on `localhost:8081` (for example a production build served from a different host), set `VITE_API_BASE_URL` in `gatekeeper-ui/.env` (no trailing slash). Leave it empty in development to use the proxy. A production deployment must then either serve the UI and API from the same origin or add CORS to the backend, which does not have any CORS configuration today.

## 2.5 First-run walkthrough

1. Sign in as `platform-admin`. You land on **Organizations**.
2. **Create organization**: give it a name; the slug is suggested from the name. The organization is `PENDING`.
3. Open the organization. In the **Tenant admin** card, fill in the form (username, password of 8 to 100 characters).
4. Back on the organization, **Change status** to `ACTIVE`. A tenant administrator can only sign in while the organization is active.
5. Sign out, sign in as the new tenant administrator. You land on the **Tenant dashboard**.
6. Open **Routes**, **Create route**. The path pattern must begin with `/<your-slug>/`. New routes are inactive; **Activate** the route.
7. Sign out, sign in as `gateway-service`. **Active routes** now lists the route you activated.

## 2.6 Rebuilding the documentation

The PDF and the combined HTML are generated from the Markdown files in this folder.

```
cd docs/tools
npm install
npm run build:pdf
```

The script renders every `*.md` file in the documented order into one HTML document, then prints it to `docs/Gatekeeper-Documentation.pdf` with headless Chrome or Edge. Set the `CHROME_PATH` environment variable if neither is found in the default Windows install locations.

`docs/index.html` is hand-written and is not touched by the script. Edit it directly.
