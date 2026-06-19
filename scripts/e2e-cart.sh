#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

require_cmd docker "Start Docker Desktop or Colima."

info "SEC-CART live e2e"
info "evidence is bounded: check IDs and pass/fail only; no tokens, cookies, or secrets are printed"

if [ "${E2E_CART_SKIP_UP:-0}" = "1" ]; then
  info "using already-running stack (E2E_CART_SKIP_UP=1)"
else
  sh scripts/up.sh
fi
sh scripts/verify-cart-spicedb-live.sh

(
  cd "$root/frontend"
  E2E_FULL_STACK=1 run_pnpm exec playwright test tests/e2e/cart-live.spec.ts
)

success "SEC-CART live e2e passed"
