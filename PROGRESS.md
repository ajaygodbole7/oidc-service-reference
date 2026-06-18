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

Cart vertical slice.

Scaffold from `../oidc-reference` is DONE and accepted (2026-06-17 18:15): stack boots
healthy and `SEC-NO-BROWSER-TOKENS` passed live. Authorization substrate is DONE and
accepted (2026-06-17 22:10): SpiceDB starts, schema loads, seed relationships apply,
fake-vs-real adapter contract passes, and unavailable SpiceDB denies through
`ResourceAuthorizer`.

## Exact next action

Cart vertical slice is NEXT. Use the accepted scaffold + authorization substrate to build
the first real business flow through all four gates.

Start with cart-service scaffold and a failing security/contract case before app code:

```sh
sh scripts/verify-spicedb-live.sh
sh scripts/verify-commerce-security-common.sh
```

Then add `cart-service` with:
1. JWT validation using `CommerceJwtValidator`.
2. explicit `ScopeAuthorizer` for `cart:read` / `cart:write`.
3. explicit `ResourceAuthorizer` checks for `cart:{id}#read/write@user:{sub}`.
4. trace evidence with bounded/masked gate results.
5. gateway route + frontend cart UI only after the service gate is proven.

Do not create runnable `scripts/agent-init.sh` or `scripts/agent-loop.sh` yet. Do not
start the Postgres persistence slice yet.

## Acceptance gate

The cart slice can advance only when:
- cart UI and cart-service run through the gateway
- JWT, scope, and SpiceDB gates are traced
- `SEC-NO-BROWSER-TOKENS`, `SEC-NON-COMMERCE-AUD`,
  `SEC-SCOPE-WITHOUT-RELATIONSHIP`, `SEC-RELATIONSHIP-WITHOUT-SCOPE`,
  `SEC-SPOOFED-IDENTITY-HEADERS`, `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN`, and
  `SEC-RELATIONSHIP-REMOVAL-IMMEDIATE` pass live
- architecture checks for controller/service/domain/persistence/security boundaries pass
- no-browser-token guard remains green

## Build checklist

- [x] Scaffold from `oidc-reference`: Auth Service, APISIX gateway, frontend shell,
      Keycloak realm, compose, scripts, and test harness. (Accepted 2026-06-17 18:15 —
      stack boots healthy, `SEC-NO-BROWSER-TOKENS` green live.)
- [x] Bring up Keycloak, Valkey, APISIX, Auth Service, and frontend shell.
- [x] Add SpiceDB, schema, and seed relationships. (Accepted 2026-06-17 22:10 —
      `scripts/verify-spicedb-live.sh` green.)
- [x] Add `commerce-security-common`: focused JOSE JWT validator, scope/resource
      authorizers, decision trace, fake and real SpiceDB adapter. (Accepted
      2026-06-17 22:10.)
- [x] Add `AuthorizationClient` and fake/test implementation. (Accepted 2026-06-17
      22:10 — fake-vs-real contract and unavailable denial proof green.)
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

`commerce-security-common` JWT primitive — `clean test` green on host JDK 26
(2026-06-17 19:19): enforcer rules pass (Java 26, dependencyConvergence, banned deps);
`CommerceJwtValidatorTest` 10/10 (valid, at+JWT, single-aud string, bad sig, wrong iss,
wrong aud, expired, bad typ, multi-aud-without-azp, multi-aud-with-azp). Teeth proven:
neutering the azp guard turns `rejects_multi_audience_without_azp` red.

Current local primitive additions verified 2026-06-17 21:20 PDT:

```sh
sh scripts/verify-commerce-security-common.sh
sh scripts/verify-all.sh
```

Result: PASS. `commerce-security-common` ran 16 tests (10 JWT real-crypto/adversarial
tests + 6 scope/resource authorizer tests), enforcer-clean on JDK 26. `verify-all.sh`
now includes `HARNESS-COMMERCE-SECURITY-COMMON-PRESENT` plus the common-module verifier.
It reports expected PENDING states for the stopped Compose stack, later `SEC-*` checks,
and SpiceDB-dependent checks.

SpiceDB static substrate verified 2026-06-17 21:24 PDT:

```sh
sh scripts/verify-spicedb-static.sh
docker compose config --quiet
sh scripts/verify-all.sh
```

Result: PASS. Compose includes `ghcr.io/authzed/spicedb:v1.53.0` (verified as the current
GitHub latest release before adding it), `authorization-service/schema.zed` matches the
planned `user`/`store`/`cart`/`order` schema, and `authorization-service/seed.relationships`
contains the planned local fixture relationships.

