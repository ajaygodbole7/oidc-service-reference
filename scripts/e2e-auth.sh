#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

require_cmd docker "Start Docker Desktop or Colima."

info "SEC-NO-BROWSER-TOKENS live e2e"
info "evidence is bounded: check ID and pass/fail only; no tokens, cookies, or secrets are printed"

sh scripts/up.sh

(
  cd "$root/frontend"
  E2E_FULL_STACK=1 run_pnpm exec playwright test tests/e2e/auth.spec.ts --grep "SEC-NO-BROWSER-TOKENS"
)

success "SEC-NO-BROWSER-TOKENS passed"
