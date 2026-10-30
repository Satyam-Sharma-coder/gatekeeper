# 5. Security and Authentication

Authentication and authorization are fully implemented in `api-management-service` only. The gateway and analytics services have a security configuration but no token validation (see [08 - Known issues](08-known-issues-and-roadmap.md)).

## 5.1 Sign-in flow

```
Client                       api-management-service                 MySQL
  |  POST /api/auth/login          |                                  |
  |  {username, password} -------> |  find user by username --------> |
  |                                |  <-------- user + role + org     |
  |                                |  reject if disabled              |
  |                                |  BCrypt compare password         |
  |                                |  reject if org exists and        |
  |                                |     org status is not ACTIVE     |
  |                                |  sign JWT (HS256)                |
  | <-- {token, role, orgId, ...}  |                                  |
  |  GET /api/... + Bearer token   |                                  |
  |  -----------------------------> verify signature + expiry,        |
  |                                |  authority = ROLE_<role claim>   |
  | <-- 200 / 401 / 403            |                                  |
```

Rules enforced at login:

1. Unknown user, disabled user and wrong password all produce the same "Invalid username or password" (intended 401), so the response does not reveal which accounts exist.
2. A user that belongs to an organization can sign in only while that organization is `ACTIVE`; otherwise "The organization is not active" (intended 403).
3. Platform and gateway accounts have no organization and skip rule 2.

## 5.2 The token

| Property | Value |
|---|---|
| Algorithm | HS256 (HMAC with a shared secret from `security.jwt.secret`) |
| Lifetime | `security.jwt.expiration-seconds`, currently 3600 (one hour) |
| `iss` | `gatekeeper-api-management` |
| `sub` | the username |
| `iat`, `exp` | issue and expiry time |
| `role` | `PLATFORM_ADMIN`, `TENANT_ADMIN` or `GATEWAY_SERVICE` |
| `organizationId` | present only for users that belong to an organization |

There is no refresh token, no logout endpoint and no revocation list. A token stays valid until it expires, even if the user is later disabled or the organization is suspended. (Suspending an organization does, however, block tenant data access immediately, see 5.4.)

## 5.3 Authorization

`SecurityConfig` maps URL prefixes to roles (table in [03 - Backend API reference](03-backend-api-reference.md#31-conventions)). The `role` claim becomes the authority `ROLE_<role>`, so `hasRole("TENANT_ADMIN")` matches a token whose claim is `TENANT_ADMIN`. There is no method-level security.

The service is stateless (no HTTP session) and CSRF protection is disabled, which is appropriate for a bearer-token API that does not use cookies.

## 5.4 Tenant isolation

Every tenant endpoint calls `TenantContext.getOrganization()`, which:

1. Reads the `organizationId` claim from the authenticated JWT. Missing means the request is refused ("Tenant identity is missing").
2. Loads the organization from the database. Missing means refused ("Organization does not exist").
3. Requires its status to be `ACTIVE`. Otherwise refused ("The organization is not active").

The organization id never comes from the URL, query string or request body, so a tenant cannot address another tenant's data by changing an id. Lookups by id also include the organization id (`findByIdAndOrganizationId`), so a foreign route id behaves as "not found".

Because step 3 runs on every request, suspending or deleting an organization takes effect immediately for tenant endpoints even though the user's token has not expired.

## 5.5 Password handling

Passwords are hashed with BCrypt before storage. Responses never include hashes. Creating or updating a tenant administrator requires a password of 8 to 100 characters, and an update always requires a new password.

## 5.6 How the UI handles the session

| Concern | Behavior |
|---|---|
| Storage | The session (token, username, role, organization id, issue and expiry times) is kept in `sessionStorage` under `gatekeeper.session`. It disappears when the browser tab closes |
| Expiry | The session expires at the earlier of the response's `expiresInSeconds` and the token's `exp` claim. A timer signs the user out then with a "session expired" notice. A stored session that has already expired is discarded on load |
| 401 from the API | Any 401 on an authenticated call clears the session and returns to the login page |
| Route protection | `ProtectedRoute` allows only the listed roles. A signed-in user who opens another role's area is redirected to their own home. `GuestRoute` keeps signed-in users away from `/login` |
| Role homes | `PLATFORM_ADMIN` goes to `/platform/organizations`, `TENANT_ADMIN` to `/tenant`, `GATEWAY_SERVICE` to `/gateway` |
| Token display | The token is never rendered |

Client-side route protection is a convenience only. The server enforces every permission.

## 5.7 Security checklist for deployment

- The database password and JWT secret are read from `DB_PASSWORD` and `JWT_SECRET` (a git-ignored `.env` file locally, real environment variables or a secret store in deployment). Use a fresh, long random `JWT_SECRET` outside local development; the value used during early development should be treated as exposed.
- Change the seeded `platform-admin` and `gateway-service` passwords.
- Serve the UI and API over HTTPS.
- Decide on CORS if the UI will be served from a different origin than the API; none is configured.
- Wire JWT validation into the gateway and analytics services before adding endpoints there. Both would need the shared HS256 secret, or the design should move to asymmetric keys.
