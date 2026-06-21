#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
. "$root/scripts/lib/common.sh"
cd "$root"

require_cmd docker "Start Docker Desktop or Colima."
require_cmd node "Install Node 26.3.0."

warn_low_disk 3

# Full default scope set, and the de-facto cross-harness baseline: verify-live-all runs cart
# first, and cart's restore_default_auth (EXIT trap) leaves auth-service configured with these
# scopes, which the catalog and order live gates then rely on. Keep catalog:write + orders:* here.
DEFAULT_SCOPES="openid,profile,email,roles,api.audience,api.read,cart:read,cart:write,catalog:write,orders:read,orders:write"
MISSING_CART_SCOPE_SCOPES="openid,profile,email,roles,api.audience,api.read"
NON_COMMERCE_AUD_SCOPES="openid,profile,email,roles,api.read,cart:read,cart:write"

requested="${*:-all}"
stack_ready=0
default_auth_ready=0
spicedb_stopped=0

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }

should_run() {
  id="$1"
  case " $requested " in
    *" all "*) return 0 ;;
    *" $id "*) return 0 ;;
    *) return 1 ;;
  esac
}

known_case() {
  case "$1" in
    SEC-SCOPE-WITHOUT-RELATIONSHIP|\
    SEC-SPOOFED-IDENTITY-HEADERS|\
    SEC-BROWSER-AUTHORIZATION-OVERWRITTEN|\
    SEC-RELATIONSHIP-REMOVAL-IMMEDIATE|\
    SEC-RELATIONSHIP-WITHOUT-SCOPE|\
    SEC-OWNERSHIP-PROVISIONED-FOR-CALLER|\
    SEC-NO-RESOURCE-HIJACK|\
    SEC-PROVISIONING-FAILS-CLOSED|\
    SEC-NON-COMMERCE-AUD|\
    SEC-SECURITY-TRACE-EVIDENCE|\
    SEC-SPICEDB-UNAVAILABLE)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

if [ "$requested" != "all" ]; then
  for id in "$@"; do
    known_case "$id" || fail_check HARNESS-CART-SECURITY-LIVE "unknown cart SEC case '$id'"
  done
fi

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
      fail_check HARNESS-CART-SECURITY-LIVE "$service unavailable or not healthy"
    fi
    sleep 2
  done
}

restart_spicedb() {
  if [ "$spicedb_stopped" = "1" ]; then
    info "restarting SpiceDB and restoring local schema/seed relationships"
    docker compose up -d --no-build spicedb >/dev/null
    wait_service_healthy spicedb
    sh scripts/verify-cart-spicedb-live.sh >/dev/null
    spicedb_stopped=0
  fi
}

restore_default_auth() {
  if [ "$stack_ready" = "1" ] && [ "$default_auth_ready" = "0" ]; then
    info "restoring default local auth-service cart scopes"
    OIDC_SCOPES="$DEFAULT_SCOPES" docker compose up -d --no-build --force-recreate auth-service apisix >/dev/null
    wait_service_healthy auth-service
    docker compose restart apisix >/dev/null
    wait_responding "APISIX" "http://127.0.0.1:9080/auth/me" 120
    default_auth_ready=1
  fi
}

enable_cart_fixtures() {
  info "ensuring cart-service local test-fixture profile is active"
  CART_SERVICE_SPRING_PROFILES_ACTIVE=test-fixture \
    docker compose up -d --no-build --force-recreate cart-service apisix >/dev/null
  wait_service_healthy cart-service
  docker compose restart apisix >/dev/null
  wait_responding "APISIX" "http://127.0.0.1:9080/auth/me" 120
}

reset_cart_service_for_dynamic_ownership() {
  info "clearing dynamically-provisioned carts so the dynamic-ownership case starts without an admin cart"
  # Postgres persists carts across runs (unlike the old in-memory repo), so recreating the
  # container is not enough — remove any cart that is not a seeded fixture (alice/bob).
  docker compose exec -T postgres psql -U "${POSTGRES_USER:-commerce}" -d cart_db \
    -c "DELETE FROM carts WHERE owner_sub NOT IN ('alice', 'bob')" >/dev/null 2>&1 || true
  CART_SERVICE_SPRING_PROFILES_ACTIVE=test-fixture \
    docker compose up -d --no-build --force-recreate cart-service apisix >/dev/null
  wait_service_healthy cart-service
  docker compose restart apisix >/dev/null
  wait_responding "APISIX" "http://127.0.0.1:9080/auth/me" 120
}

cleanup() {
  restart_spicedb
  restore_default_auth
}
trap cleanup EXIT INT TERM

