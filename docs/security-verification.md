# Security verification

The reference proves its security properties with runnable gates, not prose. Every check has a
stable id and bounded output; no tokens or secrets are printed. This document is the map.

## The gates

| Command | Scope | Stack |
| --- | --- | --- |
| `scripts/verify-all.sh` | Static: version pins, per-service unit and contract tests, architecture boundaries, SpiceDB schema, draft security cases, and (when a stack is running) service health and the per-service Postgres databases. | none |
| `scripts/verify-architecture.sh` | Layering invariants by source inspection (also run inside `verify-all`). | none |
| `scripts/verify-frontend.sh` | Frontend lint, type-check, unit tests, build, and Playwright end-to-end. | live (e2e) |
| `scripts/verify-live-all.sh` | The 17 live backend `SEC-*` cases for cart, catalog, and order/payment, in one stack bring-up. | live |

A complete pass runs all three layers: `verify-all.sh` (static gates), `verify-frontend.sh`
(frontend checks and browser e2e), and `verify-live-all.sh` (live backend security). The live
scripts alone have a static blind spot, and the static script does not exercise live
authorization, so neither substitutes for the other.

## The SEC catalog

Each live security case has a stable id, a purpose, the command to run it, the expected result,
and a remediation hint. The catalogs are checked in under `tests/security/`:
`cart-security-cases.tsv`, `catalog-security-cases.tsv`, `order-payment-security-cases.tsv`.

The backend live battery is 17 cases: 11 in `cart-security-cases.tsv`, 1 in
`catalog-security-cases.tsv`, and 5 in `order-payment-security-cases.tsv`. `SEC-NO-BROWSER-TOKENS`
is not in this battery; it is the frontend Playwright case run by `scripts/e2e-auth.sh` (under
`verify-frontend.sh`), which asserts the token boundary end to end through the browser.

What the backend live suite proves, by area:

- **Token boundary:** `SEC-SPOOFED-IDENTITY-HEADERS`, `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN`.
- **Scope and relationship independence:** `SEC-SCOPE-WITHOUT-RELATIONSHIP`,
  `SEC-RELATIONSHIP-WITHOUT-SCOPE`, `SEC-NON-COMMERCE-AUD`.
- **Resource ownership:** `SEC-OWNERSHIP-PROVISIONED-FOR-CALLER`, `SEC-NO-RESOURCE-HIJACK`,
  `SEC-PROVISIONING-FAILS-CLOSED`.
- **Fail-closed:** `SEC-SPICEDB-UNAVAILABLE`, `SEC-RELATIONSHIP-REMOVAL-IMMEDIATE`.
- **Catalog:** `SEC-CATALOG-ANONYMOUS-READ-ONLY`.
- **Payment isolation:** `SEC-PAYMENT-NO-BROWSER-ROUTE`, `SEC-PAYMENT-WRONG-CLIENT`,
  `SEC-PAYMENT-REJECTS-USER-TOKEN`.
- **Checkout idempotency:** `SEC-CHECKOUT-IDEMPOTENT-REPLAY`, `SEC-CHECKOUT-IDEMPOTENCY-COLLISION`.
- **Trace evidence:** `SEC-SECURITY-TRACE-EVIDENCE`.

## Architecture checks

`verify-architecture.sh` enforces the layering by source-import inspection, so a structural
regression fails the build without a running stack:

- `ARCH-DOMAIN-PURE-*`, `ARCH-WEB-NO-PERSISTENCE-*`, `ARCH-GATES-AT-SERVICE-*` per service.
- `ARCH-GATE-SCOPE-PRESENT-*` and `ARCH-GATE-RESOURCE-PRESENT-*`: cart, catalog, and order must
  invoke both the scope and resource gates.
- `ARCH-FE-*`: the SPA token boundary (no token names, no `Authorization` header, no direct IdP
  calls, no auth-state storage writes).

These run as source greps because the build JDK outpaces ArchUnit's bytecode parser; a source
gate is version-proof and dependency-free. The frontend's `architecture.test.ts` (under
`verify-frontend.sh`) covers the same SPA boundary with more depth.

## Evidence discipline

Harness output is stable check ids, status codes, and pass/fail only. No token, cookie, or
secret value is printed, including in the trace-evidence case, which exposes gate decisions and a
token fingerprint but never token material. Failures name the specific check and a remediation,
so a red gate points at the fix.
