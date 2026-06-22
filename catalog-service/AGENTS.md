# AGENTS.md - catalog-service (Spring Boot 4 / Java 26)

Root AGENTS.md owns the shared invariants, build method, and hard rules. This file adds
only what is specific to catalog-service; it does not restate them.

## What this is

Catalog domain service. Product reads are anonymous/read-only; product writes exercise
gates 2, 3, and 4 after the gateway session gate has injected the service JWT:
service JWT -> catalog:write scope -> SpiceDB store manage check.

## Build & test (this module)

Use the root reactor so the shared security module and catalog-service stay in sync:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-amzn mvn -f pom.xml -pl catalog-service -am clean test
```

## Boundary rules

- Controllers are HTTP adapters only: translate request/response shapes and call one
  application-service use case.
- Anonymous `GET /api/catalog/products/**` must not require a JWT and must not expose
  management-only fields.
- Write use cases orchestrate the visible gates: `ScopeAuthorizer` before
  `ResourceAuthorizer`, then domain mutation.
- The write resource is `store:main`; the required permission is `manage`.
- Repositories are Spring Data JDBC over Postgres (the persistence slice is done); the
  in-memory repository remains as a fast unit-test/dev fixture.
- Do not parse JWTs, inspect bearer strings, or call the SpiceDB SDK in this module.

## Security contract / SEC-* cases proven here

- `SEC-CATALOG-ANONYMOUS-READ-ONLY`: anonymous callers can read list/detail and cannot
  create or update products; merchant writes require `catalog:write` and `store#manage`.

Module tests prove the service-level cases. Live gateway/browser-token checks remain
orchestrator-owned until the catalog slice is integrated through APISIX.

## Gotchas

- Root `PLAN.md` lists `services/catalog-service`, but the current working repo places
  service modules at the root beside `cart-service`.
- Trace evidence is bounded: product ids, store ids, subjects, permissions, and token
  fingerprints only.
