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

Order/payment vertical slice — not started.

Scaffold from `../oidc-reference` is DONE and accepted (2026-06-17 18:15): stack boots
healthy and `SEC-NO-BROWSER-TOKENS` passed live. Authorization substrate is DONE and
accepted (2026-06-17 22:10): SpiceDB starts, schema loads, seed relationships apply,
fake-vs-real adapter contract passes, and unavailable SpiceDB denies through
`ResourceAuthorizer`. Cart is now accepted: the cart UI and cart-service run through APISIX,
the JWT/scope/SpiceDB gates are explicit, all cart `SEC-*` cases passed live, and the
no-browser-token guard remains green. Dynamic ownership is accepted: first add resolves the
cart by current subject, generates the cart id server-side, writes `cart:{id}#owner@user:{sub}`
through `ResourceAuthorizer`, then persists; later reads/writes use the normal fully
consistent SpiceDB gate-4 check.

Catalog is accepted (2026-06-20 07:35 PDT): anonymous reads work through APISIX without a
bearer token, anonymous writes deny, authenticated non-merchant writes deny, and merchant
writes succeed only through `catalog:write` plus SpiceDB `store:main#manage`.

## Exact next action

Before writing order/payment code, tighten the order/payment acceptance map in `PLAN.md` and
the harness checklist so parallel service agents can work without guessing. Pin the first
order/payment slice to:

```sh
SEC-PAYMENT-NO-BROWSER-ROUTE
SEC-PAYMENT-WRONG-CLIENT
SEC-PAYMENT-REJECTS-USER-TOKEN
SEC-CHECKOUT-IDEMPOTENT-REPLAY
SEC-CHECKOUT-IDEMPOTENCY-COLLISION
```

Then fan out order-service, payment-service, frontend workflow, and harness/security agents
under the ownership rules in `AGENTS.md`. Keep testing sequential when Maven `clean` or the
full Compose stack is involved.

Useful acceptance evidence to preserve:

```sh
sh scripts/verify-catalog-service.sh
sh tests/security/verify-catalog-security-draft.sh
cd frontend && ./node_modules/.bin/tsc --noEmit
cd frontend && ./node_modules/.bin/vitest run src/App.test.tsx src/auth.test.ts src/architecture.test.ts
sh tests/security/verify-catalog-security-live.sh SEC-CATALOG-ANONYMOUS-READ-ONLY
sh scripts/verify-cart-service.sh
sh tests/security/verify-cart-security-draft.sh
sh scripts/verify-all.sh
SMOKE_SKIP_DISCOVERY=1 sh authorization-server/tests/smoke.sh
docker compose config --quiet
cd frontend && corepack pnpm run typecheck
cd frontend && corepack pnpm exec vitest run src/App.test.tsx src/auth.test.ts src/architecture.test.ts
sh scripts/verify-cart-spicedb-live.sh
E2E_CART_SKIP_UP=1 sh scripts/e2e-cart.sh
sh tests/security/verify-cart-security-live.sh SEC-SPOOFED-IDENTITY-HEADERS
sh tests/security/verify-cart-security-live.sh SEC-SCOPE-WITHOUT-RELATIONSHIP SEC-BROWSER-AUTHORIZATION-OVERWRITTEN SEC-RELATIONSHIP-REMOVAL-IMMEDIATE SEC-RELATIONSHIP-WITHOUT-SCOPE SEC-NON-COMMERCE-AUD
sh tests/security/verify-cart-security-live.sh SEC-OWNERSHIP-PROVISIONED-FOR-CALLER SEC-NO-RESOURCE-HIJACK SEC-PROVISIONING-FAILS-CLOSED
cd frontend && E2E_FULL_STACK=1 corepack pnpm exec playwright test tests/e2e/auth.spec.ts --grep SEC-NO-BROWSER-TOKENS
```

Next concrete implementation steps:
1. Keep verifier runs sequential when they invoke Maven `clean`; parallel Maven gates can
   delete each other's reactor outputs.
2. Do not rerun the full stack while free disk is near 3 GiB; Claude observed the catalog
   stack run falling to roughly 0.5 GiB free.
3. Do not rerun the full stack while free disk is near 3 GiB unless the next live gate needs
   it; image rebuilds can drop the host below 1 GiB.
