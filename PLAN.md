# oidc-service-reference build plan

## Foundational Thesis

`oidc-service-reference` takes the BFF/OIDC foundation proven in
`oidc-reference` and hardens it inside a real-world business application: real
persistence with Postgres, a real frontend stack, real domain services, and real
resource authorization. Catalog, cart, checkout, order, and merchant workflows
should feel ordinary to build and use.

The identity, session, token, and authorization plumbing works invisibly in the
background. A developer building catalog/cart/checkout features never handles
tokens, never parses JWTs in controllers, and never re-solves ownership checks.
The copied BFF front-door and the four-gate ladder carry it:

```text
gateway session -> service JWT -> scope -> SpiceDB resource
```

The repo is the teaching: it proves the OIDC foundation is production-shaped
enough to disappear beneath a normal business application, with
security-verification harnesses proving the boundaries still hold.

## Context

`oidc-reference` proves the browser-token boundary through login:

- React SPA has no OAuth/OIDC client.
- Access and refresh tokens never reach the browser. The `id_token` never reaches browser
  JS, storage, SPA-readable JSON, SPA-visible cookies, or app logs; only the server's
  logout redirect to the IdP may carry `id_token_hint`.
- A confidential Auth Service owns Authorization Code + PKCE, token exchange,
  refresh, logout, and server-side session.
- APISIX resolves the opaque session and injects a bearer token.
- Local infra is Docker Compose, Keycloak, APISIX, Valkey, Spring Boot, and
  React.

This repo is the sibling that proves that foundation survives contact with a
normal business app. It is not an attack lab. It should feel like a business
ecommerce app first. Security is platform behavior underneath;
security-verification tests prove that platform still holds.

## Goal

Teach how a BFF/OIDC login becomes least-privilege, audience-bound,
resource-authorized calls across persistent ecommerce microservices. OIDC/BFF,
gateway token handling, service JWT validation, scopes, and SpiceDB resource
authorization are secure platform defaults that disappear beneath ordinary
feature work, with security-verification harnesses proving each boundary.

## Product

Lead with the business app:

1. **Catalog browsing** - product list + details; anonymous or signed-in.
2. **Cart** - signed-in user adds/removes items and sees only their own cart.
3. **Checkout** - user checks out; order-service calls payment-service;
   idempotency prevents duplicate orders.
4. **Order history** - user sees their orders; support can view but not cancel;
   owner can cancel eligible orders.
5. **Merchant catalog management** - merchant manages products as a normal admin
   workflow.

Each flow has invisible security: gateway resolves the session, service validates
the JWT, scope check passes, SpiceDB resource check passes, decision traced.
Security Trace is harness/internal evidence for those boundaries, not a core
ecommerce product flow.

## Architecture

```text
React SPA
  -> APISIX API Gateway
    -> Auth Service /internal/resolve
    -> Catalog Service
    -> Cart Service
    -> Order Service
        -> Payment Service
    -> Trace endpoint

Auth Service -> Keycloak
Auth Service -> Valkey
Domain Services -> SpiceDB
Order Service -> Keycloak client_credentials
Order Service -> Payment Service
```

Core platform behavior:

1. Browser holds only an HttpOnly `__Host-sid` session cookie.
2. SPA calls only `/auth/*` and `/api/**`.
3. Gateway resolves the session with Auth Service.
4. Gateway strips unsafe inbound headers.
5. Gateway injects the server-side access token.
6. Domain services validate JWTs, scopes, and resource authorization.
7. Resource authorization uses SpiceDB behind a Spring adapter.
8. Payment Service accepts only service-to-service calls from Order Service.

## Authorization Ladder

Every protected domain request passes four platform gates. Payment adds a
separate service-to-service gate.

1. **Gateway session gate**
   - Reads `__Host-sid`.
   - Calls Auth Service `/internal/resolve`.
   - Validates CSRF on state-changing requests.
   - Strips unsafe inbound headers.
   - Injects `Authorization: Bearer <access_token>`.
   - Creates or forwards `X-Trace-Id`.
   - Does not perform domain authorization.

2. **Service JWT gate**
   - Service validates issuer, RS256 signature, expiry, JWT type, and
     `aud=commerce-api`.
   - Accepted `typ` values are explicit: `JWT` and `at+JWT`.
   - Audience validation must accept Keycloak's single-audience string shape and
     multi-audience array shape.

3. **Coarse action gate**
   - Service checks JWT scopes/roles for action class, for example:
     `catalog:write`, `cart:read`, `cart:write`, `orders:read`,
     `orders:write`.

4. **Fine resource gate**
   - Service application code asks `ResourceAuthorizer`; `ResourceAuthorizer` uses the
     `AuthorizationClient` port backed by SpiceDB, for example:
     `cart:alice-cart#write@user:alice`,
     `order:alice-order#read@user:support`,
     `store:main#manage@user:merchant`.
   - Checks are per request, fail closed, and `fully_consistent`.

5. **Payment service-to-service gate**
   - Payment Service validates `aud=payment-service`,
     `azp/client_id=order-service`, and `scope=payments:authorize`.

Important distinction:

- OIDC and JWT scopes say the caller may ask for an action class.
- SpiceDB says the caller may act on this exact resource.
- Payment S2S token proves the caller service, not direct user consent.

## Locked Decisions

