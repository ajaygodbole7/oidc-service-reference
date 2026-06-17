# AGENTS.md — oidc-service-reference

## What this is
- **teaching reference**: `oidc-service-reference` takes the BFF/OIDC foundation proven
in `oidc-reference` and hardens it inside a real-world business app — Postgres
persistence, a real frontend stack, real domain services, and SpiceDB resource
authorization. 
- A developer building catalog/cart/checkout features never handles tokens or
re-solves security; the copied BFF front-door and four-gate ladder carry it invisibly.
- The build spec is **`PLAN.md` — read it first.** Nothing is built yet; `PLAN.md` is the
contract, this file is how to work.
- Sibling repo to **`../oidc-reference`**, which proves the browser-token boundary through
login. This repo builds on that: **copy/adapt its BFF front-door** (auth-service, gateway,
frontend shell, test harness), then add domain services + resource authorization.

## Framing — do not drift (these were deliberate, hard-won calls)
- **The repo is the teaching.** It is NOT a "developer-experience" product, and NOT an
  "attack lab." Adversarial tests are **security verification** — proof the platform holds,
  not the headline.
- **The thesis:** secure identity and authorization are platform behavior that survives
  contact with real frontend, real persistence, and real domain workflows.
- **Lead with the business app** (catalog / cart / checkout / order history / merchant
  catalog management). Security is the *invisible platform* underneath, not the UI.
- **Security as platform = the four-gate ladder:** gateway session → service JWT
  (`aud=commerce-api`) → coarse scope/role → fine **SpiceDB** resource check. The fine
  resource gate (Zanzibar) is the centerpiece.

## Locked decisions — don't re-litigate (detail in `PLAN.md`)
- **Token:** minted once at login (Keycloak), single `commerce-api` audience, BFF holds it
  server-side, gateway injects it (phantom token). **No per-hop token exchange, no
  resolve-time `resource` param** — reuse `oidc-reference`'s token model.
- **Least privilege = scopes/roles in the JWT (coarse) + SpiceDB ReBAC (fine).**
- **ReBAC = SpiceDB** behind an `AuthorizationClient` adapter; **per-request `Check`,
  fail-closed**, `fully_consistent` (so relationship revocation is immediate).
- **S2S = client-credentials + structured command** (order→payment), separate
  `payment-service` audience. Payment authorizes the *caller* and trusts `userSub` as data;
  RFC 8693 token exchange is the documented future upgrade for cryptographic user provenance.
- **Stack = Java 26 / Spring Boot 4.1.0**, all services. Small shared `commerce-security-common`
  (five primitives: `CommercePrincipal`, JWT validator, `ScopeAuthorizer`,
  `ResourceAuthorizer`, `DecisionTrace`) — **just enough, NOT a framework.**
- **Versions = latest stable, then exact pins.** Use `PLAN.md`'s version table as the
  starting contract. No floating Docker `latest` tags, npm ranges, or unrecorded tool
  bumps. If upstream has moved before scaffold starts, refresh the table first; after
  scaffold exists, upgrades are their own verified slice.
- **Persistence = phased.** Prove the first cart ladder with in-memory repos, then add
  Postgres for domain persistence after the service flows are shaped. Valkey stays for BFF
  sessions + the Security Trace; order/payment idempotency moves to Postgres when those
  services become persistent.
- **Substrate = Docker Compose.** Prod-only infra (mTLS/SPIFFE, mesh, event bus, k8s,
  multi-region) is **documented hardening, not built.**
- Keycloak local = PingFederate stand-in; Cognito = documented scope-profile adapter.

## How to build (`PLAN.md` → "Build sequence")
Vertical slice first: scaffold the BFF front-door + SpiceDB + `commerce-security-common`,
then the **cart business flow end-to-end through all four gates** + the Security Trace +
its verification cases, then catalog, then checkout/order/payment, then the full
security-verification suite. Prove the ladder once on one feature before replicating.

