# Security behind the scenes

The four-gate ladder is mostly automatic. This document separates what a developer writes from
what the platform does on every request.

## What a frontend developer writes

A call to the backend, through the `auth.ts` helper:

```ts
await fetch("/api/cart/items", {
  method: "POST",
  credentials: "include",
  headers: { "X-XSRF-TOKEN": csrf, "Content-Type": "application/json" },
  body: JSON.stringify(item),
});
```

That is the whole contract. `credentials: "include"` sends the session cookie; the CSRF header
is echoed from the `XSRF-TOKEN` cookie on unsafe methods. The SPA never sees a token, never sets
`Authorization`, and never talks to the IdP. A `401` means the session is gone, and the app
redirects to `/auth/login`.

## What a backend developer writes

A thin controller that receives the already-validated principal, and an application service that
runs the two business gates:

```java
// controller (CartController): receive the validated principal, map, delegate
@PostMapping("/api/cart/items")
CartResponse addItem(
    @RequestAttribute("commercePrincipal") CommercePrincipal principal,   // set by gate 2
    @Valid @RequestBody CartRequest.AddItem request) {
  AddItemCommand command = /* mapped from the request body */;
  return CartResponse.from(service.addItem(principal, command));
}

// application service (CartApplicationService): the two business gates, then the work
scopeAuthorizer.requireScope(principal, CART_WRITE);                       // gate 3
resourceAuthorizer.requireAllowed(principal, resource(cart.id()), WRITE);  // gate 4
repository.save(cart);
```

The developer writes gate 3 and gate 4 explicitly, because the right scope and the right
resource are business decisions. They do not write JWT validation, session handling, CSRF, or
the SpiceDB protocol.

## What the platform does automatically

| Step | Where | Automatic behavior |
| --- | --- | --- |
| Session to token | APISIX `bff-session` | Resolve the `__Host` cookie via `/internal/resolve`, inject the access token, strip client identity and `Authorization` headers, validate CSRF. |
| Gate 2 (JWT) | `CommercePrincipalFilter` | Validate signature, `iss`, `exp`, `aud=commerce-api`; expose only the mapped `CommercePrincipal` to controllers. |
| Gate 4 transport | `AuthorizationClient` | Make the SpiceDB check. The service calls `ResourceAuthorizer`, never gRPC directly. |
| Trace | Security trace | Record the gate decisions under a bounded `X-Trace-Id`, with no token material. |

A developer reaches the application-service body only after gates 1 and 2 have passed. Gates 3
and 4 are one line each. Everything else is the platform's job, and that is the point: the
security model is hard to get wrong because most of it is not the feature author's code.
