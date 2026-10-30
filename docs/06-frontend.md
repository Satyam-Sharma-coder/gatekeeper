# 6. Frontend (gatekeeper-ui)

## 6.1 Stack

React 19, TypeScript (strict mode), Vite 8, Tailwind CSS 4 (imported in `src/index.css`), react-router-dom 7 (data router with lazy-loaded pages), oxlint. There is no state-management or data-fetching library: plain hooks and a small `fetch` wrapper.

## 6.2 Screens and routes

| URL | Role | Screen | Backend used |
|---|---|---|---|
| `/login` | guest | Sign in | `POST /api/auth/login` |
| `/platform/organizations` | PLATFORM_ADMIN | Organization list with search, status filter, sort; create, edit, change status, delete | `/api/platform/organizations` |
| `/platform/organizations/:id` | PLATFORM_ADMIN | Organization detail plus the **Tenant admin** card (create, edit, delete) | organizations and `.../tenant-admin` |
| `/tenant` | TENANT_ADMIN | Dashboard: organization summary, route counts, five most recent audit entries, session card | `/api/tenant/organization`, `/routes`, `/audit-records` |
| `/tenant/routes` | TENANT_ADMIN | Route list: search, status filter, sort; create, edit, activate or deactivate, delete | `/api/tenant/routes` |
| `/tenant/routes/:id` | TENANT_ADMIN | Route detail: all settings, header rules table, version history with snapshot viewer | `/routes/{id}`, `/route-versions/{routeId}` |
| `/tenant/audit` | TENANT_ADMIN | Audit log: search by actor, resource or details; filter by action | `/api/tenant/audit-records` |
| `/tenant/organization` | TENANT_ADMIN | Organization details and rename | `/api/tenant/organization` |
| `/tenant/users` | TENANT_ADMIN | Placeholder ("Coming soon") | none: no backend endpoint |
| `/gateway` | GATEWAY_SERVICE | Session information | none |
| `/gateway/routes` | GATEWAY_SERVICE | Read-only list of active routes across tenants | `/api/gateway/routes` |
| `/gateway/registration` | GATEWAY_SERVICE | Placeholder ("Coming soon") | none: no backend endpoint |
| `*` | any | Not found | none |

## 6.3 Folder structure

The components folder is spelled `componenets` in the repository. That spelling is kept; imports must use it.

```
gatekeeper-ui/src
  main.tsx, App.tsx          providers: Toast > Auth > Router
  index.css                  Tailwind import and base styles
  api/
    client.ts                fetch wrapper, ApiError, error-message extraction
    types.ts                 TypeScript mirrors of backend DTOs and enums
    auth.ts                  login
    organizations.ts         platform organization calls
    tenantAdmin.ts           platform tenant-admin calls
    tenantOrganization.ts    tenant: own organization
    routes.ts                tenant: routes, versions, audit records
    gateway.ts               gateway: active routes
  auth/                      AuthProvider, session storage, JWT parsing, role labels and homes
  routes/                    router.tsx (route table, nav menus), ProtectedRoute.tsx
  hooks/                     useAsyncData, useCountdown, useDocumentTitle
  lib/                       format.ts (dates), validation.ts (form rules), dom.ts, guards.ts
  componenets/
    ui/                      Button, Modal, ConfirmDialog, Card, TextField, SelectField,
                             CheckboxField, Banner, EmptyState, StatusBadge, Spinner, Toasts
    layout/AppShell.tsx      sidebar, header, mobile menu, skip link
    organizations/           organization form, status and delete dialogs
    tenant-admin/            tenant admin form and panel
    routes/                  RouteFormModal, RouteStatusDialog, DeleteRouteDialog, RouteVersionsPanel
    session/SessionCard.tsx
  pages/
    LoginPage.tsx, NotFoundPage.tsx
    platform/                OrganizationsPage, OrganizationDetailPage
    tenant/                  Dashboard, Routes, RouteDetail, Audit, Organization, Users
    gateway/                 GatewayStatus, GatewayRoutes, GatewayRegistration
```

## 6.4 How data flows through the UI

```
Page component
   |  useAsyncData(loader)            loading / error / data / reload()
   v
api/<module>.ts   typed function, e.g. listRoutes(signal)
   v
api/client.ts     adds Authorization header from sessionStorage
   |              aborts on unmount (AbortSignal)
   |              401  -> clear session, back to /login
   |              !ok  -> ApiError(status, friendly message)
   v
fetch  /api/...   (Vite proxy to :8081 in development)
```

Key behaviors:

- **`useAsyncData`** runs a loader on mount and when the loader function changes, cancels the previous request, and exposes `reload()`. Pages wrap loaders in `useCallback` when they depend on a route parameter.
- **Mutations** are done inside dialogs. Each dialog owns its submitting and error state, shows server errors inside the dialog, and on success calls back to the page, which shows a toast, closes the dialog and reloads the list.
- **Error messages** come from the response body (`message`, `detail`, `error` or the first entry of `errors`) when it is specific; otherwise from a per-call message (for example 409 on route creation) or a status default. Generic server phrases such as "Internal Server Error" are ignored in favour of the friendly text.
- **Forms** validate on submit with the same limits as the backend (`lib/validation.ts`), show field errors, and focus the first invalid field.

## 6.5 The route form

`RouteFormModal` is used for both create and edit.

| Behavior | Detail |
|---|---|
| Path pattern | Pre-filled with `/<slug>/` when the organization slug is known; the client checks the prefix, the server checks it again |
| Route ID | Editable on create, read-only on edit (the backend forbids changing it) |
| Defaults | Methods: `GET`; timeout 5000 ms; rate limit empty (no limit); idempotency off; **active off** |
| Methods | Checkboxes for GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS; at least one required |
| Header rules | Add and remove rows. Each row has direction, action, header name and, for `ADD`, a required value. `REMOVE` rows send `headerValue: null` |
| Saving | Create calls POST; edit calls PUT with the full route; the new version appears in the detail page history |

Activating and deactivating is a separate confirmed action (`PATCH .../status`), also available from the list and the detail page.

## 6.6 Layout and accessibility

- `AppShell` provides a fixed sidebar on wide screens and a toggled menu on small screens, with a "Skip to content" link. The menu items differ per role (`PLATFORM_NAV`, `TENANT_NAV`, `GATEWAY_NAV` in `routes/router.tsx`).
- Modals trap focus, close on Escape and restore focus; they cannot be dismissed while a request is in flight.
- Tables have captions and scoped headers; row actions have visually hidden names ("Edit Orders API").
- Result counts are announced through `aria-live`. Reduced-motion preferences are respected.

## 6.7 Adding a new screen

1. Add the DTO types to `api/types.ts` and a function to the right `api/*.ts` module.
2. Add field rules to `lib/validation.ts` if there is a form.
3. Build the page in `pages/<area>/` with a default export, using `useAsyncData`, `PageHeader`, `Card`, `EmptyState`, `Banner` and the dialog pattern from an existing page.
4. Register it in `routes/router.tsx` (lazy `page(import(...))`) under the right `ProtectedRoute`, and add a nav entry if it should appear in the sidebar.
5. Run `npm run build` and `npm run lint`.
