#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

require_cmd docker "Install Docker Desktop or Colima."

warn_low_disk 3

DEV_SPICEDB_KEY="${SPICEDB_PRESHARED_KEY:-LOCAL_DEV_SPICEDB_PRESHARED_KEY__CHANGE_BEFORE_DEPLOY}"

info "starting local SpiceDB"
SPICEDB_PRESHARED_KEY="$DEV_SPICEDB_KEY" docker compose up -d spicedb

info "running live SpiceDB contract"
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
    die "SpiceDB live contract failed after $tries attempts"
  fi
  warn "SpiceDB live contract not ready yet; retrying ($tries/6)"
  sleep 2
done

success "HARNESS-SPICEDB-LIVE passed: schema loaded, seed relationships applied, fake-vs-real contract green, unavailable path denies"
