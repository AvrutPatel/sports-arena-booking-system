# Production readiness changes

## 1. Auth-service authentication boundary

Problem: the previous `/api/auth/**` public matcher also matched profile and
wallet endpoints. A controller calling `SecurityContextHolder` does not itself
require authentication.

Approach:

- Allow anonymous POST requests only to `/api/auth/login` and `/api/auth/register`.
- Allow OPTIONS requests for the existing gateway-managed preflight flow.
- Require authentication for all other requests, including future auth routes.
- Use stateless JWT authentication; reject missing credentials on protected
  routes and malformed, expired, or incorrectly signed bearer tokens with 401.
- Return 403 for authenticated callers denied by method-level authorization.

Why this approach:

- Service-level rules protect requests that bypass the gateway.
- A small public allowlist avoids accidentally exposing future endpoints.
- Filter-chain enforcement covers controllers centrally instead of relying on
  each controller to remember an authentication check.
- Stateless sessions match the existing bearer-token client and prevent session
  authentication from implicitly replacing a token on subsequent requests.

Trade-offs and remaining work:

- Invalid bearer tokens are rejected even on public routes. Clients should omit
  an expired token when logging in again.
- CORS remains managed as before; OPTIONS access does not configure allowed
  origins or provide CORS response headers.
- Financial operation validation, service credentials, atomic accounting, and
  idempotency are implemented in the next change. See [wallet changes](wallet-changes.md)
  for the coordinated auth/booking rollout, migration steps, and remaining gaps.
- This change does not address booking concurrency or payment recovery.

Verification:

```powershell
cd auth-service
mvn -Dtest=AuthenticationBoundaryTests test
```

The request-level tests use the actual security filter chain and JWT parser with
test-only signing keys. Business services and repositories are mocked, so the
suite requires neither PostgreSQL nor service discovery. It covers public route
validation, protected route rejection, token validity, stateless requests,
customer wallet-call compatibility, and owner-role authorization.