- **Token model: minted once at login.** IdP issues the user access token at the
  auth-code exchange. BFF holds it in `sess:{sid}`; gateway injects it as a
  phantom token. No per-hop token exchange and no resolve-time `resource`
  parameter in v1.
- **Audience.** One `commerce-api` audience on the user token for all
  user-facing services; separate `payment-service` audience for S2S. No
  per-user-service audience in v1.
- **Least privilege.** Coarse scopes/roles in the JWT plus fine SpiceDB ReBAC.
- **ReBAC.** SpiceDB behind an `AuthorizationClient` adapter for v1. SDK calls
  do not appear in controllers.
- **S2S.** Client credentials plus structured command for
  `order-service -> payment-service`.
- **Payment limitation.** Payment authorizes the caller service and trusts
  `userSub` as command data. RFC 8693 token exchange is documented future
  hardening for cryptographic user provenance.
- **Stack.** Java 26 / Spring Boot 4.1.0, React + TypeScript + Vite, TanStack
  Router, TanStack Query, Zod, Zustand, shadcn/ui, Radix, Tailwind, APISIX,
  Keycloak, Valkey, SpiceDB, Postgres, Docker Compose.
- **Persistence.** Prove the first cart ladder with in-memory domain
  repositories. Add Postgres as a deferred persistence slice for catalog, cart,
  order, and payment domain data. Valkey stays for BFF sessions and short-TTL
  Security Trace; order/payment idempotency moves to Postgres when those
  services become persistent.
- **Error shape.** `application/problem+json` everywhere.
- **Anonymous catalog.** Gateway allowlists `GET /api/catalog/products` and
  product detail as auth-optional; catalog treats missing bearer as anonymous.
- **Relationship manipulation.** Test-only fixture endpoints live behind a
  `test-fixture` Spring profile, off by default.
- **Provider agnostic.** Provider specifics stay in config. Code branches on
  standard OIDC concepts, not provider brands.

## Version Pinning Policy

Start from the latest stable, compatible versions available when the scaffold is
created, then pin every generated artifact exactly. "Latest" means stable GA or
current stable package release; exclude milestones, release candidates, canaries,
nightlies, and early-access builds unless this plan explicitly opts into them.

These pins were verified on 2026-06-17 and are the starting stack for the first
scaffold:

| Area | Component | Pin |
| --- | --- | --- |
| Java runtime | OpenJDK | `26` |
| Backend BOM | Spring Boot parent | `4.1.0` |
| Frontend runtime | Node.js | `26.3.0` |
| Frontend package manager | pnpm | `11.7.0` |
| Frontend app | React / React DOM | `19.2.7` |
| Frontend app | TypeScript | `6.0.3` |
| Frontend app | Vite | `8.0.16` |
| Frontend app | `@vitejs/plugin-react` | `6.0.2` |
| Frontend routing/data | `@tanstack/react-router` | `1.170.16` |
| Frontend routing/data | `@tanstack/react-query` | `5.101.0` |
| Frontend APIs/types | Zod | `4.4.3` |
| Frontend APIs/types | Zustand | `5.0.14` |
| Frontend APIs/types | `openapi-typescript` | `7.13.0` |
| Frontend UI | Tailwind CSS | `4.3.1` |
| Frontend UI | shadcn CLI | `4.11.0` |
| Local IdP | Keycloak image | `quay.io/keycloak/keycloak:26.6.3` |
| Gateway | APISIX image | `apache/apisix:3.16.0-debian` |
| Session/trace store | Valkey image | `valkey/valkey:9.1.0` |
| ReBAC | SpiceDB image | `ghcr.io/authzed/spicedb:v1.53.0` |
| Domain persistence | Postgres image | `postgres:18.4` |
| Domain persistence | PostgreSQL JDBC driver | `42.7.11` |
| Domain persistence | Flyway | `12.8.1` |
| Test harness | Testcontainers | `2.0.5` |
| Architecture gates | ArchUnit JUnit 5 | `1.4.2` |

Pinning rules:

- Committed Docker Compose files must use exact image tags, never `latest`.
- `package.json` must use exact versions, not `^`, `~`, `*`, `latest`, or
  workspace-generated loose ranges. Commit the pnpm lockfile.
- shadcn/ui components are copied into the repo. Pin the shadcn CLI when adding
  components, and pin each installed Radix package exactly in `package.json`.
- Maven uses an exact Spring Boot parent/BOM. Dependencies managed by that BOM
  stay BOM-managed; explicit versions are allowed only for tools/dependencies not
  managed by the chosen BOM.
- `.java-version`, `.sdkmanrc`, `.node-version`, `packageManager`, Maven Wrapper,
  Docker image tags, and test tool versions must agree with this table once those
  files exist.
- Before the scaffold slice generates code, refresh this table if the current
  date is later than 2026-06-17. After scaffold exists, version changes are their
  own upgrade slice: update pins, run the affected live verifier, and record the
  result in `PROGRESS.md`.
- Future-proofing comes from easy, verified upgrades. Do not trade reproducible
  local harnesses for floating dependency ranges.

## Components

```text
frontend/                 # React SPA - no OIDC client, no tokens
api-gateway/              # APISIX + bff-session.lua
auth-service/             # OIDC client + /internal/resolve + session store
authorization-server/     # Keycloak realm
authorization-service/    # SpiceDB + schema.zed + seed
commerce-security-common/ # shared security primitives
services/
  catalog-service/
  cart-service/
  order-service/
  payment-service/
schema/
docs/
scripts/
tests/
  security/
```

