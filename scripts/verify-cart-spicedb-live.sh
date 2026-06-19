#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

require_cmd docker "Install Docker Desktop or Colima."

warn_low_disk 3

DEV_SPICEDB_KEY="${SPICEDB_PRESHARED_KEY:-LOCAL_DEV_SPICEDB_PRESHARED_KEY__CHANGE_BEFORE_DEPLOY}"

info "cart live SpiceDB gate"
info "starting only SpiceDB; no app image rebuild is required"
SPICEDB_PRESHARED_KEY="$DEV_SPICEDB_KEY" docker compose up -d --no-build spicedb

tries=0
while :; do
  tries=$((tries + 1))
  if SPICEDB_LIVE_TEST=true \
      SPICEDB_TARGET="${SPICEDB_TARGET:-127.0.0.1:50051}" \
      SPICEDB_PRESHARED_KEY="$DEV_SPICEDB_KEY" \
      sh scripts/verify-commerce-security-common.sh; then
    break
  fi
  if [ "$tries" -ge 6 ]; then
    die "cart live SpiceDB gate failed after $tries attempts"
  fi
  warn "SpiceDB live gate not ready yet; retrying ($tries/6)"
  sleep 2
done

success "HARNESS-CART-SPICEDB-LIVE passed: real SpiceDB matches fake authorization and unavailable checks deny"
