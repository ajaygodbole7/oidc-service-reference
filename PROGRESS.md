# oidc-service-reference progress

This is the cross-session handoff artifact for agents and humans. `PLAN.md` is the
product/build contract; `AGENTS.md` is the operating method. Update this file at the end
of every work session.

## Required handoff schema

Every session must leave these sections populated:
- `Current slice`: the active slice from `PLAN.md`.
- `Exact next action`: the next concrete action another agent should take.
- `Acceptance gate`: the verifier target from `PLAN.md`'s per-slice acceptance matrix.
- `Build checklist`: slice checklist, with only verified slices checked.
- `Verifier status`: last command(s), result, and whether the slice can advance.
- `Blockers`: missing dependencies, failing gates, unresolved decisions, or environment issues.
- `Session handoff`: short continuity note for Claude, Codex, subagents, or humans.
- `Parallel agents`: active subagents, ownership scopes, returned verifier output, and
  integration blockers once fan-out begins.

## Current slice

Scaffold from `../oidc-reference`.

Goal: copy/adapt the BFF front-door, frontend shell, Keycloak realm, compose substrate,
verification scripts, and harness foundations without changing the token model.

## Exact next action

Run the remaining scaffold proof after the disk cleanup:

```sh
corepack pnpm run typecheck
sh scripts/up.sh
sh scripts/verify-all.sh
sh scripts/e2e-auth.sh
```

Docker was pruned aggressively after the first successful boot because the host disk was
nearly full, so the next `scripts/up.sh` run will repull/rebuild images.

Do not create runnable `scripts/agent-init.sh` or `scripts/agent-loop.sh` yet. Do not
start the Postgres persistence slice yet.

## Acceptance gate

The scaffold slice can advance only when:
- copied/adapted BFF front-door boots locally
- baseline auth flow runs through the gateway
- copied `assertNoBrowserTokens` guard passes
- generated manifests use exact version pins from `PLAN.md`
- initial `scripts/verify-all.sh` exists and reports the current scaffold state honestly
  with stable check IDs, bounded/masked output, service-health summary, and explicit
  pending/skipped states for checks not implemented yet

## Build checklist

- [ ] Scaffold from `oidc-reference`: Auth Service, APISIX gateway, frontend shell,
      Keycloak realm, compose, scripts, and test harness.
- [x] Bring up Keycloak, Valkey, APISIX, Auth Service, and frontend shell.
- [ ] Add SpiceDB, schema, and seed relationships.
- [ ] Add `commerce-security-common`.
- [ ] Add `AuthorizationClient` and fake/test implementation.
- [ ] Build the cart vertical slice: JWT gate, scope gate, SpiceDB resource gate,
      trace writes, cart UI, architecture checks, and core security-verification cases.
- [ ] Parallel fan-out after cart: catalog-service agent, order-service agent,
      payment-service agent, frontend workflow agent, and harness/security agent work under
      explicit ownership scopes.
- [ ] Build catalog: anonymous reads, `SEC-CATALOG-ANONYMOUS-READ-ONLY`, merchant writes,
      and `store#manage` check.
- [ ] Build order and payment: checkout, S2S client credentials, idempotency, support
      read, owner cancel, and payment rejection scenarios.
- [ ] Add Postgres persistence: local Postgres, per-service database/schema,
      Flyway migrations, Spring Data JDBC repository implementations, and
      Postgres-backed order/payment idempotency.
- [ ] Complete platform verification: SpiceDB-unavailable fail-closed, full
      security-verification suite, Security Trace evidence, stable harness check catalog,
      architecture checks, bounded/masked evidence, service-health summaries, and final
      docs.

## Verifier status

Static scaffold verifier passed:

```sh
sh scripts/verify-all.sh
```

Result: PASS for scaffold file presence, exact version pins, no active copied
`backend-resource-server` routes, no floating Docker `latest` tags, and no npm version
ranges in `frontend/package.json`. The verifier reports expected pending states for
service health, `SEC-NO-BROWSER-TOKENS`, and later service/security checks.

Cheap config gates:

```sh
docker compose config --quiet
GATEWAY_CLIENT_SECRET=LOCAL_DEV_GATEWAY_CLIENT_SECRET__CHANGE_BEFORE_DEPLOY \
  CSRF_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
  sh scripts/render-apisix-config.sh
```

Result: both passed.

Live stack gate:

```sh
sh scripts/up.sh
```

Result: passed after correcting the APISIX image pin to
`apache/apisix:3.16.0-debian`. Keycloak, Valkey, Auth Service, and APISIX reached
healthy status. The stack was later stopped and Docker images/cache were pruned to
recover disk space.

Live harness checks observed green before pruning:

```sh
sh scripts/verify-idp.sh
sh scripts/verify-api-gateway.sh
```

Result: IDP static/discovery/JWKS/service-token checks passed against the already-running
root Keycloak; APISIX Lua decision-table and CSRF fixture parity checks passed.

Harness changes staged for the next run:
- `scripts/verify-idp.sh` now uses the root Keycloak when it is already running instead
  of starting a second Keycloak on the same port.
- `scripts/verify-all.sh` now reports `HARNESS-SERVICE-HEALTH` as PASS only when the
  required Compose services are healthy.
- `scripts/e2e-auth.sh` now runs the focused `SEC-NO-BROWSER-TOKENS` Playwright proof.
- frontend scripts support `pnpm` through Corepack when no global pnpm shim exists.

Current expected proof for future slices:
- failing test/security case is observed red for the expected reason
- slice implementation is observed green through the relevant live gate
- no-browser-token e2e guard remains green once frontend/auth flow exists

## Parallel agents

Not active yet. Fan-out begins after the cart vertical slice is green and the
orchestrator has frozen the shared contracts for service agents.

## Blockers

- Host disk is still tight after cleanup (`/System/Volumes/Data` had about 7.3 GiB free
  after pruning). Avoid unnecessary Docker rebuild churn.
- Docker was pruned to 0B images/containers/volumes/build-cache, so the next live run
  must repull/rebuild images.
- Live `SEC-NO-BROWSER-TOKENS` proof is implemented but has not been observed green yet.
- Local shell has Node 24.12.0; `frontend/package.json` pins Node 26.3.0, so pnpm emits
  an engine warning until Node is switched.
- Postgres persistence is intentionally deferred until the first cart authorization ladder
  is proven with in-memory repositories.
- Harness evidence belongs in verifier output, tests, scripts, and `PROGRESS.md`.
- Future `scripts/agent-init.sh` and `scripts/agent-loop.sh` are intentionally deferred
  until the stack and verifier exist.

## Session handoff

The scaffold files are in place, static verifier gates pass, and the stack booted once
with Keycloak, Valkey, Auth Service, and APISIX healthy. Docker was then pruned for disk
safety, so the next agent should run `corepack pnpm run typecheck`, `sh scripts/up.sh`,
`sh scripts/verify-all.sh`, and `sh scripts/e2e-auth.sh`. Do not mark the scaffold slice
done until `SEC-NO-BROWSER-TOKENS` is observed green in the live Playwright run.
