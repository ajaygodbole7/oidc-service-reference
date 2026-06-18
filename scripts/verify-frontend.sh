#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root/frontend"

fail() {
  echo "frontend verification failed: $1" >&2
  exit 1
}

[ -f package.json ] || fail "missing package.json"
[ -f playwright.config.ts ] || fail "missing playwright.config.ts"
[ -d src ] || fail "missing src/"
[ -d tests ] || fail "missing tests/"

run_pnpm run lint
run_pnpm run typecheck
run_pnpm run test
run_pnpm run build
run_pnpm run check:bundle
run_pnpm run test:e2e