## Reuse From `oidc-reference`

Copy/adapt:

- Auth Service `/internal/resolve`.
- `SessionRecord`, `SessionIndexes`, `RefreshLock`, `AuthProperties`,
  `SecretSentinelValidator`.
- `bff-session.lua`: resolve, strip, inject, CSRF, route allowlist.
- Lightweight JWT validator chain:
  `JwtIssuerValidator`, `JwtTypeValidator("JWT", "at+JWT")`, audience check,
  RS256 pin.
- `SignedCsrfSupport`, `csrf-fixture.json`, Java/Lua parity tests.
- `SecurityAudit`, extended with `resource=`.
- Verification scripts, e2e/Playwright harness, and `assertNoBrowserTokens`.

Net-new:

- `commerce-security-common`.
- `authorization-service` with SpiceDB schema and seed.
- `AuthorizationClient` adapter.
- Catalog, cart, order, and payment services.
- Security Trace as verifier evidence.
- Ecommerce SPA screens.

## Shared Security Module

`commerce-security-common` is "just enough", not a framework. Keep it to these
primitives unless the vertical slice proves a need:

```text
CommercePrincipal                  # subject, scopes, tokenFingerprint
CommerceJwtValidator               # validated JWT -> CommercePrincipal
ScopeAuthorizer                    # require(principal, scope)
ResourceAuthorizer                 # require(subject, permission, resource)
DecisionTrace                      # allowed/denied(gate, resource)
```

Raw JWTs stay at the security boundary. Domain objects never receive raw tokens.
Per-service code wires these shared primitives; it does not duplicate them.

Use simple, focused JWT/JOSE libraries behind the module. Do not build the core
teaching path on heavyweight Spring OAuth2 Resource Server auto-configuration.
Spring filters/adapters are fine at the HTTP edge, but the four gates must remain
visible in service code: application services explicitly call `ScopeAuthorizer`
and `ResourceAuthorizer` before domain operations.

## Frontend Scope

React + TypeScript + Vite. No Next.js, no SSR requirement, no server
components.

Frontend app stack:

```text
TanStack Router     # typed routes, params, search, loaders, route boundaries
TanStack Query      # server state, mutations, cache, invalidation
Zod                 # runtime validation for API responses, forms, search params
Zustand             # local UI-only state
shadcn/ui + Radix   # accessible, copy-owned UI primitives
Tailwind            # styling system and design tokens
```

API/type strategy:

- Spring/OpenAPI contracts are the backend source of truth.
- Generate TypeScript API types with `openapi-typescript` when backend contracts exist.
  Do not generate frontend API clients/hooks in v1; use repo-owned typed API helpers so
  browser code never sets bearer headers or handles tokens.
- Use Zod at trust boundaries: API response parsing, route search-param
  validation, and form validation.
- TanStack Query owns server state for `/auth/me`, catalog, cart, and orders.
- Zustand owns local UI-only state such as drawers, selected product image,
  theme/sidebar state, and command palette state.
- Zustand must not store authenticated user data, API cache, cart/order server
  data, tokens, or authorization decisions.

UI system rules:

- Use shadcn/ui components first, composed over Radix primitives.
- Add components through the shadcn CLI; components are checked into the repo as
  source.
- Use semantic Tailwind tokens and component variants before custom styling.
- Use lucide icons in buttons and controls when an icon exists.
- Every route must have polished loading, error, empty, and disabled states.
- The app should feel like a business ecommerce UI first; Security Trace is
  harness/internal evidence, not a core ecommerce screen.

User-facing screens:

- Product catalog.
- Product details.
- Cart.
- Checkout.
- Order history.
- Merchant catalog management.

Frontend calls:

```text
GET  /auth/me
GET  /auth/login?return_to=...
POST /auth/logout

GET    /api/catalog/products
GET    /api/catalog/products/{productId}
POST   /api/catalog/products
PATCH  /api/catalog/products/{productId}
GET    /api/cart
GET    /api/carts/{cartId}
POST   /api/cart/items
DELETE /api/cart/items/{itemId}
POST   /api/orders/checkout
GET    /api/orders/{orderId}
POST   /api/orders/{orderId}/cancel
```

Frontend must never:

- create an OAuth/OIDC client
- read or write access tokens
- set bearer headers
- parse JWTs
- store tokens in `localStorage`, `sessionStorage`, IndexedDB, or JS-readable
  cookies

Frontend developer experience should look ordinary: route code calls typed API
helpers through TanStack Query mutations and queries. It never creates an OAuth
client, sets bearer headers, parses JWTs, or stores tokens.

## API Gateway Scope

Use APISIX, adapted from `oidc-reference`.

Responsibilities:

- Route allowlist:
  - `/auth/**` to Auth Service
  - `GET /api/catalog/products/**` auth-optional to Catalog Service
  - protected catalog writes to Catalog Service
  - `/api/cart/**` and `/api/carts/**` to Cart Service
  - `/api/orders/**` to Order Service
  - no browser route to Payment internals
- Read session cookie.
- Redirect top-level unauthenticated navigation to
  `/auth/login?return_to=...`.
- Return `401` for unauthenticated fetch/XHR.
- Validate signed CSRF on state-changing requests.
- Call Auth Service `/internal/resolve`.
- Strip unsafe inbound headers:
  - `Authorization`
  - `Cookie`
  - `X-User`
  - `X-Roles`
  - `X-Groups`
  - `X-Forwarded-User`
  - `X-Forwarded-Email`
  - `X-Remote-User`
  - `X-Auth-Request-User`
