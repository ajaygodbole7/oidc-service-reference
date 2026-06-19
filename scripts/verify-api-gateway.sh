#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

fail() {
  echo "api-gateway verification failed: $1" >&2
  exit 1
}

apisix_yaml="api-gateway/apisix.yaml.template"
plugin_lua="api-gateway/plugins/bff-session.lua"

[ -f "$apisix_yaml" ] || fail "$apisix_yaml not present"
[ -s "$plugin_lua" ] || fail "$plugin_lua missing or empty"

grep -F -q "/auth" "$apisix_yaml" \
  || fail "$apisix_yaml missing /auth passthrough route"
if grep -E -q 'resource-server|/api/me|/api/user-data|/api/admin' "$apisix_yaml"; then
  fail "$apisix_yaml still contains copied demo resource-server routes"
fi

if grep -E -q 'require.*resty\.redis|valkey_host|valkey_port|valkey_password|idle_ttl_seconds' "$plugin_lua"; then
  fail "$plugin_lua speaks to a session store; gateway must resolve via /internal/resolve"
fi
grep -F -q "/internal/resolve" "$plugin_lua" \
  || fail "$plugin_lua missing the /internal/resolve back-channel call"

grep -F -q "# >>> test-only routes" "$apisix_yaml" \
  || fail "$apisix_yaml missing test-only route marker"
grep -F -q "/api/_test/cart/*" "$apisix_yaml" \
  || fail "$apisix_yaml missing local/test-only protected cart harness route"
grep -A8 -F "uri: /api/_test/cart/*" "$apisix_yaml" | grep -F -q "bff-session:" \
  || fail "$apisix_yaml cart harness route must use bff-session"

sh api-gateway/tests/test-lua-unit.sh || fail "Lua unit/parity tests failed"

DEV_GW='LOCAL_DEV_GATEWAY_CLIENT_SECRET__CHANGE_BEFORE_DEPLOY'
DEV_CSRF='AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='
REAL_GW='render-test-nondev-gateway-placeholder'
REAL_CSRF='BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB='

assert_render_rc() {
  label="$1"; cmp="$2"; expected="$3"; shift 3
  got=0
  env "$@" sh scripts/render-apisix-config.sh >/dev/null 2>&1 || got=$?
  case "$cmp" in
    eq) [ "$got" -eq "$expected" ] || fail "render guard $label: want rc=$expected got $got" ;;
    ne) [ "$got" -ne "$expected" ] || fail "render guard $label: want rc!=$expected got $got" ;;
    *) fail "bad assert_render_rc comparator $cmp" ;;
  esac
}

render_local="api-gateway/apisix.yaml.local"
backup=
[ -f "$render_local" ] && { backup="$(mktemp)"; cp "$render_local" "$backup"; }
cleanup() {
  if [ -n "$backup" ] && [ -f "$backup" ]; then
    mv "$backup" "$render_local"
  else
    rm -f "$render_local"
  fi
}
trap cleanup EXIT INT TERM

assert_render_rc "refuses dev gateway secret" eq 3 \
  REQUIRE_NONDEV_SECRETS=1 GATEWAY_CLIENT_SECRET="$DEV_GW" CSRF_SIGNING_KEY="$REAL_CSRF"
assert_render_rc "refuses dev csrf key" eq 3 \
  REQUIRE_NONDEV_SECRETS=1 GATEWAY_CLIENT_SECRET="$REAL_GW" CSRF_SIGNING_KEY="$DEV_CSRF"
assert_render_rc "allows real secrets under prod flag" ne 3 \
  REQUIRE_NONDEV_SECRETS=1 GATEWAY_CLIENT_SECRET="$REAL_GW" CSRF_SIGNING_KEY="$REAL_CSRF"
assert_render_rc "dev path unaffected" ne 3 \
  GATEWAY_CLIENT_SECRET="$DEV_GW" CSRF_SIGNING_KEY="$DEV_CSRF"

env GATEWAY_CLIENT_SECRET="$DEV_GW" CSRF_SIGNING_KEY="$DEV_CSRF" \
  sh scripts/render-apisix-config.sh >/dev/null 2>&1 \
  || fail "local render failed"
grep -F -q "/api/_test/cart/*" "$render_local" \
  || fail "local render stripped cart harness route"

env REQUIRE_NONDEV_SECRETS=1 GATEWAY_CLIENT_SECRET="$REAL_GW" CSRF_SIGNING_KEY="$REAL_CSRF" \
  sh scripts/render-apisix-config.sh >/dev/null 2>&1 \
  || fail "production-intent render failed"
if grep -F -q "/api/_test/cart/*" "$render_local"; then
  fail "production-intent render kept cart harness route"
fi

echo "api-gateway verification passed"
