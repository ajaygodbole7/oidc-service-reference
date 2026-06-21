#!/usr/bin/env sh
#
# Full local LIVE acceptance — the live counterpart to scripts/verify-all.sh (static).
#
# Brings the full Compose stack up ONCE, then runs every live SEC gate against it with
# each harness's SKIP_UP flag, so the stack is built/booted a single time instead of once
# per harness. Each gate still seeds SpiceDB and restarts APISIX as it needs.
#
# Heavy: scripts/up.sh rebuilds images and runs the whole stack (now including Postgres).
# The host commonly runs near-full — free disk to ~7 GiB before invoking. Run static gates
# (scripts/verify-all.sh) and this live battery together for a complete acceptance.
#
# Reuse an already-running stack with LIVE_ALL_SKIP_UP=1.
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

require_cmd docker "Start Docker Desktop or Colima."
require_cmd node "Install Node 26.3.0."

warn_low_disk 7

info "full local live acceptance — one stack, all live SEC gates"
info "evidence is bounded: stable check IDs and pass/fail only; no tokens, cookies, or secrets are printed"

if [ "${LIVE_ALL_SKIP_UP:-0}" = "1" ]; then
  info "using already-running stack (LIVE_ALL_SKIP_UP=1)"
else
  sh scripts/up.sh
fi

# Each harness with SKIP_UP=1 reuses this stack and handles its own SpiceDB seed + APISIX
# restart. Run sequentially so they never contend on the shared stack.
CART_SECURITY_SKIP_UP=1 sh tests/security/verify-cart-security-live.sh
CATALOG_SECURITY_SKIP_UP=1 sh tests/security/verify-catalog-security-live.sh
ORDER_PAYMENT_SECURITY_SKIP_UP=1 sh tests/security/verify-order-payment-security-live.sh

success "full local live acceptance completed — cart, catalog, and order/payment live SEC gates green"
