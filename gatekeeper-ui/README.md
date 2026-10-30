# gatekeeper-ui

The Gatekeeper admin console. React 19, TypeScript, Vite 8, Tailwind CSS 4, react-router-dom 7. Dev port **3000**.

One app serves three roles. After sign-in each role lands in its own area:

| Role | Area | Main screens |
|---|---|---|
| Platform admin | `/platform` | Organizations, organization detail with tenant admin |
| Tenant admin | `/tenant` | Dashboard, routes (create, edit, activate, delete), route detail with version history, audit log, organization |
| Gateway service | `/gateway` | Active routes feed, session |

Screens and architecture: [docs/06-frontend.md](../docs/06-frontend.md).

## Run

Requires `api-management-service` running on port 8081.

```bash
npm install
npm run dev          # http://localhost:3000
```

The dev server proxies `/api` to `http://localhost:8081`, so no CORS setup is needed.

## Configuration

Optional. Copy `.env.example` to `.env` to override:

| Variable | Meaning |
|---|---|
| `VITE_API_BASE_URL` | Base URL of the backend, no trailing slash. Leave empty in development to use the proxy. Set it for a production build served from a different origin (the backend would then need CORS, which is not configured yet) |

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type check (`tsc -b`) and production bundle in `dist/` |
| `npm run lint` | oxlint |
| `npm run preview` | Serve the production bundle |

## Conventions

- Strict TypeScript, `import type` for types, file extensions in imports.
- The components folder is spelled `componenets`; keep that spelling in imports.
- Data loading uses `useAsyncData`; API calls live in `src/api/`.