- Inject `Authorization: Bearer <access_token>`.
- Add `Cache-Control: no-store`.
- Create or forward `X-Trace-Id`.

Gateway must not:

- call SpiceDB
- parse domain resources
- make cart/order/catalog authorization decisions
- trust browser-provided identity headers
- route browser traffic to `/internal/payments/**`

## Auth Service Scope

Spring Boot confidential OIDC client adapted from `oidc-reference`.

Endpoints:

```text
GET  /auth/login?return_to=...
GET  /auth/callback/idp
GET  /auth/me
POST /auth/logout
POST /internal/resolve
```

`/internal/resolve` request:

```json
{
  "sid": "opaque-session-id"
}
```

`/internal/resolve` response:

```json
{
  "access_token": "...",
  "access_token_expires_at": "2026-06-16T12:00:00Z",
  "subject": "alice",
  "fingerprint": "sha256:..."
}
```

Responsibilities:

- Authorization Code + PKCE.
- Server-side session.
- Token exchange.
- Refresh token rotation handling.
- Refresh serialization.
- Logout.
- `/auth/me`.
- Internal resolver auth for gateway.

`/auth/me` returns a minimal browser-safe display projection only:

```json
{
  "authenticated": true,
  "subject": "alice",
  "displayName": "Alice",
  "roles": ["customer"],
  "csrf": {
    "headerName": "X-CSRF-Token"
  }
}
```

Rules:

- No access token, refresh token, ID token, raw JWT, raw claims object, session id,
  cookie value, or client secret.
- The no-browser-token harness must inspect `/auth/me` responses.

Do not add resource-specific token minting in v1.

## Authorization Server Scope

Use Keycloak as the local IdP and PingFederate stand-in.

Realm clients:

- `commerce-auth`: confidential client for Auth Service.
- `commerce-api-gateway`: confidential client for gateway internal calls.
- `order-service`: confidential client for Order Service client-credentials
  flow.
- `commerce-api`: user-facing API audience.
- `payment-service`: Payment Service audience.

Users:

- `alice`
- `bob`
- `support`
- `merchant`
- `admin`

Scopes:

```text
catalog:read
catalog:write
cart:read
cart:write
orders:read
orders:write
payments:authorize
```

Token expectations:

- User-facing access token has `aud=commerce-api`.
- Payment S2S token has `aud=payment-service`.
- Tokens use RS256 in v1.
- Services validate issuer, signature, expiry, type, audience, and scopes.

Document Cognito as a scope-profile adapter; do not bake provider-specific code
into services.

## Authorization Service Scope

Use SpiceDB as the open-source Zanzibar implementation.

All Spring services call it through an adapter:

```java
public interface AuthorizationClient {
    boolean check(SubjectRef subject, ResourceRef resource, Permission permission);
    void writeRelationship(Relationship relationship);
    void deleteRelationship(Relationship relationship);
}
```

Ownership rule: application services call `ResourceAuthorizer`; `ResourceAuthorizer`
depends on the `AuthorizationClient` port; the SpiceDB adapter implements that port in
infrastructure/client code. Application services, controllers, and domain objects never
inject or call `AuthorizationClient` directly.

Do not scatter SpiceDB SDK calls through controllers or domain services.

Single-tenant SpiceDB schema (`authorization-service/schema.zed`):

```zed
definition user {}

definition store {
  relation manager: user
  permission view = manager
  permission manage = manager
}

definition cart {
  relation owner: user
  permission read = owner
  permission write = owner
}

definition order {
  relation owner: user
  relation support: user
  permission read = owner + support
  permission cancel = owner
}
```

Seed relationships:

```text
store:main#manager@user:merchant

cart:alice-cart#owner@user:alice
cart:bob-cart#owner@user:bob

order:alice-order#owner@user:alice
order:alice-order#support@user:support
order:bob-order#owner@user:bob
```

Use `fully_consistent` checks for the relationship-revocation demo and document
the latency cost. Tenant -> store -> cart hierarchy is a documented v2 extension.
`store#manager` is the relationship seeded in SpiceDB; `store#manage` is the permission
checked by catalog writes.

## Spring Boot Service Structure

Services are domain-oriented and proportionate. Controllers are adapters;
application services orchestrate; domain objects own business invariants.

Canonical layout for each domain service:

```text
com.example.commerce.cart
  CartServiceApplication.java

com.example.commerce.cart.web
  CartController.java
  CartRequest.java
  CartResponse.java
  RestExceptionHandler.java

com.example.commerce.cart.service
  CartApplicationService.java
  AddItemCommand.java
  RemoveItemCommand.java

com.example.commerce.cart.domain
  Cart.java
  CartId.java
  CartItem.java
  ProductId.java
  Quantity.java
  Money.java
  CartRepository.java

com.example.commerce.cart.persistence
  InMemoryCartRepository.java

com.example.commerce.cart.client
  SpiceDbAuthorizationClient.java
  TraceClient.java

com.example.commerce.cart.config
  SecurityConfig.java
  CartProperties.java
```

Rules:

- `web`: controllers, request/response DTOs, exception mapping only.
- `service`: Spring `@Service` application orchestration.
- `domain`: business entities, value objects, repository interfaces,
  invariants.