4. Start order/payment only after the acceptance map above is explicit enough for subagents.

Do not create runnable `scripts/agent-init.sh` or `scripts/agent-loop.sh` yet. Do not
start the Postgres persistence slice yet.

## Acceptance gate

The next order/payment slice can advance only when the first checkout/payment gates pass in
a watched live run:
- browser traffic cannot reach `/internal/payments/**`
- Payment Service accepts only Order Service client-credentials tokens with
  `aud=payment-service`, `azp/client_id=order-service`, and `payments:authorize`
- user/browser `commerce-api` tokens are rejected by Payment Service
- checkout requires `orders:write`, server-side current-cart resolution, SpiceDB
  `cart:{resolvedCartId}#read@user:{sub}`, and an idempotency key
- same idempotency key plus same body returns the same order without double-authorizing
  payment
- same idempotency key plus different body rejects before payment is called

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
- [x] Build the cart vertical slice: JWT gate, scope gate, SpiceDB resource gate,
      trace writes, cart UI, architecture checks, and core security-verification cases.
      (Accepted 2026-06-18 22:46 PDT — cart-service + frontend tests green,
      live SpiceDB proof green, `SEC-NO-BROWSER-TOKENS` green, and all cart live
      `SEC-*` cases green.)
- [x] Add dynamic cart ownership provisioning: create-on-first-add writes the SpiceDB owner
      relationship for the authenticated subject through `ResourceAuthorizer`, uses a
      server-generated cart id, proves the next access through fully consistent gate 4,
      and fails closed when relationship writes fail. (Accepted 2026-06-19 17:07 PDT.)
- [x] Start parallel fan-out after cart: catalog-service agent, frontend workflow agent,
      and harness/security agent worked under explicit ownership scopes for the catalog
      slice. (Started 2026-06-19; catalog accepted 2026-06-20.)
- [x] Build catalog: anonymous reads, `SEC-CATALOG-ANONYMOUS-READ-ONLY`, merchant writes,
      and `store#manage` check. (Accepted 2026-06-20 07:35 PDT — catalog service local
      gates green, frontend typecheck/Vitest green, `verify-all.sh` local gates green, and
      live catalog browser/gateway harness passed all three cases.)
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

Cart scaffold local gates verified 2026-06-17 22:31 PDT:

```sh
sh scripts/verify-cart-service.sh
sh tests/security/verify-cart-security-draft.sh
HARNESS_PROFILE=local sh tests/security/verify-cart-security-draft.sh
sh scripts/verify-all.sh
cd frontend && corepack pnpm run typecheck
cd frontend && corepack pnpm exec vitest run src/App.test.tsx
```

Result: PASS. `verify-cart-service.sh` builds `commerce-security-common` and `cart-service`
in a root Maven reactor; cart-service tests ran 6/6 green and keep scope/resource gates
visible. Frontend cart component tests ran 6/6 green. Draft security harness reports stable
`SEC-*` IDs with actionable PENDING states. Playwright cart e2e could not be re-run in this
main thread because the sandbox blocked binding `127.0.0.1:5173`, and escalation is blocked
by the current Codex usage limit.

Cart local integration gates verified 2026-06-18 16:40 PDT:

```sh
sh scripts/verify-cart-service.sh
sh tests/security/verify-cart-security-draft.sh
SMOKE_SKIP_DISCOVERY=1 sh authorization-server/tests/smoke.sh
GATEWAY_CLIENT_SECRET=LOCAL_DEV_GATEWAY_CLIENT_SECRET__CHANGE_BEFORE_DEPLOY \
  CSRF_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
  sh scripts/render-apisix-config.sh
docker compose config --quiet
cd frontend && corepack pnpm run typecheck
cd frontend && corepack pnpm exec vitest run src/App.test.tsx src/auth.test.ts src/architecture.test.ts
sh scripts/verify-cart-spicedb-live.sh
docker compose stop spicedb
sh scripts/verify-all.sh
```

Result: PASS for local/static gates. `verify-cart-service.sh` ran 19 common tests
(1 live SpiceDB test skipped in the normal local run) and 15 cart tests. The frontend
typecheck passed with the known local Node 24 vs pinned Node 26 warning; focused Vitest ran
33 tests green. The narrow cart SpiceDB live gate passed with no skipped live SpiceDB test:
real SpiceDB matched the in-memory fake for seeded cart relationships and unavailable
SpiceDB denied. `verify-all.sh` passed implemented gates and kept full-stack/browser cart
`SEC-*` checks explicitly PENDING until live fixtures and the APISIX cart flow are run.

