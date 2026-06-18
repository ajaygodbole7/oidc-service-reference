# frontend agent notes

Read the root `../AGENTS.md`, `../PLAN.md`, and `../PROGRESS.md` first. This file only
narrows the local boundary for frontend work.

## Ownership

`frontend` owns the React shell and business workflow UI. It calls same-origin `/auth/*`
and `/api/*` routes through the BFF/gateway front door. It does not hold tokens.

## Hard rules

- No browser tokens. Do not read, store, parse, log, or display access tokens, refresh
  tokens, or `id_token` values.
- `/auth/me` must remain a minimal browser-safe display projection.
- Use TanStack Router and TanStack Query for SPA routing/data fetching as the app grows;
  do not introduce Next.js.
- UI work should lead with the business app experience. Security evidence belongs in
  harness output and tests.

## Local gates

From `frontend/`:

```sh
corepack pnpm run typecheck
corepack pnpm run test
```

From the repo root for the live token-boundary proof:

```sh
sh scripts/e2e-auth.sh
```
