# auth-service agent notes

Read the root `../AGENTS.md`, `../PLAN.md`, and `../PROGRESS.md` first. This file only
narrows the local boundary for Auth Service work.

## Ownership

`auth-service` owns the copied BFF front-door session behavior from `oidc-reference`.
It talks to Keycloak and Valkey, holds tokens server-side, and exposes only
browser-safe identity/session endpoints through APISIX.

## Hard rules

- Never expose access tokens, refresh tokens, or `id_token` values to browser JavaScript,
  SPA-readable JSON, SPA-visible cookies, logs, or test output.
- Keep `/auth/me` as a minimal display projection. Do not echo raw claims or token payloads.
- Keep provider-specific details in config. Branch on standard OIDC behavior, not provider
  brand.
- Gateway/session behavior is platform plumbing only. Do not add business authorization
  decisions here.

## Local gates

From the repo root:

```sh
sh scripts/verify-all.sh
sh scripts/e2e-auth.sh
```

For Auth Service-only Java work:

```sh
cd auth-service && JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/26-amzn}" ./mvnw -B clean test
```