Cart live browser/gateway proof verified 2026-06-18 17:37 PDT:

```sh
docker builder prune -af
docker compose rm -sf keycloak
docker compose up -d keycloak
docker compose up -d --no-build auth-service cart-service apisix
docker compose restart apisix
sh scripts/verify-cart-spicedb-live.sh
cd frontend && E2E_FULL_STACK=1 corepack pnpm exec playwright test tests/e2e/cart-live.spec.ts
cd frontend && E2E_FULL_STACK=1 corepack pnpm exec playwright test tests/e2e/auth.spec.ts --grep SEC-NO-BROWSER-TOKENS
E2E_CART_SKIP_UP=1 sh scripts/e2e-cart.sh
sh scripts/verify-all.sh
```

Result: PASS after fixes. Live stack health is green for Keycloak, Valkey, SpiceDB,
Auth Service, cart-service, and APISIX. Key fixes: APISIX is restarted after auth/cart
service convergence to avoid stale upstream IPs; protected cart routes attach the
`bff-session` plugin directly so bearer injection runs; Keycloak local users now emit a
stable `sub` from `commerce_sub` so cart-service can authorize `user:alice` against
SpiceDB; `scripts/e2e-cart.sh` supports `E2E_CART_SKIP_UP=1` for already-running stacks.
Live proofs green: `SEC-NO-BROWSER-TOKENS`, `SEC-CART-CURRENT-USER-SPICEDB`, and
`SEC-SCOPE-WITHOUT-RELATIONSHIP`. `verify-all.sh` is green and still reports expected
PENDING states for the remaining not-yet-live cart/security fixtures.

Additional fixes from the 2026-06-18 review pass:
- Cart web error handling now maps validation to 400, missing principal binding to 401,
  and unexpected failures to sanitized `application/problem+json` 500.
- Cart response cent values use `long`; invalid money scale is rejected as a bad request.
- In-memory cart repository returns/stores aggregate copies so tests cannot accidentally
  prove behavior through shared mutable instances.
- `ResourceAuthorizer` preserves unavailable causes and no longer relabels programming
  bugs as authorization outages.
- SpiceDB has a Compose healthcheck, and the missing Guava/Error Prone pins are recorded
  in `PLAN.md` and asserted by `verify-all.sh`.
- Frontend `callApi` rejects off-origin and non-`/api` calls before fetching; mocked cart
  e2e remains only a UI/token-boundary smoke, not a cross-component gate proof.

Cart vertical slice ACCEPTED 2026-06-18 22:46 PDT:

```sh
sh scripts/verify-cart-service.sh
corepack pnpm run typecheck
corepack pnpm exec vitest run src/App.test.tsx src/auth.test.ts src/architecture.test.ts
sh scripts/verify-api-gateway.sh
SMOKE_SKIP_DISCOVERY=1 sh authorization-server/tests/smoke.sh
docker compose config --quiet
sh tests/security/verify-cart-security-live.sh SEC-SPOOFED-IDENTITY-HEADERS
sh tests/security/verify-cart-security-live.sh SEC-SCOPE-WITHOUT-RELATIONSHIP SEC-BROWSER-AUTHORIZATION-OVERWRITTEN SEC-RELATIONSHIP-REMOVAL-IMMEDIATE SEC-RELATIONSHIP-WITHOUT-SCOPE SEC-NON-COMMERCE-AUD
E2E_CART_SKIP_UP=1 sh scripts/e2e-cart.sh
cd frontend && E2E_FULL_STACK=1 corepack pnpm exec playwright test tests/e2e/auth.spec.ts --grep SEC-NO-BROWSER-TOKENS
sh scripts/verify-all.sh
```