- `persistence`: repository implementations.
- `client`: outbound clients to SpiceDB, Payment, Trace, or Auth-related
  services.
- `config`: Spring configuration and properties.
- Shared security primitives come from `commerce-security-common`, not
  per-service copies.

Controller/application-service contract:

- Controllers are HTTP adapters: authenticate principal, validate/translate request DTOs,
  call one application service use case, and return response/problem DTOs.
- Controllers do not parse JWTs, call SpiceDB, call repositories, or enforce resource
  ownership manually.
- Application services orchestrate the ladder in this order: scope check, resource check,
  domain operation, persistence. Trace/evidence is recorded as each gate runs, including
  denials; domain and persistence success are recorded only after mutation succeeds.
- Domain objects enforce business invariants and do not depend on Spring Security, JWT,
  web, persistence, or SpiceDB SDK types.
- Repository interfaces stay in the domain; implementations stay in persistence.
- Raw JWTs never cross the security boundary into application/domain code.

Domain objects must enforce business invariants. Security helpers should not
leak raw JWTs into domain entities.

## Postgres Persistence Slice

Postgres is part of the target local stack, but it is not part of the first
cart authorization proof. The first cart vertical slice may use in-memory
repositories so the repo proves the four-gate ladder before taking on database
concerns.

Add Postgres after the cart ladder is proven and before the final platform
verification:

```text
One local Postgres container
Separate database or schema per service:
  catalog_service
  cart_service
  order_service
  payment_service
Flyway migrations per service
Spring Data JDBC for simple repository implementations
```

Persistence rules:

- Repository interfaces stay in each service's domain package.
- Postgres implementations live in each service's persistence package.
- Postgres stores product, cart, order, payment, and idempotency domain data.
- Valkey continues to store BFF sessions and short-TTL Security Trace entries.
- Order/payment idempotency must be transactionally tied to the relevant
  Postgres state once those services are persistent.
- Postgres unavailable or transaction failure must fail closed: no checkout, payment,
  catalog write, cart mutation, or order mutation is treated as allowed/successful when
  required persistence cannot commit.
- Postgres ownership columns are business data only; they are not the fine
  authorization source.
- SpiceDB remains the resource-authorization authority. Do not replace
  `ResourceAuthorizer` checks with `WHERE owner_id = ?` shortcuts.

## Catalog Service Scope

Endpoints:

```text
GET  /api/catalog/products
GET  /api/catalog/products/{productId}
POST /api/catalog/products
PATCH /api/catalog/products/{productId}
```

Rules:

- Product list/detail is anonymous or authenticated optional, read-only, and must not
  expose management-only fields.
- `POST/PATCH` require:
  - valid JWT with `aud=commerce-api`
  - scope `catalog:write`
  - SpiceDB `store:main#manage@user:{sub}`

Domain model:

```text
Product
ProductId
Sku
ProductName
Money
InventoryStatus
StoreId
```

## Cart Service Scope

Endpoints:

```text
GET    /api/cart
GET    /api/carts/{cartId}
POST   /api/cart/items
DELETE /api/cart/items/{itemId}
```

Rules:

- `GET /api/cart`: resolve the current user's cart id server-side, then still require
  `cart:read` plus SpiceDB `cart:{resolvedCartId}#read@user:{sub}`. `findByOwner(sub)` or
  a future `WHERE owner_id = ?` lookup is not a substitute for gate four.
- `GET /api/carts/{cartId}` requires:
  - `cart:read`
  - SpiceDB `cart:{cartId}#read@user:{sub}`
- `POST/DELETE` require:
  - `cart:write`
  - SpiceDB `cart:{cartId}#write@user:{sub}`

Domain invariants:

- quantity must be positive
- item must exist before removal
- cart total is computed from items
- cart owner is immutable after creation

## Order Service Scope

Endpoints:

```text
POST /api/orders/checkout
GET  /api/orders/{orderId}
POST /api/orders/{orderId}/cancel
```

Rules:

- checkout requires:
  - `orders:write`
  - idempotency key
  - current user cart id resolved server-side, followed by SpiceDB
    `cart:{resolvedCartId}#read@user:{sub}` before checkout
  - payment authorization through Payment Service
- read requires:
  - `orders:read`
  - SpiceDB `order:{orderId}#read@user:{sub}`
- cancel requires:
  - `orders:write`
  - SpiceDB `order:{orderId}#cancel@user:{sub}`

Domain invariants:

- same idempotency key plus same body returns the same order
- same idempotency key plus different body is rejected
- cancelled order cannot be paid again
- confirmed order records payment authorization id
- checkout must claim and persist the idempotency key/body before calling payment; concurrent
  same-key requests must collapse to one payment authorization, and collisions fail before
  payment is called

## Payment Service Scope

Internal only.

Endpoint:

```text
POST /internal/payments/authorize
```

Request:

```json
{
  "orderId": "ord-123",
  "userSub": "alice",
  "amount": 4299,
  "currency": "USD",
  "idempotencyKey": "checkout-abc"
}
```

Rules:

- no browser route
- requires client credentials token
- validates `aud=payment-service`
- validates `azp/client_id=order-service`
- validates `scope=payments:authorize`
- requires idempotency key

Payment authorizes Order Service and trusts `userSub` as command data from Order
Service. It does not cryptographically prove direct user consent. RFC 8693 token
exchange is future hardening.

