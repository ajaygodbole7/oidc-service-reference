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
- `Session log`: append-only, timestamped chronology of what each session did — commits and
  non-commit events alike (boots, prunes, discoveries, gate results). Newest at the bottom;
  use local time from `date`.

## Current slice

Authorization substrate (SpiceDB + `commerce-security-common`).

Scaffold from `../oidc-reference` is DONE and accepted (2026-06-17 18:15): stack boots
healthy and `SEC-NO-BROWSER-TOKENS` passed live. Next slice adds SpiceDB to Compose with
schema + seed, and the `commerce-security-common` module (the five primitives + a JOSE-based
`CommerceJwtValidator` behind the `AuthorizationClient` port). The cart slice consumes it.

## Exact next action

Authorization-substrate slice IN PROGRESS. Done so far (2026-06-17 19:19): the
`commerce-security-common` module exists with the gate-2 JWT-validation primitive
(`CommerceJwtValidator`, `CommercePrincipal`, `InvalidTokenException`) — `mvnw clean test`
green on host JDK 26, enforcer-clean, 10 adversarial real-crypto tests, teeth-proven.

Build it standalone on the host (no Docker): `JAVA_HOME=~/.sdkman/candidates/java/26-amzn`
then a Maven wrapper against `commerce-security-common/pom.xml`. Always `clean test` — a
`mv`-style revert can regress source mtime and leave a stale `.class`.

Remaining for this slice:
1. Finish `commerce-security-common`: `ScopeAuthorizer`, `ResourceAuthorizer`, `DecisionTrace`,
   the `AuthorizationClient` port (+ `SubjectRef`/`ResourceRef`/`Permission`/`Relationship`),
   and an in-memory fake — all unit-tested.
2. Add SpiceDB to `compose.yaml` (pinned image) with a health gate; add `schema.zed` + seed.
3. Add the real `SpiceDbAuthorizationClient` adapter + a live fake-vs-real contract test.
4. Add a build entry (root aggregator or verify-all integration) and flip
   `SEC-SPICEDB-UNAVAILABLE` + the substrate checks from PENDING to live.

Do not start the Postgres persistence slice yet.

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

- [x] Scaffold from `oidc-reference`: Auth Service, APISIX gateway, frontend shell,
      Keycloak realm, compose, scripts, and test harness. (Accepted 2026-06-17 18:15 —
      stack boots healthy, `SEC-NO-BROWSER-TOKENS` green live.)
- [x] Bring up Keycloak, Valkey, APISIX, Auth Service, and frontend shell.
- [ ] Add SpiceDB, schema, and seed relationships.
- [ ] Add `commerce-security-common` (in progress: gate-2 JWT primitive done + green;
      authorizers, `AuthorizationClient` port + fake, `DecisionTrace` remain).
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

`commerce-security-common` JWT primitive — `mvnw clean test` green on host JDK 26
(2026-06-17 19:19): enforcer rules pass (Java 26, dependencyConvergence, banned deps);
`CommerceJwtValidatorTest` 10/10 (valid, at+JWT, single-aud string, bad sig, wrong iss,
wrong aud, expired, bad typ, multi-aud-without-azp, multi-aud-with-azp). Teeth proven:
neutering the azp guard turns `rejects_multi_audience_without_azp` red.

Scaffold slice ACCEPTED 2026-06-17 18:15 — all gates green live, in a watched run:
- `sh scripts/up.sh` — stack boots healthy (APISIX 3.16.0-debian, Keycloak 26.6.3, Valkey
  9.1.0, JDK-26 auth-service).
- `sh scripts/verify-all.sh` — all `HARNESS-*` PASS incl. `HARNESS-SERVICE-HEALTH` (live).
- `sh scripts/e2e-auth.sh` — `SEC-NO-BROWSER-TOKENS` PASS (Playwright chromium, 10.4s).
  Required a fix: `frontend/playwright.config.ts` webServer command `pnpm run dev` →
  `corepack pnpm run dev` (no global pnpm shim on this host).

