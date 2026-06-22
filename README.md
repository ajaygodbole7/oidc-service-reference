# oidc-service-reference

A reference commerce backend that authorizes every request through a four-gate ladder, built
on a Backend-for-Frontend (BFF) OIDC foundation. This is a reference codebase, not a product.
It shows how a cookie-session SPA, an API gateway, OIDC service tokens, coarse scopes, and
fine-grained relationship checks compose into one authorization path where every gate fails
closed.

The OIDC/BFF front door is adapted from
[`oidc-reference`](https://github.com/ajaygodbole7/oidc-reference); this repo adds the commerce
domain (cart, catalog, order, payment) and the fine-grained authorization layer (SpiceDB).

## The four-gate ladder

Every `/api/**` request clears four independent gates before it reaches business logic. Each
gate denies by default.

1. **Gateway session.** APISIX resolves the opaque `__Host` session cookie to a server-side
   session and injects the access token. The browser never receives a token.
2. **Service JWT.** The target service validates the injected JWT (signature, `iss`, `exp`,
   `aud=commerce-api`) with a focused Nimbus validator, not a catch-all resource server.
3. **Coarse scope.** The application service requires the operation's scope, such as
   `cart:write`, `catalog:write`, or `orders:read`.
4. **Fine resource.** The application service asks SpiceDB whether this subject may take this
   action on this specific resource, read `fully_consistent`.

The gates are independent on purpose. A valid token with the wrong scope is denied at gate 3.
A granted SpiceDB relationship without the scope is denied at gate 3. A correct scope without
the relationship is denied at gate 4. Persistence stores ownership columns as business data
only; it never substitutes for gate 4.

## Architecture

```
browser (SPA, cookie only)
  │  /auth/*, /api/**            no token in the browser
  ▼
APISIX gateway :9080  ──►  Auth Service :8081  ──►  Valkey :6379   (session store)
  │  injects JWT                  confidential OIDC client; owns /auth/*, /internal/resolve
  ▼
cart :8083 · catalog :8084 · order :8086 · payment :8085
  │  gates 2, 3, 4 per request
  ├──►  SpiceDB :50051     gate 4, relationship checks
  └──►  Postgres :5432     per-service persistence

Keycloak :8080            authorization server (issues user and service tokens)
order ──► payment         server-to-server, client-credentials, aud=payment-service
```

- **Frontend** (Vite SPA): cookie-authenticated, talks only to `/auth/*` and `/api/**`. Holds
  no tokens.
- **APISIX**: browser ingress. Resolves the session, injects the bearer, validates the signed
  double-submit CSRF token.
- **Auth Service**: the confidential OIDC client. Owns `/auth/*`, the OAuth round trip, session
  storage, and `/internal/resolve`.
- **Domain services** (cart, catalog, order, payment): each runs the four-gate ladder and its
  own business logic.
- **SpiceDB**: relationship-based authorization (gate 4).
- **Keycloak, Valkey, Postgres**: authorization server, session store, persistence. Each is a
  swappable vendor.

Payment Service has no browser route. Order Service calls it server-to-server with a
client-credentials token whose `aud=payment-service`, never a user token.

## API contract

**Cursor pagination.** The catalog list is keyset-paginated:
`GET /api/catalog/products?cursor=&limit=`. `limit` defaults to 20 and is capped; the response
returns the items plus a `nextCursor` (null on the last page). The cursor is an opaque base64url
of the last row's sortable TSID id; hand it back as the next `cursor`. A blank or malformed
cursor reads as the first page, never an error.

**Errors.** Every service returns RFC 9457 ProblemDetail (`application/problem+json`): a `type`
URI built from a per-error slug, plus `title`, `status`, `detail`, and the `errorCode`, `traceId`
(from the request trace), and `timestamp` extensions. A 403 carries the safe scope reason only;
the SpiceDB decision trace is logged server-side, never in the body. The gateway-filter 401
carries the same shape as a controller-raised error.

## Running locally

Prerequisites: Docker, Node 26.3.0, pnpm 11.7.0, curl.

```sh
scripts/bootstrap.sh           # install frontend dependencies
scripts/up.sh                  # render gateway config, build and start the stack
cd frontend && pnpm run dev    # serve the SPA
```

Open the SPA at http://127.0.0.1:5173 and sign in through Keycloak. The dev realm seeds users
such as `alice` (password `alice`); commerce traffic flows through the gateway at
http://127.0.0.1:9080. The Keycloak admin console is at http://localhost:8080 (`admin`/`admin`).
Stop the stack with `scripts/down.sh`.

The local stack uses loud `CHANGE_BEFORE_DEPLOY` dev secrets and binds every port to loopback.
A boot-time validator refuses to start with these secrets on a non-loopback bind.

## Verifying

Both layers gate on stable check IDs with bounded output; no tokens or secrets are printed.

| Command | What it covers |
| --- | --- |
| `scripts/verify-all.sh` | Static gates: version pins, per-service unit/contract tests, architecture boundaries, SpiceDB schema, draft security cases. No live stack. |
| `scripts/verify-architecture.sh` | Layering invariants by source inspection: domain purity, web never importing persistence, gates only at the service boundary, and the frontend token boundary. (Also runs inside `verify-all`.) |
| `scripts/verify-frontend.sh` | Frontend lint, type-check, unit tests, build, and Playwright end-to-end. |
| `scripts/verify-live-all.sh` | Live security gates against the running stack: every `SEC-*` case for cart, catalog, and order/payment in one orchestrated run. |

The live suite proves the ladder end to end: cross-user denial, scope-without-relationship and
relationship-without-scope denial, server-generated resource ids, immediate revocation on
relationship removal, SpiceDB-unavailable fail-closed, gateway header stripping and bearer
overwrite, payment audience isolation, checkout idempotency, and the standing invariant that no
token reaches the browser.

## The token boundary

The browser holds one opaque session cookie and nothing else. Access and refresh tokens live
only in the server-side session store; the gateway injects the access token into `/api/**`
requests after the browser is out of the picture. SPA source may not name tokens, set an
`Authorization` header, call the IdP token endpoint, or write auth state to web storage.
`scripts/verify-architecture.sh` enforces this statically and the live suite asserts it at
runtime.

## Status and scope

This is a local reference. It runs on Docker Compose with a single Keycloak realm and
per-service Postgres databases. Production concerns (mTLS/SPIFFE, service mesh, RFC 8693 token
exchange, multi-region, observability, supply-chain pinning) are documented as hardening
guidance, not built into the default.

## Documentation

- [docs/architecture.md](docs/architecture.md): components, request path, trust boundaries.
- [docs/authorization-model.md](docs/authorization-model.md): the four-gate ladder and the SpiceDB schema.
- [docs/token-model.md](docs/token-model.md): user and service tokens, the browser token boundary.
- [docs/domain-modeling.md](docs/domain-modeling.md): the service layering and dependency rules.
- [docs/security-behind-the-scenes.md](docs/security-behind-the-scenes.md): what you write vs what the platform does.
- [docs/business-flows.md](docs/business-flows.md): browse, cart, checkout, order.
- [docs/security-verification.md](docs/security-verification.md): the verify gates and the SEC/ARCH catalog.
- [docs/threat-model.md](docs/threat-model.md): threats and the controls that stop them.
- [docs/production-hardening.md](docs/production-hardening.md): what a real deployment adds.
- [SECURITY.md](SECURITY.md): local security posture and the secret-sentinel boot check.