## Build harness — the agentic loop
Use an agent loop as the build method: **gather context → take action → verify work →
repeat**. The loop is tool-neutral: Codex, Claude, another agent, or a human should be
able to follow the same slice contract.

### Bootstrap/read order
Every session starts by reading, in order:
1. `AGENTS.md` for operating rules and non-negotiable invariants.
2. `PLAN.md` for the product/build contract and per-slice acceptance matrix.
3. `PROGRESS.md` for current slice, exact next action, verifier status, and blockers.
4. Recent git history when available.

If any of those disagree, stop and reconcile the conflict before changing code. Prefer the
newest explicit human instruction, but do not silently override `PLAN.md` locked decisions
or the hard rules below.

### Roles
- **Human:** owns scope changes, approvals for commits/pushes/destructive actions, and
  any decision that changes the teaching goal.
- **Orchestrator agent:** owns the loop, chooses the next slice from `PROGRESS.md` and
  `PLAN.md`, delegates only when useful, and runs the final consolidated verifier.
- **Subagents:** work on isolated, slice-scoped tasks and return summaries, changed files,
  verifier output, risks, and blockers. They do not mark slices done.
- **Verifier:** the live gate is the authority. Agent claims are not authority.

Every session ends by updating `PROGRESS.md` with:
- current slice
- exact next action
- done work
- verifier status
- blockers
- changed files
- handoff notes for the next agent or human

Per-slice loop:
1. Pick the next slice from `PLAN.md` and `PROGRESS.md`.
2. Gather local context before changing files.
3. Write the failing test, contract check, or security-verification case first.
4. Run it red and confirm it fails for the expected reason.
5. Implement the smallest change that should make it pass.
6. Run the live gate for the slice.
7. Mark the slice done in `PROGRESS.md` only when the live gate is green.

False-green guard: a slice is not done until its relevant live verifier passes in a run
you watched. No marking-by-inspection, no "unit tests passed so it works", and no
skipping the cross-component proof when the slice touches the platform boundary.

### Parallel service fan-out
Avoid sequential token-burning once the shared platform contract exists. The orchestrator
must prove the reusable pattern once, then fan out service work by default.

Sequential foundation:
1. Scaffold the BFF front-door and baseline harness.
2. Add the authorization substrate and `commerce-security-common`.
3. Build the cart vertical slice as the template for JWT validation, scope checks,
   `ResourceAuthorizer`, trace evidence, architecture checks, and no-browser-token proof.

After the cart slice is green, parallel fan-out is the default:
- **Catalog agent:** owns `services/catalog-service` and catalog UI/API integration.
- **Order agent:** owns `services/order-service` and order/checkout orchestration.
- **Payment agent:** owns `services/payment-service` and S2S payment authorization.
- **Frontend workflow agent:** owns ecommerce screens and TanStack Query/Router wiring that
  is not service-internal.
- **Harness/security agent:** owns `tests/security`, `verify-all.sh`, stable check catalog,
  service-health summaries, and architecture gates.
- **Persistence agent:** owns the later Postgres/Flyway/Spring Data JDBC slice after service
  APIs and domain boundaries are stable.

Parallel-agent rules:
- Each subagent gets an explicit write scope and must not edit shared contracts unless the
  orchestrator approves the change.
- Shared contracts include `commerce-security-common`, SpiceDB schema, Keycloak realm,
  gateway routes, API endpoint shapes, `SEC-*` IDs, and harness output format.
- Subagents return summaries with changed files, verifier output, risks, and blockers.
- Subagents do not mark slices done and do not update `PROGRESS.md` independently.
- The orchestrator owns integration, conflict resolution, final `verify-all.sh`, and the
  single authoritative `PROGRESS.md` update.

### Non-negotiable invariants
Every slice must preserve these:
- **No browser tokens:** access and refresh tokens never reach the browser; the `id_token`
  never reaches browser JS, storage, SPA-readable JSON, SPA-visible cookies, or app logs.
  Only the server's logout→IdP redirect may carry `id_token_hint`.
