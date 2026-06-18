# authorization-service agent notes

Read the root `../AGENTS.md`, `../PLAN.md`, and `../PROGRESS.md` first. This directory
owns the local SpiceDB authorization substrate, not an app service.

## Ownership

`authorization-service` contains the SpiceDB schema and seed relationships used by the
local harness. Java services reach this through `commerce-security-common` and a future
`SpiceDbAuthorizationClient` adapter; they do not read or mutate these files directly.

## Hard rules

- SpiceDB is the fine resource-authorization authority.
- Keep schema names aligned with `PLAN.md`: `user`, `store`, `cart`, and `order`.
- Keep `store#manager` as the seeded relationship and `store#manage` as the checked
  permission.
- Seed files are local/dev fixtures only. Do not add production tenancy, hierarchy, or
  migration machinery here until `PLAN.md` says so.
- Do not put tokens, cookies, secrets, or raw prompts in schema comments, seed files, or
  harness output.

## Local gates

From the repo root:

```sh
sh scripts/verify-spicedb-static.sh
docker compose config --quiet
```

The live schema-load and seed-apply gate is still part of the active authorization-substrate
slice and must be proven before that slice is accepted.