Result: PASS. Live proofs green: `SEC-NO-BROWSER-TOKENS`,
`SEC-CART-CURRENT-USER-SPICEDB`, `SEC-SCOPE-WITHOUT-RELATIONSHIP`,
`SEC-RELATIONSHIP-WITHOUT-SCOPE`, `SEC-NON-COMMERCE-AUD`,
`SEC-SPOOFED-IDENTITY-HEADERS`, `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN`, and
`SEC-RELATIONSHIP-REMOVAL-IMMEDIATE`. The missing-scope case initially failed because
Keycloak default client scopes always granted `cart:read`/`cart:write`; the realm now keeps
`api.audience` and cart scopes optional, while Auth Service explicitly requests them in the
normal local profile. The aggregate `verify-all.sh` remains a lightweight local gate and
prints PENDING pointers for live-only cart checks and future services.

Dynamic ownership provisioning accepted 2026-06-19 17:07 PDT:

```sh
sh scripts/verify-cart-service.sh
sh scripts/verify-api-gateway.sh
CART_SECURITY_SKIP_UP=1 sh tests/security/verify-cart-security-live.sh SEC-OWNERSHIP-PROVISIONED-FOR-CALLER SEC-NO-RESOURCE-HIJACK SEC-PROVISIONING-FAILS-CLOSED
```

Result: PASS. The service contract proves first add for a subject with no existing cart
provisions `cart:{serverGeneratedId}#owner@user:{sub}` through the authorization port before
persistence, proves relationship-write failures fail closed without saving the cart, and
proves the fake and real SpiceDB adapters agree that a write is immediately visible to a
subsequent fully consistent check. The live browser/gateway harness proves an authenticated
`admin` first add succeeds without a pre-seeded cart and that attacker-chosen resource ids in
the payload are ignored (`SEC-NO-RESOURCE-HIJACK`). The live loop also found and fixed an
APISIX forwarding bug: `bff-session` was clearing `Content-Length`, causing proxied POSTs to
arrive at cart-service with a missing body.

Catalog local gates verified 2026-06-20 07:18 PDT:

```sh
sh scripts/verify-catalog-service.sh
sh tests/security/verify-catalog-security-draft.sh
./node_modules/.bin/tsc --noEmit
./node_modules/.bin/vitest run src/App.test.tsx src/auth.test.ts src/architecture.test.ts
PATH=/Users/ajaygodbole/.sdkman/candidates/maven/current/bin:/Users/ajaygodbole/.sdkman/candidates/java/current/bin:/usr/bin:/bin:/usr/sbin:/sbin sh scripts/verify-all.sh
```

Result: PASS for local/static/JVM/frontend gates. `verify-catalog-service.sh` ran
`commerce-security-common` 23 tests (2 expected skipped live SpiceDB tests) and
`catalog-service` 15 tests. Frontend typecheck passed and focused Vitest ran 35 tests.
`verify-all.sh` was run with Docker hidden from `PATH` because Docker was not responding
quickly; it passed implemented checks and reported service/live checks as explicit PENDING.
Fixed during this continuation: `compose.yaml` now includes `catalog:write` in the default
`OIDC_SCOPES`, matching `auth-service` config and the live merchant-write requirement.

Catalog slice ACCEPTED 2026-06-20 07:35 PDT:

```sh
sh tests/security/verify-catalog-security-live.sh SEC-CATALOG-ANONYMOUS-READ-ONLY
```

Result: PASS in a watched run. The verifier rebuilt and started the local stack, seeded real
SpiceDB, restarted APISIX, proved the cart/SpiceDB live contract, and ran
`frontend/tests/e2e/catalog-live.spec.ts`. All three browser/gateway cases passed:
anonymous catalog read with write denied, authenticated non-merchant write denied by the
resource gate, and merchant write allowed only with `catalog:write` plus
`store:main#manage`. The stack was torn down immediately after the run; Docker builder cache
and unused images were pruned. Host disk recovered from about 1.0 GiB free to about 4.1 GiB
free, and `docker system df` reported 0B images, containers, volumes, and build cache.

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

Completed 2026-06-18 cart review/fix fan-out:
- Cart-service worker owned cart-service correctness fixes and returned green
  `verify-cart-service.sh`.
- Shared substrate worker owned `commerce-security-common`, SpiceDB health, and pin-table
  fixes; returned green common verifier, static SpiceDB verifier, and Compose config.
- Frontend worker owned `frontend/**`; returned green typecheck, focused Vitest, and
  architecture tests.
- Harness worker owned `tests/security/**` and `scripts/verify-all.sh`; returned green draft
  cart security harness.
