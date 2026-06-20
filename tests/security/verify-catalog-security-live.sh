#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
. "$root/scripts/lib/common.sh"
cd "$root"

require_cmd docker "Start Docker Desktop or Colima."
require_cmd node "Install Node 26.3.0."

warn_low_disk 3

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }

wait_service_healthy() {
  service="$1"
  tries=0
  while :; do
    tries=$((tries + 1))
    cid="$(docker compose ps -q "$service" 2>/dev/null || true)"
    if [ -n "$cid" ]; then
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)"
      [ "$status" = "healthy" ] && return 0
    fi
    if [ "$tries" -ge 90 ]; then
      fail_check HARNESS-CATALOG-SECURITY-LIVE "$service unavailable or not healthy"
    fi
    sleep 2
  done
}

info "catalog live security harness"
info "evidence is bounded: stable IDs, service health, and pass/fail only; no tokens, cookies, or secrets are printed"

# Catalog needs no scope juggling (merchant gets catalog:write from the default scope
# set) and no test-fixture profile (anonymous reads need no session). When the realm or
# the catalog image changed, bring the stack up fresh so Keycloak re-imports the realm
# (the merchant user + catalog:write scope) and catalog-service is rebuilt.
if [ "${CATALOG_SECURITY_SKIP_UP:-0}" = "1" ]; then
  info "using already-running stack (CATALOG_SECURITY_SKIP_UP=1)"
else
  sh scripts/up.sh
fi

wait_service_healthy catalog-service
wait_service_healthy auth-service
# Seed the SpiceDB schema + relationships (incl store:main#manager@user:merchant) so the
# merchant write passes gate 4. SpiceDB uses the memory datastore, so a fresh stack starts
# empty; mirror the cart live harness, which seeds the same way.
sh scripts/verify-cart-spicedb-live.sh
# APISIX caches upstream IPs at start; restart so the catalog-service upstream resolves.
docker compose restart apisix >/dev/null
wait_responding "APISIX" "http://127.0.0.1:9080/auth/me" 120

(
  cd "$root/frontend"
  export E2E_FULL_STACK=1
  run_pnpm exec playwright test tests/e2e/catalog-live.spec.ts --grep "SEC-CATALOG-ANONYMOUS-READ-ONLY"
)

pass SEC-CATALOG-ANONYMOUS-READ-ONLY "live local verifier passed"
success "catalog live security harness completed"
