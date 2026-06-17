#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
pending() { printf 'PENDING %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }

check_file() {
  id="$1"; path="$2"; purpose="$3"
  [ -e "$path" ] || fail_check "$id" "missing $path; $purpose"
  pass "$id" "$purpose"
}

check_absent_in() {
  id="$1"; pattern="$2"; purpose="$3"; shift 3
  if grep -Hn "$pattern" "$@" >/tmp/oidc-service-reference-verify.out 2>/dev/null; then
    cat /tmp/oidc-service-reference-verify.out >&2
    fail_check "$id" "$purpose"
  fi
  pass "$id" "$purpose"
}

info "oidc-service-reference scaffold verifier"
info "evidence is bounded: file names, service health, stable check IDs; no tokens or secrets are printed"

check_file HARNESS-SCAFFOLD-AUTH auth-service/pom.xml "Auth Service scaffold exists"
check_file HARNESS-SCAFFOLD-GATEWAY api-gateway/apisix.yaml.template "APISIX gateway scaffold exists"
check_file HARNESS-SCAFFOLD-IDP authorization-server/realm/oidc-service-reference-realm.json "Keycloak realm scaffold exists"
check_file HARNESS-SCAFFOLD-FRONTEND frontend/package.json "frontend shell scaffold exists"

grep -q 'quay.io/keycloak/keycloak:26.6.3' compose.yaml \
  || fail_check HARNESS-PIN-KEYCLOAK "Keycloak image must be pinned to 26.6.3"
grep -q 'apache/apisix:3.17.0' compose.yaml \
  || fail_check HARNESS-PIN-APISIX "APISIX image must be pinned to 3.17.0"
grep -q 'valkey/valkey:9.1.0' compose.yaml \
  || fail_check HARNESS-PIN-VALKEY "Valkey image must be pinned to 9.1.0"
grep -q '"packageManager": "pnpm@11.7.0"' frontend/package.json \
  || fail_check HARNESS-PIN-PNPM "frontend package manager must be pinned to pnpm 11.7.0"
pass HARNESS-PINNED-VERSIONS "initial manifests use exact scaffold pins"

check_absent_in HARNESS-NO-RESOURCE-SERVER 'backend-resource-server\|resource-server:8082' \
  "copied backend-resource-server demo routes must not be active in this repo" \
  compose.yaml api-gateway/apisix.yaml.template scripts/up.sh
check_absent_in HARNESS-NO-FLOATING-DOCKER ':latest' \
  "Docker manifests must not use floating latest tags" \
  compose.yaml auth-service/Dockerfile
check_absent_in HARNESS-NO-NPM-RANGES '"[~^*]' \
  "frontend package versions must be exact, not ranges" \
  frontend/package.json

if command -v docker >/dev/null 2>&1; then
  if docker compose ps >/tmp/oidc-service-reference-compose-ps.out 2>/dev/null; then
    info "service health summary from docker compose ps"
    sed -n '1,12p' /tmp/oidc-service-reference-compose-ps.out
  else
    pending HARNESS-SERVICE-HEALTH "compose stack is not running; run scripts/up.sh when ready"
  fi
else
  pending HARNESS-SERVICE-HEALTH "docker not available on PATH"
fi

pending SEC-NO-BROWSER-TOKENS "requires live auth flow through APISIX and frontend"
pending SEC-NON-COMMERCE-AUD "requires commerce service JWT gate"
pending SEC-CATALOG-ANONYMOUS-READ-ONLY "requires catalog service"
pending SEC-SCOPE-WITHOUT-RELATIONSHIP "requires cart service and SpiceDB"
pending SEC-RELATIONSHIP-WITHOUT-SCOPE "requires cart service and SpiceDB"
pending SEC-SPOOFED-IDENTITY-HEADERS "requires protected /api route"
pending SEC-BROWSER-AUTHORIZATION-OVERWRITTEN "requires protected /api route"
pending SEC-PAYMENT-NO-BROWSER-ROUTE "requires payment service"
pending SEC-PAYMENT-WRONG-CLIENT "requires payment service"
pending SEC-PAYMENT-REJECTS-USER-TOKEN "requires payment service"
pending SEC-CHECKOUT-IDEMPOTENT-REPLAY "requires order/payment services"
pending SEC-CHECKOUT-IDEMPOTENCY-COLLISION "requires order/payment services"
pending SEC-SPICEDB-UNAVAILABLE "requires SpiceDB"
pending SEC-RELATIONSHIP-REMOVAL-IMMEDIATE "requires SpiceDB and cart service"

success "scaffold verifier completed with explicit pending checks"
