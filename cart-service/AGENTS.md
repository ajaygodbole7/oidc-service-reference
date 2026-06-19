# AGENTS.md - cart-service (Spring Boot 4 / Java 26)

Root AGENTS.md owns the shared invariants, build method, and hard rules. This file adds
only what is specific to cart-service; it does not restate them.

## What this is

Cart domain service. It exercises gates 2, 3, and 4 of the four-gate ladder after the
gateway session gate has injected the service JWT: service JWT -> scope -> SpiceDB
resource -> trace.

## Build & test (this module)

Use the root reactor so the shared security module and cart-service stay in sync:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-amzn mvn -f pom.xml -pl cart-service -am clean test
```

## Boundary rules

- Controllers are HTTP adapters only: translate request/response shapes and call one
  application-service use case.
- Application services orchestrate the visible gates: `ScopeAuthorizer` before
  `ResourceAuthorizer`, then domain mutation/read.
- `GET /api/cart` resolves the caller's cart id from the server-side subject, but that
  ownership lookup never replaces the SpiceDB check.
- Repositories stay in-memory until the first cart ladder is proven live.
- Do not parse JWTs, inspect bearer strings, or call the SpiceDB SDK in this module.

## Security contract / SEC-* cases proven here

- `SEC-SCOPE-WITHOUT-RELATIONSHIP`: scope passes, resource check denies another user's cart.
- `SEC-RELATIONSHIP-WITHOUT-SCOPE`: relationship can exist, but missing scope denies before
  the resource gate.

Module tests prove these service-level cases. Live gateway/JWT/browser-token checks remain
orchestrator-owned until the cart vertical slice is integrated.

## Gotchas

- The root reactor includes `commerce-security-common`; prefer the reactor command over
  installing sibling jars into the local Maven cache.
- Trace evidence is bounded: cart ids, subjects, permissions, and token fingerprints only.