- **Gateway is not business authz:** APISIX resolves sessions, strips unsafe headers,
  injects bearer, validates CSRF, and routes. It does not decide cart/order/catalog
  ownership.
- **Service gates stay explicit:** service JWT validation, scope checks, and SpiceDB
  resource checks remain separate gates.
- **Resource authorization stays near the domain:** application services orchestrate
  scope/resource checks; domain objects own business invariants; controllers stay thin.
- **Fail closed:** missing/invalid tokens, unknown routes, SpiceDB outages, bad
  relationships, and validation failures deny by default.
- **Provider agnostic:** branch on standard OIDC concepts (`iss`, `aud`, scopes, claims,
  discovery), never provider brand.
- **SpiceDB remains the fine authz authority:** Postgres ownership columns are business
  data only; no `WHERE owner_id = ?` shortcut replaces `ResourceAuthorizer`.
- **Harness evidence stays in the harness:** use stable check IDs with purpose/command/
  expected-result/remediation, bounded/masked evidence, local/test-only fixtures,
  read-only defaults for mutating harness actions, actionable failure output, architecture
  gates, and service-health summaries in scripts/tests/`PROGRESS.md`.
- **No speculative infra:** production-only concerns stay documented hardening unless
  `PLAN.md` explicitly moves them into the local reference.

Future harness scripts are deferred until scaffold, compose, and `verify-all.sh` exist:
- `scripts/agent-init.sh`: idempotent stack startup, service-health summaries for
  Keycloak, APISIX, Valkey, SpiceDB, and Postgres, plus SpiceDB schema/seed.
- `scripts/agent-loop.sh`: bounded loop around `${AGENT_CMD}`, verifier execution,
  red/green progress updates, and iteration cap.

The future driver defaults to a tool-neutral `AGENT_CMD`. It must not auto-commit or
auto-push. Passing work is recorded in `PROGRESS.md`; commits remain human-controlled.

## Per-module agent config
Each module ships its own `AGENTS.md` plus a sibling `CLAUDE.md` containing only `@AGENTS.md`
(the same dual pattern as root), so Codex and Claude both read it. Stack knowledge lives here,
not in Claude-only `.claude/skills`, because Codex must see it.

The split is additive, not duplicative:
- **Root `AGENTS.md`** owns everything shared: the invariants, the build method, the hard rules,
  commit convention, version pins, host-wide gotchas, and the Spring/Java conventions common to all
  backend services. A per-module file never restates these.
- **A module's `AGENTS.md`** adds only what is specific to that module: what it is and the gate(s)
  it owns, how to build/test it, its boundary rules, the `SEC-*` cases it proves, and its own gotchas.

Creating a module's `AGENTS.md` + `CLAUDE.md` stub is part of that module's scaffold
definition-of-done. Do not create them ahead of the module's directory. Modules that will get one:
`commerce-security-common`, `services/catalog-service`, `services/cart-service`,
`services/order-service`, `services/payment-service`, `auth-service`, `frontend`. (`api-gateway` is
APISIX/Lua config — add one only if it grows non-trivial; otherwise root covers it.)

Template:
```text
# AGENTS.md — <module> (<stack>)

Root AGENTS.md owns the shared invariants, build method, and hard rules. This file adds
only what is specific to <module>; it does not restate them.

## What this is
<one line> — gate(s) in the four-gate ladder it owns or exercises: <...>.

## Build & test (this module)
<commands — e.g. `cd <module> && ./mvnw clean test` + ArchUnit gate; or vitest + eslint
+ type-check for the frontend>

## Boundary rules
<module-specific only — e.g. validators live here not in services; controllers thin,
application services orchestrate the ladder; ResourceAuthorizer called at the boundary>

## Security contract / SEC-* cases proven here
<the SEC-* IDs this module proves live>

## Gotchas
<module-unique only>
```
Plus a sibling `CLAUDE.md` containing only `@AGENTS.md`.

