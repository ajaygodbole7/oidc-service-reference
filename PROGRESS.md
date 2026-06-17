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

Start the scaffold slice by inventorying `../oidc-reference` and copying/adapting the
minimum BFF front-door foundation into this repo: Auth Service, APISIX gateway, frontend
shell, Keycloak realm, compose substrate, scripts, and the no-browser-token test harness.
Use the exact stack pins in `PLAN.md`; if scaffold starts after 2026-06-17, refresh those
pins before generating manifests. Do not use floating Docker `latest` tags or npm ranges.

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
- [ ] Bring up Keycloak, Valkey, APISIX, Auth Service, and frontend shell.
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

Blocked until scaffold, compose, and `scripts/verify-all.sh` exist in this repo.

Current expected proof for future slices:
- failing test/security case is observed red for the expected reason
- slice implementation is observed green through the relevant live gate
- no-browser-token e2e guard remains green once frontend/auth flow exists

## Parallel agents

Not active yet. Fan-out begins after the cart vertical slice is green and the
orchestrator has frozen the shared contracts for service agents.

## Blockers

- No scaffolded app code exists yet.
- No compose stack exists yet.
- No runnable verifier exists yet.
- Postgres persistence is intentionally deferred until the first cart authorization ladder
  is proven with in-memory repositories.
- Harness evidence belongs in verifier output, tests, scripts, and `PROGRESS.md`.
- Future `scripts/agent-init.sh` and `scripts/agent-loop.sh` are intentionally deferred
  until the stack and verifier exist.

## Session handoff

The next agent or human should begin with the scaffold slice and the exact next action
above. Keep `PLAN.md` as the product/build contract, use `AGENTS.md` as the operating
contract, use this file for continuity, and update it before ending the session.
