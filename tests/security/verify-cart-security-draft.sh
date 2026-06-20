#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
cases_file="$script_dir/cart-security-cases.tsv"

BASE_URL="${BASE_URL:-http://127.0.0.1:9080}"
CART_ROUTE="${CART_ROUTE:-/api/carts/alice-cart}"
HARNESS_PROFILE="${HARNESS_PROFILE:-read-only}"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
pending() { printf 'PENDING %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }
info() { printf '==> %s\n' "$*"; }

has_cart_service() {
  if [ -f "$root/cart-service/pom.xml" ] || [ -f "$root/services/cart-service/pom.xml" ]; then
    return 0
  fi
  return 1
}

has_gateway_cart_route() {
  if [ -f "$root/api-gateway/apisix.yaml.template" ] \
    && grep -E '/api/cart|cart-service' "$root/api-gateway/apisix.yaml.template" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

has_spicedb_seed() {
  if [ -f "$root/authorization-service/seed.relationships" ] \
    && grep -q '^cart:alice-cart#owner@user:alice$' "$root/authorization-service/seed.relationships" \
    && grep -q '^cart:bob-cart#owner@user:bob$' "$root/authorization-service/seed.relationships"; then
    return 0
  fi
  return 1
}

is_local_mutation_profile() {
  [ "$HARNESS_PROFILE" = "local" ] || [ "$HARNESS_PROFILE" = "test" ]
}

unavailable_reason() {
  case "$1" in
    SEC-NON-COMMERCE-AUD)
      if ! has_cart_service; then
        printf 'cart-service unavailable: create cart-service before live audience rejection can run'
      elif ! has_gateway_cart_route; then
        printf 'APISIX /api/cart route missing: route cart APIs through the BFF gateway'
      else
        printf 'non-commerce token fixture unavailable: add local/test-only fixture that fails closed without printing token material'
      fi
      ;;
    SEC-SCOPE-WITHOUT-RELATIONSHIP)
      if ! has_cart_service; then
        printf 'cart-service unavailable: ResourceAuthorizer has no cart endpoint to protect'
      elif ! has_spicedb_seed; then
        printf 'SpiceDB cart seed unavailable: alice-cart and bob-cart owner relationships required'
      else
        printf 'cross-user cart fixture unavailable: add local/test-only Alice-with-cart:read request against Bob cart'
      fi
      ;;
    SEC-RELATIONSHIP-WITHOUT-SCOPE)
      if ! has_cart_service; then
        printf 'cart-service unavailable: ScopeAuthorizer has no cart endpoint to protect'
      elif ! has_spicedb_seed; then
        printf 'SpiceDB cart seed unavailable: alice-cart owner relationship required'
      else
        printf 'missing-scope fixture unavailable: add local/test-only owner token without required cart scope'
      fi
      ;;
    SEC-OWNERSHIP-PROVISIONED-FOR-CALLER)
      if ! has_cart_service; then
        printf 'cart-service unavailable: create-on-first-add cannot provision cart ownership'
      elif ! has_gateway_cart_route; then
        printf 'APISIX /api/cart route missing: cannot prove first add through the BFF gateway'
      else
        printf 'dynamic ownership fixture unavailable: use a scoped existing IdP user with no seeded cart'
      fi
      ;;
    SEC-NO-RESOURCE-HIJACK)
      if ! has_cart_service; then
        printf 'cart-service unavailable: no create path exists to test server-generated cart ids'
      elif ! has_gateway_cart_route; then
        printf 'APISIX /api/cart route missing: cannot prove attacker-chosen ids are ignored through the gateway'
      else
        printf 'resource hijack fixture unavailable: post first add with attacker-looking body data and assert generated cart id'
      fi
      ;;
    SEC-PROVISIONING-FAILS-CLOSED)
      if ! has_cart_service; then
        printf 'cart-service unavailable: no provisioning failure path exists to test'
      else
        printf 'provisioning failure contract unavailable: add service test where AuthorizationClient.writeRelationship fails before save'
      fi
      ;;
    SEC-SPOOFED-IDENTITY-HEADERS)
      if ! has_gateway_cart_route; then
        printf 'APISIX /api/cart route missing: cannot prove unsafe identity headers are stripped'
      elif ! has_cart_service; then
        printf 'cart-service unavailable: no service trace can show trusted principal source'
      else
        printf 'trace fixture unavailable: add bounded stripped-header evidence under local/test profile'
      fi
      ;;
    SEC-BROWSER-AUTHORIZATION-OVERWRITTEN)
      if ! has_gateway_cart_route; then
        printf 'APISIX /api/cart route missing: cannot prove browser Authorization overwrite'
      elif ! has_cart_service; then
        printf 'cart-service unavailable: no injected-token fingerprint can be observed'
      else
        printf 'gateway overwrite fixture unavailable: add fake-browser-bearer request with masked evidence'
      fi
      ;;
    SEC-RELATIONSHIP-REMOVAL-IMMEDIATE)
      if ! is_local_mutation_profile; then
        printf 'local/test mutation profile disabled: set HARNESS_PROFILE=local or test for revocation fixture'
      elif ! has_cart_service; then
        printf 'cart-service unavailable: no post-revocation read can be attempted'
      elif ! has_spicedb_seed; then
        printf 'SpiceDB cart seed unavailable: alice-cart owner relationship required before removal'
      else
        printf 'relationship mutation fixture unavailable: add local/test-only remove-and-restore helper'
      fi
      ;;
    *)
      printf 'unknown check ID: add unavailable_reason mapping before wiring live verifier'
      ;;
  esac
}

info "cart security harness draft"
info "base URL: $BASE_URL"
info "cart route: $CART_ROUTE"
info "profile: $HARNESS_PROFILE"
info "evidence is bounded: stable IDs and availability only; no tokens, cookies, or secrets are printed"
info "draft mode: SEC checks below are catalog/availability probes only; live authority is verify-cart-security-live.sh"

[ -f "$cases_file" ] || fail_check HARNESS-CART-SECURITY-SPEC "missing $cases_file"
pass HARNESS-CART-SECURITY-SPEC "stable cart security case catalog exists"

while IFS='|' read -r id purpose future_command expected_result remediation; do
  case "$id" in
    ''|'#'*) continue ;;
  esac
  case "$id" in
    SEC-*) ;;
    *) fail_check HARNESS-CART-SECURITY-SPEC "unstable check ID '$id': IDs must use the SEC-* catalog namespace" ;;
  esac
  [ -n "$purpose" ] || fail_check HARNESS-CART-SECURITY-SPEC "$id missing purpose"
  [ -n "$future_command" ] || fail_check HARNESS-CART-SECURITY-SPEC "$id missing future live command"
  [ -n "$expected_result" ] || fail_check HARNESS-CART-SECURITY-SPEC "$id missing expected result"
  [ -n "$remediation" ] || fail_check HARNESS-CART-SECURITY-SPEC "$id missing remediation"
  reason="$(unavailable_reason "$id")"
  pending "$id" "$reason; purpose: $purpose; expected later: $expected_result; remediation: $remediation; future live command, not executed by draft: $future_command"
done < "$cases_file"

pass HARNESS-CART-SECURITY-DRAFT "draft completed read-only; all cart SEC checks remain explicit PENDING/non-live"