Frontend `typecheck` PASS (2026-06-17 17:40; machine Node 24, engine warning vs pinned Node
26.3.0). No type errors.

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

- Host disk tight: `/System/Volumes/Data` at 6.4 GiB free / 97% used (2026-06-17 18:15,
  stack running). A full `up.sh` rebuild succeeded at 7.3 GiB this session, but margin is
  thin — an ENOSPC mid-build can brick Docker and the shell. Tear the stack down
  (`scripts/down.sh`) when idle, and free space before adding the SpiceDB/Postgres images.
- Docker was pruned to 0B images/containers/volumes/build-cache, so the next live run
  must repull/rebuild images.
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

## Session log

Append-only, timestamped chronology (newest at the bottom); captures non-commit events too.

- 2026-06-17 16:42 PDT — Codex — committed docs/agent-loop baseline (`58f9c73`).
- 2026-06-17 16:55 PDT — Codex — committed BFF front-door scaffold (`d8dc180`): auth-service,
  APISIX auth-only routes, renamed Keycloak realm, frontend shell, honest `verify-all.sh`;
  pruned portability/conformance/distributed-lock harnesses.
- 2026-06-17 (16:55–17:34) PDT — Codex — first live boot. Found `apache/apisix:3.17.0` does
  not exist; corrected the pin to `3.16.0-debian` across PLAN.md, compose.yaml, the Lua test,
  and verify-all. Stack then booted green: JDK 26 auth-service built; Keycloak 26.6.3, Valkey
  9.1.0, APISIX 3.16.0 healthy. `verify-idp.sh` + `verify-api-gateway.sh` passed live. Patched
  `verify-idp.sh` (use the running root Keycloak, no second instance) and `verify-all.sh` (real
  service health). Fixed the Vite `/auth` proxy to APISIX `:9080`. Authored the
  `SEC-NO-BROWSER-TOKENS` Playwright proof + `e2e-auth.sh` wiring; added a Corepack `run_pnpm`
  helper and committed `pnpm-lock.yaml`. Stopped the stack and pruned Docker to 0B for disk.
- 2026-06-17 17:34 PDT — Codex — committed live-harness fixes (`ce32fa2`); then ran out of tokens.
- 2026-06-17 17:40 PDT — Claude — progress check: tree clean at `ce32fa2`, stack down, disk
  7.3 GiB / 97%. Frontend `typecheck` PASS (machine Node 24). Did not run the full `up.sh`
  rebuild — disk below safe headroom (ENOSPC/brick risk). Live `SEC-NO-BROWSER-TOKENS` still
  unrun → scaffold slice NOT accepted.
- 2026-06-17 18:15 PDT — Claude — user authorized building at 7.3 GiB. Ran `up.sh` (full
  rebuild, no brick; disk 7.3→6.4 GiB), `verify-all.sh` (all `HARNESS-*` PASS live), and
  `e2e-auth.sh`. First e2e run failed: Playwright webServer used bare `pnpm` (not on PATH);
  fixed `frontend/playwright.config.ts` to `corepack pnpm run dev`. Re-run:
  `SEC-NO-BROWSER-TOKENS` PASS (chromium, 10.4s). **Scaffold slice ACCEPTED.** Uncommitted:
  the playwright.config fix + these PROGRESS updates.
- 2026-06-17 19:19 PDT — Claude — started the authorization-substrate slice. Created
  `commerce-security-common` (Maven module, parent Spring Boot 4.1.0, Java 26) with the gate-2
  JWT primitive: `CommerceJwtValidator` (focused Nimbus JOSE — RS256/JWKS, iss,
  `aud=commerce-api` string-or-array, exp, typ in {JWT, at+JWT}, explicit azp-on-multi-aud),
  `CommercePrincipal`, `InvalidTokenException`. `mvnw clean test` green on host JDK 26 —
  enforcer clean, 10/10 adversarial real-crypto tests; teeth proven by mutating the azp guard.
  (Noted the stale-`.class` trap: a `mv`-revert regressed mtime, so a non-clean run reused the
  mutated class — always `clean`.) Committed pom + src. Rest of the module + the SpiceDB live
  half are next.
