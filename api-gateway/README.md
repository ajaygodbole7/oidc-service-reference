# API Gateway (APISIX)

APISIX in standalone mode plus the custom `bff-session` Lua plugin.
The gateway is the single browser-facing ingress in the full Compose
stack. The initial scaffold forwards `/auth/*` to the Auth Service unchanged.
Domain `/api/**` routes are added later, one allowlisted business route at a
time, after the cart four-gate ladder is implemented.

## Files

- `config.yaml` — APISIX node config (standalone data-plane, port 9080,
  admin API disabled, `extra_lua_path` for the custom plugin, shared
  dicts for the Client-Credentials token cache and worker-local lock).
- `apisix.yaml.template` — declarative routes + upstreams, rendered to the
  gitignored `apisix.yaml.local` by `scripts/render-apisix-config.sh`. The
  scaffold starts with `/auth/*`; later `/api/**` routes attach `bff-session`.
- `plugins/bff-session.lua` — the plugin. Implements steps 1–7 of the
  pipeline in the `access` phase; `Cache-Control: no-store` is enforced
  in `header_filter`.
- `tests/` — black-box integration tests.
- `*.example` files — frozen templates kept for new contributors.

## Run locally

Compose mounts `config.yaml` and the rendered `apisix.yaml.local` into
`/usr/local/apisix/conf/`, and `plugins/bff-session.lua` into the APISIX
plugin directory `/usr/local/apisix/apisix/plugins/` (see `compose.yaml`
for the exact mount targets):

```
docker compose up apisix
```

## Secrets you must populate

`apisix.yaml.template` uses `${VAR}` env interpolation, filled by
`scripts/render-apisix-config.sh` at render time, for:

- `gateway_client_secret` — Keycloak client secret for
  `commerce-api-gateway`. Realm-import procedure regenerates this.
- `cookie_signing_key` — base64-encoded 256-bit HMAC key shared with the
  Auth Service.

Both are env-supplied in production and gitignored locally.

## Extending the allowlist

To add a route, copy one of the `/api/*` blocks in `apisix.yaml.template`
and adjust `uri` + `methods`. Off-allowlist `/api/*` paths return `404`
before the plugin runs. The set of declared routes is the allowlist.
