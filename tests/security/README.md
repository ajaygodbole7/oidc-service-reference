# Cart Security Harness

This directory owns the cart-slice security checks. The files are intentionally
local-first and verifier-shaped: stable check IDs, bounded evidence, actionable
unavailable states, and fail-closed expected results.

Run from the repo root:

```sh
sh tests/security/verify-cart-security-draft.sh
sh tests/security/verify-cart-security-live.sh
```

The draft runner is read-only. It does not mint tokens, mutate SpiceDB relationships, clear
traces, or call destructive fixture endpoints. Any future live relationship mutation, such
as removing `cart:alice-cart#owner@user:alice`, must require an explicit local/test profile
and must fail closed outside that profile.

The live runner starts or reuses the local stack, enables the cart-service `test-fixture`
profile only for harness endpoints, and restores the default local Auth Service scopes after
audience/scope mutation cases. It prints stable IDs and pass/fail states only; no tokens,
cookies, secrets, or full response bodies.

## Case Catalog

| ID | Purpose | Live command | Expected result | Remediation when unavailable |
| --- | --- | --- | --- | --- |
| `SEC-NON-COMMERCE-AUD` | Prove a token for another audience cannot call cart APIs. | `sh tests/security/verify-cart-security-live.sh SEC-NON-COMMERCE-AUD` | Gateway/session may pass, but cart service rejects `aud != commerce-api`. | Add cart-service JWT gate and a local/test-only non-commerce token fixture. |
| `SEC-SCOPE-WITHOUT-RELATIONSHIP` | Prove scope alone does not authorize Bob's cart. | `sh tests/security/verify-cart-security-live.sh SEC-SCOPE-WITHOUT-RELATIONSHIP` | JWT and `cart:read` pass; SpiceDB denies `cart:bob-cart#read@user:alice`. | Seed Alice/Bob carts and wire `ResourceAuthorizer` checks near cart domain logic. |
| `SEC-RELATIONSHIP-WITHOUT-SCOPE` | Prove ownership alone does not bypass coarse scopes. | `sh tests/security/verify-cart-security-live.sh SEC-RELATIONSHIP-WITHOUT-SCOPE` | SpiceDB relationship exists; `ScopeAuthorizer` denies before mutation/read proceeds. | Add a missing-scope fixture and keep scope/resource checks separate in the service. |
| `SEC-SPOOFED-IDENTITY-HEADERS` | Prove browser-supplied identity headers cannot impersonate a user. | `sh tests/security/verify-cart-security-live.sh SEC-SPOOFED-IDENTITY-HEADERS` | APISIX strips unsafe identity headers; service trace shows trusted principal only. | Add protected cart route through APISIX and trace stripped-header evidence. |
| `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN` | Prove browser `Authorization` is not trusted. | `sh tests/security/verify-cart-security-live.sh SEC-BROWSER-AUTHORIZATION-OVERWRITTEN` | APISIX overwrites browser bearer; service sees only injected token fingerprint. | Add gateway header overwrite proof and bounded token-fingerprint trace evidence. |
| `SEC-RELATIONSHIP-REMOVAL-IMMEDIATE` | Prove SpiceDB revocation is immediate. | `HARNESS_PROFILE=local sh tests/security/verify-cart-security-live.sh SEC-RELATIONSHIP-REMOVAL-IMMEDIATE` | After owner relationship removal, next cart read denies without re-login. | Add local/test-only relationship mutation fixture and use fully consistent checks. |

## Evidence Rules

- Print stable IDs, service/route availability, HTTP status classes, trace IDs, and masked
  token fingerprints only.
- Never print access tokens, refresh tokens, ID tokens, cookies, secrets, raw prompts, or
  full request/response bodies.
- Report concrete unavailable states such as `cart-service unavailable`,
  `APISIX /api/cart route missing`, `SpiceDB unavailable`, or
  `local/test mutation profile disabled`.
