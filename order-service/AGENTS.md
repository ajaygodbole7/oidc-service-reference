# AGENTS.md - order-service (Spring Boot)

Root AGENTS.md owns the shared invariants, build method, and hard rules. This file adds
only what is specific to order-service; it does not restate them.

## What this is
Order Service owns checkout, order read, and order cancel workflows. It exercises the
four-gate ladder for browser-facing order APIs and calls Payment Service through a port.

## Build & test (this module)
```sh
mvn -f order-service/pom.xml clean test
```

If `commerce-security-common` is not already installed locally, build it first from the
root reactor or install that module before running this module standalone.

## Boundary rules
- Controllers stay thin: translate HTTP requests into commands and responses only.
- `OrderApplicationService` owns gate 3 scope checks and gate 4 resource checks.
- Checkout resolves the current user's cart server-side; it never accepts a client cart id.
- Idempotency is claimed before `PaymentClient` is called. Same key and same request returns
  the same order; same key and different request fails before payment.
- Payment is a port from this module's perspective. The real S2S token gate belongs to
  payment-service and the live order/payment harness.

## Security contract / SEC-* cases proven here
- `SEC-CHECKOUT-IDEMPOTENT-REPLAY`
- `SEC-CHECKOUT-IDEMPOTENCY-COLLISION`
- `SEC-ORDER-OWNER-CANCEL`
- `SEC-ORDER-SUPPORT-READ-NOT-CANCEL`

## Gotchas
- Order persistence is Postgres-backed in this slice. Checkout idempotency uses a unique
  `(subject, idempotency_key)` claim row, then links the committed order in the
  `OrderCheckoutPersistence` transaction.
- `CartLookup` remains a local fixture/port in order-service; cart ownership is still
  enforced by the explicit SpiceDB cart read gate before payment.
- A support user may read an order only when SpiceDB grants `order#read`; support cancel is
  denied unless SpiceDB grants `order#cancel`.