- Explorers mapped A1-A4 live wiring and checked for missed HIGH/MED risks.

No subagents are currently active. Cart and catalog are accepted; the next fan-out should
split order-service, payment-service, frontend workflow, and harness/security work by the
documented ownership scopes after the order/payment acceptance map is pinned.

## Blockers

- Host disk tight: `/System/Volumes/Data` at about 4.1 GiB free / 99% used after the
  2026-06-20 catalog acceptance cleanup. The catalog live run dropped free space to about
  1.0 GiB before teardown/prune. Do not run full-stack rebuilds casually; check disk first
  and prune after live gates.
- Local shell has Node 24.12.0; `frontend/package.json` pins Node 26.3.0, so pnpm emits
  an engine warning until Node is switched.
- Postgres persistence is intentionally deferred until order/payment service flows are
  shaped or the human explicitly moves it earlier.
- Harness evidence belongs in verifier output, tests, scripts, and `PROGRESS.md`.
- Future `scripts/agent-init.sh` and `scripts/agent-loop.sh` are intentionally deferred
  until the stack and verifier exist.
- Do not run Maven-cleaning gates in parallel. `verify-all.sh` and `e2e-cart.sh` both call
  Maven clean paths; running them concurrently caused a transient missing-class compile
  failure that disappeared when rerun sequentially.

## Session handoff

The scaffold, authorization-substrate, cart vertical slice, dynamic cart ownership
provisioning, and catalog vertical slice are accepted locally. The next agent should pin the
order/payment acceptance map, then fan out order-service, payment-service, frontend workflow,
and harness/security work under the documented ownership scopes. Keep the four gates visible
in code and traces. Do not start Postgres until order/payment service flows are shaped or the
human explicitly moves persistence earlier.

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
- 2026-06-17 22:31 PDT — Codex — attempted to push commit `f6b62be` after adding `origin`
  as `https://github.com/ajaygodbole7/oidc-service-reference.git`; push failed with
  `Repository not found`. Then ran three parallel workers for the cart slice: cart-service
  module, frontend cart UI, and draft security harness. Integrated their outputs, patched
  cart runtime authz to use real SpiceDB instead of an in-memory fake, aligned cart response
  shape with frontend, removed browser-visible trace details from error responses, added a
  root Maven reactor + `scripts/verify-cart-service.sh`, and wired the draft cart security
  harness into `verify-all.sh`. Sequential verification passed: cart reactor tests,
  frontend typecheck, frontend App Vitest, draft security harness, and `verify-all.sh`.
  Playwright cart e2e remains blocked in this main thread by localhost bind sandboxing and
  the current escalation usage limit.
- 2026-06-18 16:40 PDT — Codex — reviewed Claude's cart-slice findings and ran parallel
  subagents for cart-service fixes, shared substrate fixes, frontend cleanup, harness cleanup,
  A1-A4 mapping, and missed-risk review. Fixed findings B/C/D as needed: cart money/error
  handling, long cent fields, argument-sensitive authz tests, write-denial tests, cart
  `@NullMarked`, copy-on-store in-memory repository, cause-preserving resource authz,
  narrower unavailable handling, graceful SpiceDB shutdown, SpiceDB healthcheck, and missing
  Guava/Error Prone pin docs/assertions. Then implemented A1-A4 local wiring: cart-service
  `CommercePrincipalFilter` using `CommerceJwtValidator`, cart OIDC config, actuator health,
  Dockerfile, Keycloak `cart:read`/`cart:write` scopes, auth-service requested scopes,
  APISIX protected cart routes, Compose cart-service, `scripts/up.sh` cart inclusion, and
  `scripts/verify-cart-spicedb-live.sh`. Verified green: `verify-cart-service.sh`, draft cart
  security harness, static realm smoke, APISIX render, Compose config, frontend typecheck,
  focused frontend Vitest, `verify-cart-spicedb-live.sh` (live SpiceDB, no skipped live test),
  and `verify-all.sh`. Stopped the temporary SpiceDB container. Cart slice remains WIP until
  the full stack/browser/live cart `SEC-*` proofs pass; disk is only 4.7 GiB free.
