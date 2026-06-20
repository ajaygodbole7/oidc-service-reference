#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
cases_file="$script_dir/catalog-security-cases.tsv"

BASE_URL="${BASE_URL:-http://127.0.0.1:9080}"
CATALOG_LIST_ROUTE="${CATALOG_LIST_ROUTE:-/api/catalog/products}"
CATALOG_DETAIL_ROUTE="${CATALOG_DETAIL_ROUTE:-/api/catalog/products/sample-product}"
CATALOG_WRITE_ROUTE="${CATALOG_WRITE_ROUTE:-/api/catalog/products}"
HARNESS_PROFILE="${HARNESS_PROFILE:-read-only}"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
pending() { printf 'PENDING %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }
info() { printf '==> %s\n' "$*"; }

has_catalog_service() {
  if [ -f "$root/catalog-service/pom.xml" ] || [ -f "$root/services/catalog-service/pom.xml" ]; then
    return 0
  fi
  return 1
}

has_gateway_catalog_route() {
  if [ -f "$root/api-gateway/apisix.yaml.template" ] \
    && grep -E '/api/catalog/products|catalog-service' "$root/api-gateway/apisix.yaml.template" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

has_spicedb_store_manager_seed() {
  if [ -f "$root/authorization-service/seed.relationships" ] \
    && grep -q '^store:main#manager@user:merchant$' "$root/authorization-service/seed.relationships"; then
    return 0
  fi
  return 1
}

has_spicedb_store_manage_schema() {
  if [ -f "$root/authorization-service/schema.zed" ] \
    && grep -q 'relation manager: user' "$root/authorization-service/schema.zed" \
    && grep -q 'permission manage = manager' "$root/authorization-service/schema.zed"; then
    return 0
  fi
  return 1
}

unavailable_reason() {
  case "$1" in
    SEC-CATALOG-ANONYMOUS-READ-ONLY)
      if ! has_catalog_service; then
        printf 'catalog-service unavailable: create catalog-service before anonymous read/write gates can run'
      elif ! has_gateway_catalog_route; then
        printf 'APISIX /api/catalog/products route missing: route catalog APIs through the BFF gateway with GET auth-optional and writes protected'
      elif ! has_spicedb_store_manage_schema; then
        printf 'SpiceDB store manage schema unavailable: store manager relation and manage permission required'
      elif ! has_spicedb_store_manager_seed; then
        printf 'SpiceDB store manager seed unavailable: store:main#manager@user:merchant required for merchant write proof'
      else
        printf 'catalog live fixture unavailable: add anonymous GET list/detail, anonymous POST/PATCH denial, merchant write allow, and non-merchant write denial checks'
      fi
      ;;
    *)
      printf 'unknown check ID: add unavailable_reason mapping before wiring live verifier'
      ;;
  esac
}

info "catalog security harness draft"
info "base URL: $BASE_URL"
info "catalog list route: $CATALOG_LIST_ROUTE"
info "catalog detail route: $CATALOG_DETAIL_ROUTE"
info "catalog write route: $CATALOG_WRITE_ROUTE"
info "profile: $HARNESS_PROFILE"
info "evidence is bounded: stable IDs and availability only; no tokens, cookies, or secrets are printed"
info "draft mode: SEC checks below are catalog/availability probes only; live authority is verify-catalog-security-live.sh"

[ -f "$cases_file" ] || fail_check HARNESS-CATALOG-SECURITY-SPEC "missing $cases_file"
pass HARNESS-CATALOG-SECURITY-SPEC "stable catalog security case catalog exists"

while IFS='|' read -r id purpose future_command expected_result remediation; do
  case "$id" in
    ''|'#'*) continue ;;
  esac
  case "$id" in
    SEC-*) ;;
    *) fail_check HARNESS-CATALOG-SECURITY-SPEC "unstable check ID '$id': IDs must use the SEC-* catalog namespace" ;;
  esac
  [ -n "$purpose" ] || fail_check HARNESS-CATALOG-SECURITY-SPEC "$id missing purpose"
  [ -n "$future_command" ] || fail_check HARNESS-CATALOG-SECURITY-SPEC "$id missing future live command"
  [ -n "$expected_result" ] || fail_check HARNESS-CATALOG-SECURITY-SPEC "$id missing expected result"
  [ -n "$remediation" ] || fail_check HARNESS-CATALOG-SECURITY-SPEC "$id missing remediation"
  reason="$(unavailable_reason "$id")"
  pending "$id" "$reason; purpose: $purpose; expected later: $expected_result; remediation: $remediation; future live command, not executed by draft: $future_command"
done < "$cases_file"

pass HARNESS-CATALOG-SECURITY-DRAFT "draft completed read-only; all catalog SEC checks remain explicit PENDING/non-live"