## Decision Trace

Purpose: provide bounded, masked verifier evidence for the four-gate ladder
without exposing raw tokens. Use Valkey with a short TTL, keyed by `X-Trace-Id`.

Every request gets:

```text
X-Trace-Id: trace-123
```

Trace shape:

```json
{
  "traceId": "trace-123",
  "request": "POST /api/cart/items",
  "browser": {
    "tokenVisible": false,
    "sessionCookiePresent": true
  },
  "gateway": {
    "route": "/api/cart/**",
    "resolvedSession": true,
    "injectedTokenFingerprint": "sha256:...",
    "strippedHeaders": ["authorization", "x-user"]
  },
  "service": {
    "name": "cart-service",
    "audienceCheck": "pass commerce-api",
    "scopeCheck": "pass cart:write",
    "resourceCheck": "cart:alice-cart#write@user:alice",
    "decision": "allow"
  }
}
```

Access:

- Trace IDs come from the request/response `X-Trace-Id`.
- Trace read access is harness/internal only and enabled under local/test profiles.
- Trace evidence is consumed by verifier scripts and tests; it is not a public
  user-facing API.

Trace must never include:

- raw access token
- refresh token
- ID token
- session cookie value
- client secrets

## Security Verification

These are automated tests and harness checks. They prove platform boundaries;
they are not the product narrative.

Harness practices:

- Every check has a stable ID, purpose, command, expected result, and remediation note.
- Fixture and relationship-mutation controls are local/test-profile only and fail closed
  outside that profile.
- Mutating harness actions such as relationship mutation, seed reset, and trace clear
  require an explicit local/test profile; read-only evidence is the default.
- Evidence is bounded and masked: fingerprints, trace IDs, claims summaries, health status,
  and failure reasons are allowed; tokens, secrets, cookies, and raw prompts are not.
- Failures must be actionable: report the missing service, failed gate, expected decision,
  and next investigation hint. Examples: "SpiceDB unavailable", "Keycloak not healthy",
  "APISIX route missing".
- Service-health summaries for Keycloak, APISIX, Valkey, SpiceDB, and Postgres belong in
  `agent-init.sh`, `verify-all.sh`, and verifier output.
- Architecture rules are harness gates, preferably ArchUnit tests, not UI panels.

Required scenario IDs:

- **SEC-NO-BROWSER-TOKENS — No browser tokens**
   - Login, cart, checkout, logout flow leaves no tokens in browser storage,
     SPA-readable JSON, JS-readable cookies, or app logs. The only allowed
     `id_token` appearance is server logout redirect as `id_token_hint`.

- **SEC-NON-COMMERCE-AUD — Non-commerce token rejected**
   - Token with `aud != commerce-api` is rejected by user-facing services.

- **SEC-CATALOG-ANONYMOUS-READ-ONLY — Catalog anonymous read-only**
   - Anonymous callers can read catalog list/detail, cannot create or update products, and
     never receive management-only fields.

- **SEC-SCOPE-WITHOUT-RELATIONSHIP — Scope without relationship**
   - Alice has `cart:read` but requests Bob's cart. JWT and scope pass; SpiceDB
     denies.

- **SEC-RELATIONSHIP-WITHOUT-SCOPE — Relationship without scope**
   - Caller owns a resource but token lacks the required scope. Action gate
     denies.

- **SEC-SPOOFED-IDENTITY-HEADERS — Spoofed identity headers**
   - Browser sends `X-User: admin`. Gateway strips it. Trace shows the stripped
     header.

- **SEC-BROWSER-AUTHORIZATION-OVERWRITTEN — Browser Authorization header**
   - Browser sends fake bearer. Gateway overwrites it. Service sees only the
     injected token fingerprint.

- **SEC-PAYMENT-NO-BROWSER-ROUTE — Payment direct from browser**
   - Browser cannot reach payment internal route.

- **SEC-PAYMENT-WRONG-CLIENT — Order-to-payment wrong client**
   - Payment rejects token where `azp != order-service`.

- **SEC-PAYMENT-REJECTS-USER-TOKEN — User token rejected by payment**
   - User token `aud=commerce-api` is rejected by Payment Service expecting
     `payment-service`.

- **SEC-CHECKOUT-IDEMPOTENT-REPLAY — Duplicate checkout**
    - Same idempotency key plus same body returns same order and does not
      double-authorize payment.

- **SEC-CHECKOUT-IDEMPOTENCY-COLLISION — Idempotency collision**
    - Same idempotency key plus different body is rejected.

- **SEC-SPICEDB-UNAVAILABLE — SpiceDB unavailable**
    - Reads and writes fail closed.

- **SEC-RELATIONSHIP-REMOVAL-IMMEDIATE — Relationship removal**
    - Remove Alice's cart ownership. Next call is denied without re-login.

## Testing Plan

Unit tests:

- JWT converter/validator tests
- scope authorization tests
- `CommercePrincipal` mapping tests
- `AuthorizationClient` fake/client behavior
- domain invariant tests
- idempotency tests
- payment command validation tests
- ArchUnit-style architecture tests for web/service/domain/persistence/security boundaries

Contract tests:

- Auth Service `/internal/resolve`
- gateway header stripping and bearer injection
- service audience rejection
- payment S2S token validation
- SpiceDB schema checks

Integration tests:

- cart ownership
- catalog management
- order checkout
- order-to-payment call
- payment rejecting browser/user token
- SpiceDB unavailable fail-closed
- relationship removal without re-login

Browser e2e:

- login
- browse catalog
- add to cart
- checkout
- view order
- merchant catalog update
- support order read
- Security Trace evidence is available for verifier assertions
- no browser tokens

The verification gate should be `verify-all.sh`-style: unit + contract +
integration + e2e + security-verification.

## Build Order

Vertical slice first, then parallel fan-out:

1. **Scaffold from `oidc-reference`**
   - Copy/adapt Auth Service, APISIX gateway, frontend shell, Keycloak realm,
     compose, scripts, and harness.
   - Bring up Keycloak, Valkey, APISIX, Auth Service, and frontend shell.

2. **Add authorization substrate**
   - Add SpiceDB container, schema, and seed relationships.
   - Add `commerce-security-common`.
   - Add `AuthorizationClient` and fake/test implementation.

3. **Build Cart Service vertical slice**
   - JWT validation.
   - scope gate.
   - SpiceDB resource check.
   - trace writes.
   - cart UI.
   - security checks `SEC-NO-BROWSER-TOKENS` and `SEC-NON-COMMERCE-AUD`.
   - security checks `SEC-SCOPE-WITHOUT-RELATIONSHIP`,
     `SEC-RELATIONSHIP-WITHOUT-SCOPE`, `SEC-SPOOFED-IDENTITY-HEADERS`,
     `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN`, and
     `SEC-RELATIONSHIP-REMOVAL-IMMEDIATE`.
   - architecture checks for controller/service/domain/persistence/security boundaries.
   - no-browser-tokens guard remains green.

After this slice is green, the cart implementation becomes the reusable service template.
The orchestrator should fan out catalog, order, payment, frontend workflow, and harness
work in parallel with explicit write scopes instead of continuing as a single sequential
agent.

4. **Build Catalog Service**
   - anonymous reads.
   - merchant writes.
   - `store#manage` check.
   - security check `SEC-CATALOG-ANONYMOUS-READ-ONLY`.

5. **Build Order Service and Payment Service**
   - checkout.
   - S2S client credentials.
   - idempotency.
   - support read vs owner cancel.
   - security checks `SEC-PAYMENT-NO-BROWSER-ROUTE`,
     `SEC-PAYMENT-WRONG-CLIENT`, `SEC-PAYMENT-REJECTS-USER-TOKEN`,
     `SEC-CHECKOUT-IDEMPOTENT-REPLAY`, and `SEC-CHECKOUT-IDEMPOTENCY-COLLISION`.

6. **Add Postgres persistence**
   - Add local Postgres to Compose.
   - Add per-service database/schema and Flyway migrations.
   - Replace in-memory domain repositories with Spring Data JDBC/Postgres
     implementations behind the existing repository interfaces.
   - Move order/payment idempotency into Postgres transactions.
   - Keep Valkey for BFF sessions and short-TTL Security Trace.
   - Re-run the cart/catalog/order/payment gates to prove persistence did not
     bypass SpiceDB authorization.

7. **Complete platform verification**
   - SpiceDB-unavailable fail-closed.
   - full local `verify-all.sh` security-verification suite.
   - Security Trace evidence.
   - harness check catalog with stable IDs, commands, expected results, and remediation.
   - final docs.

Parallel fan-out ownership after cart:

```text
catalog-service agent       -> services/catalog-service plus catalog API tests
order-service agent         -> services/order-service plus checkout/order tests
payment-service agent       -> services/payment-service plus S2S/payment tests
frontend workflow agent     -> frontend ecommerce routes/screens/query wiring
harness/security agent      -> tests/security, verify-all.sh, SEC-* catalog, ArchUnit gates
persistence agent           -> Postgres/Flyway/Spring Data JDBC slice, after service APIs stabilize
orchestrator                -> shared contracts, integration, final verifier, PROGRESS.md
```

Agents may work in parallel only within their ownership scopes. Shared contracts
(`commerce-security-common`, SpiceDB schema, Keycloak realm, gateway routes, endpoint
shapes, `SEC-*` IDs, and harness output format) are orchestrator-owned.

## Per-Slice Acceptance Matrix

Each slice is done only when its acceptance gate is green in a run the
orchestrator watched. `PROGRESS.md` must record the exact command(s), result, and
remaining blockers. Every module slice also ships that module's `AGENTS.md` +
`CLAUDE.md` stub (root `AGENTS.md` → "Per-module agent config").

