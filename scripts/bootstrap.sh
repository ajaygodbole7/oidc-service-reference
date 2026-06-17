#!/usr/bin/env sh
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$SCRIPT_DIR/lib/common.sh"
ROOT="$(repo_root "$SCRIPT_DIR")"
cd "$ROOT"

# First-time setup: verify the developer toolchain (with install hints) and
# install frontend dependencies. Installs nothing globally — it reports and
# exits non-zero if a HARD prerequisite is missing.

missing=0
check() {
  _cmd="$1"; _hint="$2"; _kind="${3:-hard}"
  if command -v "$_cmd" >/dev/null 2>&1; then
    success "$_cmd ($(command -v "$_cmd"))"
  elif [ "$_kind" = "soft" ]; then
    warn "$_cmd not found (optional) — $_hint"
  else
    warn "$_cmd not found — $_hint"
    missing=1
  fi
}

info "checking developer toolchain"
check docker     "Docker Desktop or Colima (the compose stack)"
check node       "Node 26.3.0 via nvm/asdf"
check pnpm       "pnpm 11.7.0 via corepack"
check curl       "preinstalled on macOS/Linux"
check rg         "brew install ripgrep (interactive search only; POSIX gates must not require it)" soft
check shellcheck "brew install shellcheck (shell lint)" soft

for w in auth-service/mvnw; do
  [ -x "$ROOT/$w" ] && success "$w" || { warn "missing or non-executable $w"; missing=1; }
done

[ "$missing" -eq 0 ] || die "missing hard prerequisites above — install them and re-run 'sh scripts/bootstrap.sh'"

info "installing frontend dependencies"
( cd "$ROOT/frontend" && pnpm install )

success "bootstrap complete — try scripts/up.sh, then cd frontend && pnpm run dev"
