# Threat model

oidc-service-reference defends a commerce backend behind a Backend-for-Frontend front door and a
four-gate authorization ladder. This document lists the concrete threats the platform is built to
stop, the control or gate that stops each one, and the live `SEC-*` case that proves the control
holds. The gates are detailed in [authorization-model.md](authorization-model.md), the token rules
in [token-model.md](token-model.md), and the verification harness in
[security-verification.md](security-verification.md).

Each control denies by default. Each threat maps to a check that runs against the live stack, not
to a claim. The check id is the proof; the catalog and commands live in
[security-verification.md](security-verification.md).

## Threats and controls

| Threat | Control | Proof |
| --- | --- | --- |
| Token theft from the browser (XSS, malicious extension, web-storage scrape) | No access, refresh, or id token ever reaches the browser. The browser holds one opaque `__Host` session cookie; tokens live only in the server-side session store and are injected by the gateway after the browser is out of the picture (gate 1, token boundary). | `SEC-NO-BROWSER-TOKENS` |
| CSRF on a state-changing request | Signed double-submit `XSRF-TOKEN` cookie bound to the session. The SPA echoes it in `X-XSRF-TOKEN` on unsafe methods; the gateway validates it (gate 1). | enforced at gate 1 |
| IDOR / horizontal privilege escalation (acting on another user's cart or order) | Gate 4 asks SpiceDB whether this subject may take this action on this exact resource, read `fully_consistent`. Resource ids are server-generated; the create path ignores any client-supplied or attacker-chosen id. | `SEC-NO-RESOURCE-HIJACK`, `SEC-SCOPE-WITHOUT-RELATIONSHIP` |
| Over-broad or wrong token (scope-versus-relationship confusion) | Gates 3 and 4 are independent. A valid scope without the relationship is denied at gate 4; a granted relationship without the scope is denied at gate 3; a token minted for the wrong audience is denied at gate 2. | `SEC-RELATIONSHIP-WITHOUT-SCOPE`, `SEC-NON-COMMERCE-AUD` |
| Spoofed identity headers or a browser-supplied bearer | The gateway strips client-supplied identity and `Authorization` headers and injects the real bearer. A browser-supplied bearer is overwritten, not trusted. | `SEC-SPOOFED-IDENTITY-HEADERS`, `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN` |
| Cross-service abuse of the payment API | Payment Service has no browser route and validates `aud=payment-service`. It accepts only Order Service client-credentials tokens (`azp/client_id=order-service`, scope `payments:authorize`) and rejects any user `commerce-api` token. | `SEC-PAYMENT-NO-BROWSER-ROUTE`, `SEC-PAYMENT-WRONG-CLIENT`, `SEC-PAYMENT-REJECTS-USER-TOKEN` |
| Authorization substrate outage | Gate 4 fails closed. `ResourceAuthorizer` turns an unavailable SpiceDB into a denial, never an allow. | `SEC-SPICEDB-UNAVAILABLE` |
| Stale authorization after revocation | Gate 4 reads `fully_consistent`, so removing a relationship denies the next request immediately, without a re-login. | `SEC-RELATIONSHIP-REMOVAL-IMMEDIATE` |
| Replay or double-charge at checkout | Checkout claims the idempotency key and body before calling payment. Same key with the same body returns the same order without re-authorizing payment; same key with a different body is rejected before payment is called. | `SEC-CHECKOUT-IDEMPOTENT-REPLAY`, `SEC-CHECKOUT-IDEMPOTENCY-COLLISION` |

## Notes on the controls

The browser is untrusted. It holds a session cookie and nothing else. Identity and
`Authorization` headers from the browser are stripped at the gateway, so a token cannot be smuggled
inward and a token cannot leak outward. `SEC-NO-BROWSER-TOKENS` asserts the boundary end to end
across login, cart, checkout, and logout; the only place an `id_token` may appear is the server's
logout redirect to the IdP, as `id_token_hint`.

Scope and relationship answer different questions. A scope says the caller may perform a class of
operation; a relationship says the caller may act on a specific object. Because the two gates are
checked independently, neither one can stand in for the other. Persistence stores ownership columns
as business data only; they are never consulted for access. Gate 4 is the only resource authority.

Resource ids are generated server-side. A cart is created on first add: the service generates the
id, writes `cart:{id}#owner@user:{subject}` for the authenticated subject through
`ResourceAuthorizer`, then persists. If that relationship write fails, the operation fails closed
and no usable cart exists without its ownership relationship
(`SEC-OWNERSHIP-PROVISIONED-FOR-CALLER`, `SEC-PROVISIONING-FAILS-CLOSED`).

Checkout is the one cross-service call. Order Service obtains its own client-credentials token and
calls Payment Service on the internal network. The user's token never travels to Payment, and
Payment is not reachable through the browser gateway, so a user cannot drive payment directly.

## Out of scope

- **No real payment processor.** Payment Service authorizes against a stub; nothing settles money.
- **No multi-tenant model.** There is a single store and one set of relationships in v1.
- **Deployment and infrastructure hardening.** Connection pooling, high availability, service
  mesh, secret management, supply-chain pinning, and the like are deployment concerns and live in
  [production-hardening.md](production-hardening.md), not in this model.