## Hard rules (carried from `oidc-reference`)
- **TDD, red→green, proven live.** Write the failing test, watch it fail for the *right
  reason*, implement, watch it pass. A live cross-component run is the proof; a unit pass
  in isolation is not. Never claim it works without a green run you watched.
- **Token boundary, precise wording:** access and refresh tokens never reach the browser;
  the `id_token` never reaches browser JS, storage, SPA-readable JSON, SPA-visible cookies,
  or app logs — only the server's logout→IdP redirect may carry `id_token_hint`. The live
  e2e (`assertNoBrowserTokens`) asserts it; preserve it.
- **Fail closed.** New error/validation paths deny by default (SpiceDB unavailable → deny;
  unknown route → deny; missing/invalid token → deny). Mirror `SecurityAudit` for new outcomes.
- **Resource authz lives near the domain.** Services are not anemic controllers — the
  domain owns invariants. Controllers are adapters; application services orchestrate the
  ladder; infrastructure hides persistence/authz clients. Keep it proportionate, not ceremony.
- **Provider-agnostic code; provider specifics in config.** Branch on standard OIDC
  (`iss`/`aud`/scopes/claim paths/discovery), never on the provider brand.
- **Don't fight the infra.** APISIX, Compose, Keycloak, Valkey, Postgres, SpiceDB are swappable
  deployment details. Connection pooling, HA, mesh, observability, supply-chain pinning are
  **production-hardening** — disclose them in `docs/production-hardening.md`, don't bake
  them into the reference default.

## Commit convention
Plain, descriptive commit messages. **Do NOT add a `Co-Authored-By: Claude` trailer** (or
any AI attribution). Commit/push only when the human asks.

## Local-environment gotchas (same stack as `oidc-reference`; SpiceDB is the new piece)
Entries tagged `[→ <module>]` relocate to that module's `AGENTS.md` when the module is scaffolded;
untagged entries are host-wide and stay here.

- This Mac runs near-full disk; a full disk bricks Docker AND the Bash tool (ENOSPC).
  Check `df -h /System/Volumes/Data` before any stack build.
- APISIX cold-start is flaky on a loaded/old Docker (sometimes >240s to bind, ~10s warm).
  Prefer Compose health-gating (`--wait`/healthchecks); bring the stack up warm, then run
  against it; avoid rapid `down→up` churn.
- `[→ Java modules]` Run `mvnw clean test` before trusting a tally — surefire reports are NOT cleaned between
  runs, so a glob tally sweeps stale reports and invents phantom results (this bit
  `oidc-reference`). Cross-check counts against the surefire XML if a number looks off.
- `rg` (ripgrep) is a Claude shell shim invisible to `sh`. Don't `require_cmd rg` in POSIX
  verify scripts; expose a real `rg` via a `/tmp/rgbin` symlink for sh-based gates.
- Keycloak emits `aud` as a JSON **string** for one audience, an **array** for several —
  any audience validator must accept both shapes.
- `[→ commerce-security-common]` On any Spring Boot / Nimbus / spring-security bump: re-run the suites and specifically
  re-check multi-aud `azp` and JWS `typ` acceptance (`JWT` + `at+JWT`) — those live in
  library defaults that moved silently on the 4.1.0 GA bump in `oidc-reference`. Keep them
  enforced explicitly in code, not delegated to a default chain.
- `[→ commerce-security-common + backend services]` SpiceDB is new here: application services use `ResourceAuthorizer`; the
  `AuthorizationClient` adapter hides the SpiceDB SDK (don't scatter SDK calls through
  controllers), uses `fully_consistent` checks, and proves fail-closed when unavailable.

## Pointers
- `PLAN.md` — the build spec (architecture, four-gate ladder, SpiceDB schema, services,
  security-verification, build sequence).
- `../oidc-reference` — copy/adapt the BFF front-door + test harness from here; it's the
  proven base this repo extends.
