#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
. "$root/scripts/lib/common.sh"
cd "$root"

require_cmd docker "Start Docker Desktop or Colima."
require_cmd node "Install Node 26.3.0."

warn_low_disk 3

GATEWAY_CLIENT_SECRET="${GATEWAY_CLIENT_SECRET:-LOCAL_DEV_GATEWAY_CLIENT_SECRET__CHANGE_BEFORE_DEPLOY}"
TOKEN_URL="http://keycloak:8080/realms/oidc-service-reference/protocol/openid-connect/token"
PAYMENT_URL="http://payment-service:8085/internal/payments/authorize"
ORDER_PAYMENT_HARNESS_RUN_ID="${ORDER_PAYMENT_HARNESS_RUN_ID:-order-payment-$(date +%Y%m%d%H%M%S)}"

requested="${*:-all}"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }

should_run() {
  case " $requested " in
    *" all "*) return 0 ;;
    *" $1 "*) return 0 ;;
    *) return 1 ;;
  esac
}

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
      fail_check HARNESS-ORDER-PAYMENT-SECURITY-LIVE "$service unavailable or not healthy"
    fi
    sleep 2
  done
}

info "order/payment live security harness"
info "evidence is bounded: stable IDs, status codes, and pass/fail only; no tokens, cookies, or secrets are printed"

if [ "${ORDER_PAYMENT_SECURITY_SKIP_UP:-0}" = "1" ]; then
  info "using already-running stack (ORDER_PAYMENT_SECURITY_SKIP_UP=1)"
else
  sh scripts/up.sh
fi

wait_service_healthy order-service
wait_service_healthy payment-service
wait_service_healthy auth-service
# Seed the SpiceDB schema + relationships (cart/order owner + store manager) so checkout's
# gate-4 cart-ownership check passes. SpiceDB uses the memory datastore (empty on a fresh stack).
sh scripts/verify-cart-spicedb-live.sh
docker compose restart apisix >/dev/null
wait_responding "APISIX" "http://127.0.0.1:9080/auth/me" 120

# SEC-PAYMENT-NO-BROWSER-ROUTE: the internal payment authorization API is not exposed through
# the browser gateway, so a request to it matches no route and fails closed.
if should_run SEC-PAYMENT-NO-BROWSER-ROUTE; then
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    http://127.0.0.1:9080/internal/payments/authorize \
    -H 'Content-Type: application/json' -d '{}' || true)"
  case "$code" in
    404|403) pass SEC-PAYMENT-NO-BROWSER-ROUTE "gateway has no internal payment route ($code)" ;;
    *) fail_check SEC-PAYMENT-NO-BROWSER-ROUTE "expected 404/403 from gateway, got $code" ;;
  esac
fi

# SEC-PAYMENT-WRONG-CLIENT: a client-credentials token from a non-order client (the gateway
# client) is rejected by Payment Service. Run inside the internal network because the payment
# API has no host/gateway exposure.
if should_run SEC-PAYMENT-WRONG-CLIENT; then
  code="$(docker compose exec -T \
    -e TOKEN_URL="$TOKEN_URL" -e PAYMENT_URL="$PAYMENT_URL" -e GW_SECRET="$GATEWAY_CLIENT_SECRET" \
    order-service sh -c '
      tok=$(curl -s -X POST "$TOKEN_URL" \
        -d grant_type=client_credentials \
        -d client_id=commerce-api-gateway \
        -d client_secret="$GW_SECRET" \
        | sed -n "s/.*\"access_token\":\"\([^\"]*\)\".*/\1/p")
      curl -s -o /dev/null -w "%{http_code}" -X POST "$PAYMENT_URL" \
        -H "Authorization: Bearer $tok" \
        -H "Content-Type: application/json" \
        -d "{\"orderId\":\"probe\",\"userSub\":\"alice\",\"amount\":1.00,\"currency\":\"USD\"}"
    ' 2>/dev/null || true)"
  code="$(printf '%s' "$code" | tr -dc '0-9')"
  [ "$code" = "401" ] || fail_check SEC-PAYMENT-WRONG-CLIENT "expected 401 for non-order client, got '$code'"
  pass SEC-PAYMENT-WRONG-CLIENT "payment rejects a non-order-service caller ($code)"
fi

# SEC-PAYMENT-REJECTS-USER-TOKEN: a commerce-api (user) token is rejected by Payment Service.
# Direct Access Grants are disabled on every realm client, so a user token cannot be minted
# out-of-band for a live curl; the ServiceJwtValidator contract test is the authority — it
# rejects any token whose aud is not payment-service (e.g. a commerce-api user token).
if should_run SEC-PAYMENT-REJECTS-USER-TOKEN; then
  sh scripts/verify-payment-service.sh >/dev/null
  pass SEC-PAYMENT-REJECTS-USER-TOKEN "ServiceJwtValidator rejects non-payment-audience tokens (contract test)"
fi

# SEC-CHECKOUT-IDEMPOTENT-REPLAY / SEC-CHECKOUT-IDEMPOTENCY-COLLISION: real browser session
# through the gateway proves the order-side idempotency ladder around the S2S payment call.
if should_run SEC-CHECKOUT-IDEMPOTENT-REPLAY || should_run SEC-CHECKOUT-IDEMPOTENCY-COLLISION; then
  (
    cd "$root/frontend"
    export E2E_FULL_STACK=1
    export ORDER_PAYMENT_HARNESS_RUN_ID
    run_pnpm exec playwright test tests/e2e/order-live.spec.ts --grep "SEC-CHECKOUT"
  )
  pass SEC-CHECKOUT-IDEMPOTENT-REPLAY "live checkout replay verifier passed"
  pass SEC-CHECKOUT-IDEMPOTENCY-COLLISION "live checkout collision verifier passed"
fi

success "order/payment live security harness completed"