prepare_stack() {
  if [ "$stack_ready" = "1" ]; then
    return 0
  fi

  info "cart live security harness"
  info "evidence is bounded: stable IDs, service health, and pass/fail only; no tokens, cookies, or secrets are printed"

  if [ "${CART_SECURITY_SKIP_UP:-0}" = "1" ]; then
    info "using already-running stack (CART_SECURITY_SKIP_UP=1)"
  else
    CART_SERVICE_SPRING_PROFILES_ACTIVE=test-fixture sh scripts/up.sh
  fi

  wait_service_healthy cart-service
  wait_service_healthy auth-service
  sh scripts/verify-cart-spicedb-live.sh
  stack_ready=1
  default_auth_ready=1
}

set_auth_scopes() {
  scopes="$1"
  info "recreating auth-service with local test scopes for requested SEC case"
  OIDC_SCOPES="$scopes" docker compose up -d --no-build --force-recreate auth-service apisix >/dev/null
  wait_service_healthy auth-service
  docker compose restart apisix >/dev/null
  wait_responding "APISIX" "http://127.0.0.1:9080/auth/me" 120
  if [ "$scopes" = "$DEFAULT_SCOPES" ]; then
    default_auth_ready=1
  else
    default_auth_ready=0
  fi
}

run_case() {
  id="$1"
  fixtures="${2:-0}"
  scenario="${3:-}"
  (
    cd "$root/frontend"
    export E2E_FULL_STACK=1
    export CART_SECURITY_FIXTURES="$fixtures"
    export CART_SECURITY_SCENARIO="$scenario"
    run_pnpm exec playwright test tests/e2e/cart-live.spec.ts --grep "$id"
  )
  pass "$id" "live local verifier passed"
}

run_service_contract_case() {
  id="$1"
  sh scripts/verify-cart-service.sh >/dev/null
  pass "$id" "service contract verifier passed"
}

prepare_stack

if should_run SEC-SCOPE-WITHOUT-RELATIONSHIP; then
  restore_default_auth
  run_case SEC-SCOPE-WITHOUT-RELATIONSHIP
fi

if should_run SEC-SPOOFED-IDENTITY-HEADERS; then
  restore_default_auth
  enable_cart_fixtures
  run_case SEC-SPOOFED-IDENTITY-HEADERS 1
fi

if should_run SEC-BROWSER-AUTHORIZATION-OVERWRITTEN; then
  restore_default_auth
  enable_cart_fixtures
  run_case SEC-BROWSER-AUTHORIZATION-OVERWRITTEN 1
fi

if should_run SEC-SECURITY-TRACE-EVIDENCE; then
  restore_default_auth
  enable_cart_fixtures
  run_case SEC-SECURITY-TRACE-EVIDENCE 1
fi

if should_run SEC-RELATIONSHIP-REMOVAL-IMMEDIATE; then
  restore_default_auth
  enable_cart_fixtures
  run_case SEC-RELATIONSHIP-REMOVAL-IMMEDIATE 1
fi

if should_run SEC-RELATIONSHIP-WITHOUT-SCOPE; then
  set_auth_scopes "$MISSING_CART_SCOPE_SCOPES"
  run_case SEC-RELATIONSHIP-WITHOUT-SCOPE 0 missing-scope
fi

if should_run SEC-OWNERSHIP-PROVISIONED-FOR-CALLER; then
  restore_default_auth
  reset_cart_service_for_dynamic_ownership
  run_case SEC-OWNERSHIP-PROVISIONED-FOR-CALLER 0 dynamic-ownership
fi

if should_run SEC-NO-RESOURCE-HIJACK; then
  restore_default_auth
  reset_cart_service_for_dynamic_ownership
  run_case SEC-NO-RESOURCE-HIJACK 0 dynamic-ownership
fi

if should_run SEC-PROVISIONING-FAILS-CLOSED; then
  run_service_contract_case SEC-PROVISIONING-FAILS-CLOSED
fi

if should_run SEC-NON-COMMERCE-AUD; then
  set_auth_scopes "$NON_COMMERCE_AUD_SCOPES"
  run_case SEC-NON-COMMERCE-AUD 0 non-commerce-aud
fi

if should_run SEC-SPICEDB-UNAVAILABLE; then
  restore_default_auth
  info "stopping SpiceDB to prove cart reads and writes fail closed"
  docker compose stop spicedb >/dev/null
  spicedb_stopped=1
  run_case SEC-SPICEDB-UNAVAILABLE 0 spicedb-unavailable
  restart_spicedb
fi

restore_default_auth
success "cart live security harness completed"
