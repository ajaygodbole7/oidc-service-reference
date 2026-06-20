#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
cases_file="$script_dir/order-payment-security-cases.tsv"

BASE_URL="${BASE_URL:-http://127.0.0.1:9080}"
CHECKOUT_ROUTE="${CHECKOUT_ROUTE:-/api/orders/checkout}"
ORDER_ROUTE="${ORDER_ROUTE:-/api/orders/alice-order}"
PAYMENT_INTERNAL_ROUTE="${PAYMENT_INTERNAL_ROUTE:-/internal/payments/authorize}"
HARNESS_PROFILE="${HARNESS_PROFILE:-read-only}"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
pending() { printf 'PENDING %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }
info() { printf '==> %s\n' "$*"; }

has_order_service() {
  if [ -f "$root/order-service/pom.xml" ] || [ -f "$root/services/order-service/pom.xml" ]; then
    return 0
  fi
  return 1
}

has_payment_service() {
  if [ -f "$root/payment-service/pom.xml" ] || [ -f "$root/services/payment-service/pom.xml" ]; then
    return 0
  fi
  return 1
}

has_gateway_order_route() {
  if [ -f "$root/api-gateway/apisix.yaml.template" ] \
    && grep -E '/api/orders|order-service' "$root/api-gateway/apisix.yaml.template" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

has_no_gateway_payment_browser_route() {
  if [ ! -f "$root/api-gateway/apisix.yaml.template" ]; then
    return 1
  fi
  if grep -E '/internal/payments|payment-service' "$root/api-gateway/apisix.yaml.template" >/dev/null 2>&1; then
    return 1
  fi
  return 0
}

has_payment_audience_config() {
  if [ -f "$root/authorization-server/realm/oidc-service-reference-realm.json" ] \
    && grep -q '"payment-service"' "$root/authorization-server/realm/oidc-service-reference-realm.json" \
    && grep -q '"order-service"' "$root/authorization-server/realm/oidc-service-reference-realm.json"; then
    return 0
  fi
  return 1
}

has_order_spicedb_schema() {
  if [ -f "$root/authorization-service/schema.zed" ] \
    && grep -q 'definition order' "$root/authorization-service/schema.zed" \
    && grep -q 'permission cancel' "$root/authorization-service/schema.zed"; then
    return 0
  fi
  return 1
}

unavailable_reason() {
  case "$1" in
    SEC-PAYMENT-NO-BROWSER-ROUTE)
      if ! has_payment_service; then
        printf 'payment-service unavailable: create internal payment-service before browser-route denial can run'
      elif ! has_no_gateway_payment_browser_route; then
        printf 'APISIX exposes payment internals: remove /internal/payments/** and payment-service from browser gateway routes'
      else
        printf 'payment live fixture unavailable: add browser/API gateway request proving /internal/payments/authorize is unreachable'
      fi
      ;;
    SEC-PAYMENT-WRONG-CLIENT)
      if ! has_payment_service; then
        printf 'payment-service unavailable: no S2S validator exists'
      elif ! has_payment_audience_config; then
        printf 'payment-service audience/client config unavailable: add payment-service audience and order-service confidential client'
      else
        printf 'wrong-client fixture unavailable: mint payment-service token for a non-order client and expect denial'
      fi
      ;;
    SEC-PAYMENT-REJECTS-USER-TOKEN)
      if ! has_payment_service; then
        printf 'payment-service unavailable: no internal endpoint exists'
      else
        printf 'user-token fixture unavailable: send commerce-api user token to payment-service internal endpoint and expect denial'
      fi
      ;;
    SEC-CHECKOUT-IDEMPOTENT-REPLAY)
      if ! has_order_service; then
        printf 'order-service unavailable: checkout/idempotency cannot run'
      elif ! has_payment_service; then
        printf 'payment-service unavailable: duplicate payment authorization cannot be observed'
      elif ! has_gateway_order_route; then
        printf 'APISIX /api/orders route missing: checkout must run through the BFF gateway'
      elif ! has_order_spicedb_schema; then
        printf 'SpiceDB order schema unavailable: order read/cancel relationships required for checkout result'
      else
        printf 'checkout replay fixture unavailable: add same-key/same-body checkout proof with payment-call count evidence'
      fi
      ;;
    SEC-CHECKOUT-IDEMPOTENCY-COLLISION)
      if ! has_order_service; then
        printf 'order-service unavailable: checkout/idempotency cannot reject collisions'
      elif ! has_payment_service; then
        printf 'payment-service unavailable: need evidence collision rejects before payment call'
      elif ! has_gateway_order_route; then
        printf 'APISIX /api/orders route missing: checkout must run through the BFF gateway'
      else
        printf 'checkout collision fixture unavailable: add same-key/different-body proof with no payment call'
      fi
      ;;
    *)
      printf 'unknown check ID: add unavailable_reason mapping before wiring live verifier'
      ;;
  esac
}

info "order/payment security harness draft"
info "base URL: $BASE_URL"
info "checkout route: $CHECKOUT_ROUTE"
info "order route: $ORDER_ROUTE"
info "payment internal route: $PAYMENT_INTERNAL_ROUTE"
info "profile: $HARNESS_PROFILE"
info "evidence is bounded: stable IDs and availability only; no tokens, cookies, client secrets, or raw prompts are printed"
info "draft mode: SEC checks below are order/payment availability probes only; live authority will be verify-order-payment-security-live.sh"

[ -f "$cases_file" ] || fail_check HARNESS-ORDER-PAYMENT-SECURITY-SPEC "missing $cases_file"
pass HARNESS-ORDER-PAYMENT-SECURITY-SPEC "stable order/payment security case catalog exists"

while IFS='|' read -r id purpose future_command expected_result remediation; do
  case "$id" in
    ''|'#'*) continue ;;
  esac
  case "$id" in
    SEC-*) ;;
    *) fail_check HARNESS-ORDER-PAYMENT-SECURITY-SPEC "unstable check ID '$id': IDs must use the SEC-* namespace" ;;
  esac
  [ -n "$purpose" ] || fail_check HARNESS-ORDER-PAYMENT-SECURITY-SPEC "$id missing purpose"
  [ -n "$future_command" ] || fail_check HARNESS-ORDER-PAYMENT-SECURITY-SPEC "$id missing future live command"
  [ -n "$expected_result" ] || fail_check HARNESS-ORDER-PAYMENT-SECURITY-SPEC "$id missing expected result"
  [ -n "$remediation" ] || fail_check HARNESS-ORDER-PAYMENT-SECURITY-SPEC "$id missing remediation"
  reason="$(unavailable_reason "$id")"
  pending "$id" "$reason; purpose: $purpose; expected later: $expected_result; remediation: $remediation; future live command, not executed by draft: $future_command"
done < "$cases_file"

pass HARNESS-ORDER-PAYMENT-SECURITY-DRAFT "draft completed read-only; all order/payment SEC checks remain explicit PENDING/non-live"
