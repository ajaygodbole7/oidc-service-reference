#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

require_cmd docker "Start Docker Desktop or Colima."
require_cmd pnpm "Install pnpm 11.7.0."

warn "live SEC-NO-BROWSER-TOKENS is not implemented in this scaffold yet"
warn "next step: start the stack with scripts/up.sh, then add a focused Playwright flow for login -> /auth/me -> logout"
exit 2
