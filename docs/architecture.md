# Architecture

oidc-service-reference is a commerce backend with a Backend-for-Frontend (BFF) front door and a
four-gate authorization ladder on every business request. This document covers the components,
the request path, and the trust boundaries. The ladder is detailed in
[authorization-model.md](authorization-model.md); the token rules in
[token-model.md](token-model.md).

## Components

| Component | Role | Address |
| --- | --- | --- |
| Frontend (Vite SPA) | Cookie-authenticated UI. Talks only to `/auth/*` and `/api/**`. Holds no tokens. | :5173 (dev) |
| APISIX gateway | Browser ingress. Resolves the session cookie, injects the bearer, validates CSRF. | :9080 |
| Auth Service | Confidential OIDC client. Owns `/auth/*`, the OAuth round trip, sessions, `/internal/resolve`. | auth-service:8081 |
| Keycloak | Authorization server. Issues user and service tokens. | :8080 |
| Valkey | BFF session store and short-TTL security-trace store. | valkey:6379 |
| Cart / Catalog / Order / Payment | Domain services. Each runs the four-gate ladder. | :8083 / :8084 / :8086 / :8085 |
| SpiceDB | Relationship-based authorization (gate 4). | :50051 |
| Postgres | Per-service persistence (`cart_db`, `catalog_db`, `order_db`, `payment_db`). | postgres:5432 |

Keycloak, Valkey, Postgres, and APISIX are swappable vendors; application code never branches on
the brand.

Two shared modules are compiled into the four domain services rather than run as their own
processes:

| Module | Provides |
| --- | --- |
| `commerce-security-common` | The four-gate primitives: `CommercePrincipal`, the Nimbus JWT validation, `ScopeAuthorizer`, `ResourceAuthorizer`, and the `AuthorizationClient` over SpiceDB. |
| `commerce-web-starter` | A Spring Boot auto-configured starter (`CommerceWebAutoConfiguration`) for the cross-cutting web concerns: the single RFC 9457 `GlobalExceptionHandler`, the sealed `ApiException` hierarchy, `ProblemDetailFactory`/`ProblemDetailWriter`, `TsidGenerator`, the keyset `CursorPaginator`/`Page`, and `TraceIdFilter`. |

The error contract across every service is RFC 9457 ProblemDetail (`application/problem+json`): a
`type` URI built from the per-error slug, plus `title`, `status`, `detail`, and the shared
`errorCode`, `traceId`, and `timestamp` extensions. Both the controller advice and the auth
filters build it through the same `ProblemDetailFactory`, so an advice-emitted and a
filter-emitted response carry the same shape.

## Request path

A browser request to a protected endpoint:

1. The SPA sends `fetch("/api/...", { credentials: "include" })`. The only credential is the
   opaque `__Host` session cookie.
2. APISIX matches the route and runs the `bff-session` plugin. The plugin calls Auth Service
   `POST /internal/resolve` with the session id, gets back the access token, strips any
   client-supplied identity or `Authorization` headers, injects the real bearer, and validates
   the signed double-submit CSRF token on unsafe methods.
3. The request reaches the domain service, which runs gates 2 to 4 (JWT, scope, SpiceDB) before
   business logic.
4. The service reads or writes Postgres and returns. No token travels back to the browser.

Anonymous catalog reads (`GET /api/catalog/products`) skip `bff-session`: they carry no bearer
and the service treats them as anonymous. Every write, and every cart and order route, runs the
full ladder.

## Trust boundaries

- **Browser to gateway.** The browser is untrusted. It holds a session cookie, never a token.
  Identity and `Authorization` headers from the browser are stripped at the gateway.
- **Gateway to services.** The gateway is the only injector of the user bearer. Services trust
  the JWT only after validating it (gate 2).
- **Service to service.** Order calls Payment with its own client-credentials token
  (`aud=payment-service`), not the user's token. Payment is not reachable through the browser
  gateway.

## Server-to-server: order to payment

Checkout is the one cross-service call. Order Service obtains a client-credentials token from
Keycloak (`client_id=order-service`, `aud=payment-service`, scope `payments:authorize`) and
calls Payment Service directly on the internal network. Payment validates that the token
audience is `payment-service` and rejects any user (`commerce-api`) token. See
[token-model.md](token-model.md).

## Persistence and authorization

Each service owns a Postgres database and persists domain data through repository interfaces,
with Spring Data JDBC implementations behind them. Ownership columns in Postgres are business
data only; they never decide access. Gate 4 always asks SpiceDB. The separation is enforced by
`scripts/verify-architecture.sh` and was re-proved live after the persistence layer was added.