| Slice | Acceptance gate |
| --- | --- |
| Scaffold from `oidc-reference` | Copied/adapted BFF front-door boots locally; baseline auth flow runs through gateway; generated manifests use exact version pins from this plan; `SEC-NO-BROWSER-TOKENS` passes; initial `scripts/verify-all.sh` exists and reports the current scaffold state honestly with stable check IDs, bounded/masked output, service-health summary, and explicit pending/skipped states for checks not implemented yet. |
| Authorization substrate | SpiceDB starts in Compose; schema loads; seed relationships are applied; fake and real `AuthorizationClient` contract tests pass; unavailable SpiceDB path denies in tests. |
| Cart vertical slice | Cart UI and cart-service run through gateway; JWT, scope, and SpiceDB gates are traced; `SEC-NO-BROWSER-TOKENS`, `SEC-NON-COMMERCE-AUD`, `SEC-SCOPE-WITHOUT-RELATIONSHIP`, `SEC-RELATIONSHIP-WITHOUT-SCOPE`, `SEC-SPOOFED-IDENTITY-HEADERS`, `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN`, and `SEC-RELATIONSHIP-REMOVAL-IMMEDIATE` pass live; architecture checks for controller/service/domain/persistence/security boundaries pass; no-browser-token guard remains green. |
| Catalog service | Anonymous product reads work without bearer; merchant writes require `catalog:write` plus `store:main#manage`; non-merchant writes deny; `SEC-CATALOG-ANONYMOUS-READ-ONLY` passes; catalog gates pass through `verify-all.sh`. |
| Order + payment services | Checkout, order read, support read, owner cancel, and payment S2S flow pass; `SEC-PAYMENT-NO-BROWSER-ROUTE`, `SEC-PAYMENT-WRONG-CLIENT`, `SEC-PAYMENT-REJECTS-USER-TOKEN`, `SEC-CHECKOUT-IDEMPOTENT-REPLAY`, and `SEC-CHECKOUT-IDEMPOTENCY-COLLISION` pass live; user tokens are rejected by payment; architecture checks remain green. |
| Postgres persistence | Postgres starts in Compose; per-service Flyway migrations run; repository implementations persist domain data without changing controllers/domain APIs; order/payment idempotency is transactionally tied to Postgres; prior cart/catalog/order/payment security gates remain green. |
| Complete platform verification | Full `verify-all.sh` gate passes: unit, contract, integration, browser e2e, no-browser-token guard, architecture checks, and all `SEC-*` security-verification checks; harness output uses stable check IDs, bounded/masked evidence, actionable failure reasons, and service-health summaries; final docs describe business flows, security behind the scenes, authorization model, verification, and production hardening. |

## Documentation

Create:

```text
docs/architecture.md
docs/business-flows.md
docs/token-model.md
docs/authorization-model.md
docs/domain-modeling.md
docs/security-behind-the-scenes.md
docs/security-verification.md
docs/threat-model.md
docs/production-hardening.md
```

`domain-modeling.md` must state:

- controllers are web adapters
- Spring services orchestrate use cases
- domain objects enforce business invariants
- persistence implementations stay behind repository interfaces
- security helpers do not leak raw tokens into domain objects
- Application services use `ResourceAuthorizer`; SpiceDB calls stay behind the
  `AuthorizationClient` adapter

`security-behind-the-scenes.md` should show what frontend and backend developers
write, and what OIDC/security infrastructure does automatically.

`production-hardening.md` documents, but does not build:

- mTLS/SPIFFE
- service mesh
- RFC 8693 token exchange
- event bus
- Kubernetes
- multi-region
- production database operations beyond the local Postgres reference
- observability/SIEM integration
- supply-chain pinning
- Cognito scope-profile adapter
- OpenFGA portability as a future authorization-service adapter

## Non-Goals

No Kubernetes. No service mesh. No Kafka or event bus in v1. No real payment
processor. No public-client SPA OAuth. No browser tokens. No business
authorization in the gateway. No multi-tenant model in v1. No per-service
user-token audience in v1. No RFC 8693 token exchange in v1. No
resource-specific token minting in `/internal/resolve`. No
`admin-because-the-JWT-role-says-so` shortcut for resource access. No giant
security framework.

## Assumptions And Defaults

- Single tenant.
- Java 26.
- Spring Boot 4.1.0.
- React + TypeScript + Vite.
- TanStack Router and TanStack Query.
- Zod for runtime validation.
- Zustand for local UI-only state.
- shadcn/ui + Radix + Tailwind for polished UI primitives.
- APISIX gateway.
- Keycloak local IdP.
- Valkey for BFF sessions and Security Trace TTL storage.
- Postgres added after the first cart ladder proof for domain persistence.
- SpiceDB for Zanzibar/ReBAC.
- In-memory domain repositories only for the initial vertical slice.
- `aud=commerce-api` for user-facing services.
- `aud=payment-service` for payment S2S.
- RS256 only in v1.
- `application/problem+json` for errors.
- Docker Compose only.

## Local Environment Gotchas

- This Mac runs near-full disk. Check `df -h /System/Volumes/Data` before any
  stack build.
- APISIX cold-start can be flaky on a loaded Docker. Prefer Compose
  health-gating and avoid rapid `down -> up` churn.
- Run `mvnw clean test` before trusting a tally; stale surefire reports can
  invent phantom results.
- `rg` is a Claude shell shim invisible to `sh`; do not require `rg` in POSIX
  verify scripts.
- On Spring Boot / Nimbus / Spring Security bumps, re-run the suites and
  re-check multi-aud `azp` and JWS `typ` acceptance. Keep these enforced in
  code, not delegated to library defaults.

## End-To-End Definition Of Done

- `docker compose up` starts Keycloak, Valkey, Postgres, APISIX, Auth Service,
  SpiceDB, frontend, and services after the persistence slice lands.
- Business flow: Alice logs in, browses catalog, adds to cart, checks out,
  order-service calls payment, views the order; merchant manages catalog;
  support views but cannot cancel.
- The Security Trace shows, per request: browser token absent, session resolved,
  injected bearer fingerprint, scope decision, resource decision, no raw token.
- `tests/security/` is green through the local `verify-all.sh` gate.
- A `verify-all.sh`-style gate runs unit, contract, integration, e2e, and
  security-verification.