- 2026-06-18 17:37 PDT — Codex — continued with Docker Desktop up and low disk. Pruned
  Docker builder cache, started the full local stack, and fixed the live cart path. Found
  APISIX retained stale upstream IPs after auth/cart recreate; patched `scripts/up.sh` to
  restart APISIX after service convergence. Added `frontend/tests/e2e/cart-live.spec.ts`
  and `scripts/e2e-cart.sh` for live cart browser checks. First run exposed `/api/cart`
  401s: APISIX was reaching cart-service without a valid service subject. Fixed protected
  cart routes to attach `bff-session` directly, then found Keycloak access tokens lacked
  `sub`; patched the realm to emit stable local `sub` from user `commerce_sub` and added
  static smoke checks. Recreated Keycloak to re-import the realm. Verified green:
  `SEC-NO-BROWSER-TOKENS`, `SEC-CART-CURRENT-USER-SPICEDB`,
  `SEC-SCOPE-WITHOUT-RELATIONSHIP`, `verify-cart-service.sh`,
  `verify-cart-spicedb-live.sh`, `E2E_CART_SKIP_UP=1 sh scripts/e2e-cart.sh`, and
  sequential `verify-all.sh`. A transient parallel `verify-all.sh`/`e2e-cart.sh` Maven
  collision failed once; rerun sequentially passed.
- 2026-06-18 22:48 PDT — Codex — integrated the live cart security fixture work and accepted
  the cart vertical slice locally. Removed unadopted design-note files, added root
  `.dockerignore` to keep repo-root Docker contexts small, and kept Docker build cache pruned
  while disk stayed near-full. Added local/test-only cart relationship/evidence fixtures,
  live cart security verifier, APISIX protected `/api/_test/cart/*` route stripped from
  production-intent renders, SpiceDB relationship touch/delete adapter support, and Playwright
  cases for spoofed headers, browser bearer overwrite, immediate relationship removal,
  missing scope, and non-commerce audience. Found the missing-scope fixture initially failed
  because Keycloak default client scopes always granted cart/audience scopes; moved
  `api.audience`, `cart:read`, and `cart:write` to optional client scopes and kept Auth
  Service requesting them in the normal local profile. Verified green: cart-service Maven
  reactor, frontend typecheck, focused Vitest (33 tests), API gateway verifier, Keycloak
  smoke, `docker compose config --quiet`, `SEC-NO-BROWSER-TOKENS`,
  `SEC-CART-CURRENT-USER-SPICEDB`, `SEC-SCOPE-WITHOUT-RELATIONSHIP`,
  `SEC-RELATIONSHIP-WITHOUT-SCOPE`, `SEC-NON-COMMERCE-AUD`,
  `SEC-SPOOFED-IDENTITY-HEADERS`, `SEC-BROWSER-AUTHORIZATION-OVERWRITTEN`,
  `SEC-RELATIONSHIP-REMOVAL-IMMEDIATE`, `E2E_CART_SKIP_UP=1 sh scripts/e2e-cart.sh`, and
  `sh scripts/verify-all.sh`. Docker build cache is 0B; stack is still running until the
  acceptance commit/teardown step.
- 2026-06-19 17:07 PDT — Codex — implemented and accepted dynamic cart ownership
  provisioning. Added `AuthorizationClient.writeRelationship/deleteRelationship` and
  `ResourceAuthorizer.writeRelationship`, with fake and real SpiceDB adapters using TOUCH
  semantics and a live write→check→delete contract. Cart first-add now resolves by current
  subject only, generates the cart id server-side, writes `cart:{id}#owner@user:{sub}` before
  persistence, and never accepts a client cart id on create; subsequent access uses the normal
  fully consistent gate-4 check. Added service tests for server-generated ids, no save on
  provisioning failure, and bounded provisioning trace evidence. Added `SEC-OWNERSHIP-
  PROVISIONED-FOR-CALLER`, `SEC-NO-RESOURCE-HIJACK`, and
  `SEC-PROVISIONING-FAILS-CLOSED` to the harness. The first live run exposed that APISIX
  `bff-session` cleared `Content-Length`, making proxied POST bodies arrive missing; fixed
  the gateway plugin and verified `sh scripts/verify-api-gateway.sh`. Final watched gates
  green: `sh scripts/verify-cart-service.sh`, `sh scripts/verify-api-gateway.sh`, and
  `CART_SECURITY_SKIP_UP=1 sh tests/security/verify-cart-security-live.sh
  SEC-OWNERSHIP-PROVISIONED-FOR-CALLER SEC-NO-RESOURCE-HIJACK
  SEC-PROVISIONING-FAILS-CLOSED`. Pruned Docker builder cache after rebuild; disk remains
  critically low at about 1.7 GiB free.
