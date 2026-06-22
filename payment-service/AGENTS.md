# AGENTS.md - payment-service (Spring Boot 4 / Java 26)

Root AGENTS.md owns the shared invariants, build method, and hard rules. This file adds
only payment-service-specific constraints.

## What this is

Internal payment authorization service. It is not browser-facing. Order Service calls
`POST /internal/payments/authorize` with a client-credentials JWT and a structured payment
command.

## Build & test (this module)

The module is intentionally not wired into the root reactor until the orchestrator updates
the root harness:

```sh
mvn -f commerce-security-common/pom.xml clean install
mvn -f payment-service/pom.xml clean test
```

## Boundary rules

- Do not expose `/internal/payments/**` through APISIX.
- Do not accept user `commerce-api` tokens here.
- Validate the service token with the tiny common `ServiceJwtValidator`: issuer, RS256,
  `typ` in `JWT|at+JWT`, `aud=payment-service`, `payments:authorize`, and caller identity
  `azp/client_id/appid=order-service`.
- Treat `userSub` as command data from Order Service, not as direct user proof.
- Require `Idempotency-Key`; same key and same body returns the same authorization, same
  key and different body fails before any new authorization is created.
- Persistence is Spring Data JDBC over Postgres (the persistence slice is done).
