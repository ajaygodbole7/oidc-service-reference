# commerce-security-common agent notes

Read the root `../AGENTS.md`, `../PLAN.md`, and `../PROGRESS.md` first. This file only
narrows the local boundary for the shared security primitives.

## Ownership

`commerce-security-common` owns the smallest shared primitives for the four-gate ladder:

- Gate 2: focused JOSE validation into `CommercePrincipal`.
- Gate 3: explicit `ScopeAuthorizer`.
- Gate 4: explicit `ResourceAuthorizer` through an `AuthorizationClient` port.
- Bounded `DecisionTrace` evidence for verifier output and service logs.

This module is not a framework and does not own service business policy.

## Hard rules

- Do not add Spring Resource Server auto-configuration. Keep JWT validation as focused
  library code so services can see the gate clearly.
- Keep SpiceDB SDK details behind `AuthorizationClient`; do not let services or
  controllers scatter direct SDK calls.
- Fail closed. Invalid JWTs, missing scopes, denied relationships, and unavailable
  authorization backends must deny.
- Evidence must be bounded and masked: fingerprints, subjects, resources, permissions,
  and reasons are allowed; raw tokens, cookies, secrets, and prompts are not.
- Postgres ownership columns are business data only. They never replace
  `ResourceAuthorizer`.

## Local gate

From the repo root:

```sh
sh scripts/verify-commerce-security-common.sh
```

The script runs `clean test` with JDK 26 and the repo Maven wrapper when available. Always
use `clean test`; stale classes can hide security regressions after source rewrites.
