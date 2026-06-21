# Token model

The browser holds no tokens. This document covers what tokens exist, where they live, and how
they reach a service.

## The browser holds one opaque cookie

After login the browser has an opaque `__Host` session cookie and a CSRF cookie. The session
cookie carries no claims and no token material; it is an id into a server-side session in Valkey.
Access and refresh tokens are stored in that session, keyed by the session id, and are never
sent to the browser.

The `XSRF-TOKEN` cookie is a signed double-submit CSRF token bound to the session. The SPA
echoes it in the `X-XSRF-TOKEN` header on unsafe methods, and the gateway validates it.

## User tokens (browser-originated requests)

For `/api/**`, the gateway resolves the session and injects the user's access token as a bearer.
The token is issued by Keycloak (the `commerce-auth` client) and carries:

- `aud=commerce-api`: gate 2 rejects any other audience.
- scopes such as `cart:read`, `cart:write`, `catalog:write`, `orders:read`, `orders:write`:
  gate 3 checks these.

This is the phantom-token pattern: an opaque session at the edge, a real JWT inside, injected by
the gateway after the browser is out of the path.

## Service tokens (order to payment)

Checkout requires a server-to-server call. Order Service requests a client-credentials token
from Keycloak as `client_id=order-service` and calls Payment Service with it. The token carries:

- `aud=payment-service`: Payment rejects any token not minted for it.
- `azp`/`client_id=order-service`: Payment can attribute the call to Order Service.
- scope `payments:authorize`.

Payment Service validates the audience and rejects a user (`commerce-api`) token outright
(`SEC-PAYMENT-WRONG-CLIENT`, `SEC-PAYMENT-REJECTS-USER-TOKEN`). Payment has no browser route
(`SEC-PAYMENT-NO-BROWSER-ROUTE`).

## The token boundary

The invariant the live suite holds:

- Access and refresh tokens never reach the browser. They live only in the server-side session
  store.
- The SPA never names a token, sets an `Authorization` header, calls the IdP token endpoint, or
  writes auth state to web storage. `scripts/verify-architecture.sh` enforces this on SPA
  source.
- The gateway strips client-supplied identity and `Authorization` headers and injects the real
  bearer. A browser-supplied bearer is overwritten, not trusted
  (`SEC-BROWSER-AUTHORIZATION-OVERWRITTEN`, `SEC-SPOOFED-IDENTITY-HEADERS`).
- `SEC-NO-BROWSER-TOKENS` asserts the boundary end to end.

## Validation, not delegation

Both the user-token gate and the payment service-token check use focused Nimbus validators with
explicit issuer, audience, expiry, and JWS type (`JWT` or `at+JWT`, per RFC 9068) checks, rather
than a framework resource-server default. The accepted audience and token type are asserted in
code, so a dependency bump cannot silently relax them to accept the wrong token.