- 2026-06-19 21:05 PDT — Claude — catalog vertical slice WIRED + live 2/3; NOT yet accepted
  (blocked by host disk). The catalog-service module (domain/service/web on the four-gate
  ladder) came from Codex workers and is green: 15 module tests + 23 commerce-security-common
  tests. Wired the orchestrator-owned shared contracts: APISIX catalog routes (GET list/detail
  auth-optional with NO `bff-session` for anonymous browsing; POST/PATCH through the full
  `bff-session` ladder), catalog-service in compose + APISIX `depends_on`, `catalog:write`
  realm client scope (optional, Auth Service requests it), a `merchant` Keycloak user
  (`commerce_sub=merchant`) backing the seeded `store:main#manager@user:merchant`, the
  catalog Dockerfile, and the live SEC harness (`tests/security/verify-catalog-security-live.sh`
  + `frontend/tests/e2e/catalog-live.spec.ts`). Found and fixed two real bugs live: (1) adding
  catalog-service to the root reactor broke BOTH cart-service and catalog-service Dockerfiles —
  each copied only its own module POM, so Maven could not parse the reactor; both now copy all
  module POMs. (2) The catalog runner never seeded SpiceDB, so the merchant write failed gate 4;
  the runner now seeds SpiceDB before the tests. Live `SEC-CATALOG-ANONYMOUS-READ-ONLY`: anon
  read 200, anon write deny, and non-merchant (alice) write 403 PASS live through the gateway;
  merchant write (gate-4 `store:main#manage`) is unit-validated (`CatalogApplicationServiceTest`)
  and the seed fix is in place, but the live re-validation is BLOCKED by host disk — the running
  7-container stack consumes GiB while up; free space fell to ~0.5 GiB and a re-run would
  ENOSPC-brick. Committed as a WIP checkpoint, NOT accepted. Next: free host disk to ~6-7 GiB
  (or compact the Docker Desktop disk image), then `sh tests/security/verify-catalog-security-live.sh`
  to confirm the merchant case green and convert to an acceptance commit.
- 2026-06-20 07:18 PDT — Codex — continued from Claude's catalog WIP checkpoint. Found one
  remaining shared-config bug before attempting another live run: `auth-service` default scopes
  included `catalog:write`, but `compose.yaml` overrode `OIDC_SCOPES` without it, so a normal
  local merchant login could fail gate 3 on catalog writes. Patched the Compose default to
  request `catalog:write`. Verified local gates green: `sh scripts/verify-catalog-service.sh`,
  `sh tests/security/verify-catalog-security-draft.sh`, frontend `./node_modules/.bin/tsc
  --noEmit`, frontend focused Vitest (`35` tests), and `verify-all.sh` with Docker hidden from
  `PATH` so it did not hang while Docker was unhealthy. Docker `system df` did not respond
  quickly and was interrupted; host disk was about `3.2 GiB` free, still below the safe live
  stack threshold. Catalog remains WIP, not accepted; next watched gate is the live catalog
  verifier after disk/Docker recovery.
- 2026-06-20 07:35 PDT — Codex — accepted the catalog vertical slice. Pruned Docker builder
  cache and unused images to recover enough space, then ran
  `sh tests/security/verify-catalog-security-live.sh SEC-CATALOG-ANONYMOUS-READ-ONLY` in a
  watched live run. The gate passed all three browser/gateway cases: anonymous read with
  anonymous write denied, authenticated non-merchant write denied, and merchant write allowed
  through `catalog:write` plus SpiceDB `store:main#manage`. The run also re-verified the real
  SpiceDB contract. The full-stack build drove free disk down to about `1.0 GiB`; immediately
  ran `sh scripts/down.sh`, `docker builder prune -af`, and `docker image prune -af`. Final
  disk was about `4.1 GiB` free and `docker system df` reported 0B Docker images, containers,
  volumes, and build cache. Updated handoff to order/payment acceptance mapping as the next
  slice and kept Postgres deferred.