Authorization substrate ACCEPTED 2026-06-17 22:10 PDT:

```sh
sh scripts/verify-spicedb-live.sh
sh scripts/verify-all.sh
```

Result: PASS. `scripts/verify-spicedb-live.sh` started
`ghcr.io/authzed/spicedb:v1.53.0`, loaded `authorization-service/schema.zed`, applied seed
relationships through the official Authzed Java SDK (`com.authzed.api:authzed:1.6.0`),
proved fake-vs-real authorization parity, and proved unavailable SpiceDB denies through
`ResourceAuthorizer`. `verify-all.sh` passed implemented local checks and reported expected
PENDING states for the stopped BFF stack/domain-service `SEC-*` gates.

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

- Host disk tight: `/System/Volumes/Data` at 6.8 GiB free / 97% used after the SpiceDB
  pull (2026-06-17 22:11). A full `up.sh` rebuild succeeded at 7.3 GiB earlier this
  session, but margin is thin — an ENOSPC mid-build can brick Docker and the shell. Tear
  the stack down (`scripts/down.sh`) when idle, and free space before the cart full-stack
  run or any Postgres image pull.
- Docker now has the pinned SpiceDB image locally. `docker system df` showed 2.168 GiB of
  images and 704.5 MiB build cache after the live substrate proof.
- Local shell has Node 24.12.0; `frontend/package.json` pins Node 26.3.0, so pnpm emits
  an engine warning until Node is switched.
- Postgres persistence is intentionally deferred until the first cart authorization ladder
  is proven with in-memory repositories.
- Harness evidence belongs in verifier output, tests, scripts, and `PROGRESS.md`.
- Future `scripts/agent-init.sh` and `scripts/agent-loop.sh` are intentionally deferred
  until the stack and verifier exist.

## Session handoff

The scaffold and authorization-substrate slices are accepted. The next agent should start
the cart vertical slice with a failing service/security case, then scaffold `cart-service`
against the existing `commerce-security-common` primitives and SpiceDB seed data. Keep the
four gates visible in code and traces. Do not start Postgres; cart still uses in-memory
repositories until the ladder is proven.

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
- 2026-06-17 21:17 PDT — Codex — fixed stale handoff state and continued the
  authorization-substrate slice locally: added `ScopeAuthorizer`, `ResourceAuthorizer`,
  `DecisionTrace`, `AuthorizationClient` value objects/port, an in-memory fake, module-local
  `AGENTS.md` + `CLAUDE.md` stubs for `auth-service`, `frontend`, and
  `commerce-security-common`, plus `scripts/verify-commerce-security-common.sh` wired into
  `scripts/verify-all.sh`. `sh scripts/verify-commerce-security-common.sh` and
  `sh scripts/verify-all.sh` passed; root harness now honestly gates the common module while
  keeping SpiceDB and later service checks PENDING until implemented.
- 2026-06-17 21:24 PDT — Codex — added the static SpiceDB substrate: pinned
  `ghcr.io/authzed/spicedb:v1.53.0` Compose service, `authorization-service/schema.zed`,
  `authorization-service/seed.relationships`, module-local `AGENTS.md` + `CLAUDE.md`, and
  `scripts/verify-spicedb-static.sh` wired into `verify-all.sh`. Verified
  `docker compose config --quiet`, `sh scripts/verify-spicedb-static.sh`, and
  `sh scripts/verify-all.sh` green for implemented local checks. Did not run live Compose
  image pull/start because disk remains a recorded blocker; schema load, seed apply, real
  SpiceDB adapter, and unavailable-SpiceDB denial proof remain.
- 2026-06-17 22:10 PDT — Codex — completed and accepted the authorization-substrate slice.
  Added official Authzed Java SDK `1.6.0` + gRPC Java `1.72.0`, real
  `SpiceDbAuthorizationClient`, live fake-vs-real contract test, unavailable-SpiceDB
  fail-closed proof, and `scripts/verify-spicedb-live.sh`. First live run exposed invalid
  `--grpc-no-tls` flag for SpiceDB v1.53.0; removed it because plaintext is default unless
  TLS cert/key flags are provided. Re-run passed: schema loaded, seed relationships applied,
  fake-vs-real parity green, unavailable path denies. `verify-all.sh` passed implemented
  checks; full BFF stack/domain-service `SEC-*` checks remain PENDING until cart exists.
  Stopped the SpiceDB container after verification; the image remains cached.
