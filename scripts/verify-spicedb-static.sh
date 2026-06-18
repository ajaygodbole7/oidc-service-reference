#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }

info "SpiceDB static substrate verifier"
info "evidence is bounded: file names and stable check IDs; no tokens or secrets are printed"

[ -f authorization-service/schema.zed ] \
  || fail_check HARNESS-SPICEDB-SCHEMA "authorization-service/schema.zed missing"
[ -f authorization-service/seed.relationships ] \
  || fail_check HARNESS-SPICEDB-SEED "authorization-service/seed.relationships missing"
[ -f authorization-service/AGENTS.md ] \
  || fail_check HARNESS-SPICEDB-AGENTS "authorization-service/AGENTS.md missing"
[ -f authorization-service/CLAUDE.md ] \
  || fail_check HARNESS-SPICEDB-AGENTS "authorization-service/CLAUDE.md missing"

grep -q 'ghcr.io/authzed/spicedb:v1.53.0' compose.yaml \
  || fail_check HARNESS-PIN-SPICEDB "SpiceDB image must be pinned to ghcr.io/authzed/spicedb:v1.53.0"
grep -q -- '--datastore-engine=memory' compose.yaml \
  || fail_check HARNESS-SPICEDB-LOCAL-DATASTORE "local SpiceDB must use an explicit memory datastore until persistence slice"
grep -q 'definition user' authorization-service/schema.zed \
  || fail_check HARNESS-SPICEDB-SCHEMA "schema must define user"
grep -q 'definition store' authorization-service/schema.zed \
  || fail_check HARNESS-SPICEDB-SCHEMA "schema must define store"
grep -q 'definition cart' authorization-service/schema.zed \
  || fail_check HARNESS-SPICEDB-SCHEMA "schema must define cart"
grep -q 'definition order' authorization-service/schema.zed \
  || fail_check HARNESS-SPICEDB-SCHEMA "schema must define order"
grep -q 'permission manage = manager' authorization-service/schema.zed \
  || fail_check HARNESS-SPICEDB-MANAGE-PERMISSION "store#manage must derive from store#manager"
grep -q '^store:main#manager@user:merchant$' authorization-service/seed.relationships \
  || fail_check HARNESS-SPICEDB-SEED "merchant manager relationship missing"
grep -q '^cart:alice-cart#owner@user:alice$' authorization-service/seed.relationships \
  || fail_check HARNESS-SPICEDB-SEED "Alice cart owner relationship missing"
grep -q '^cart:bob-cart#owner@user:bob$' authorization-service/seed.relationships \
  || fail_check HARNESS-SPICEDB-SEED "Bob cart owner relationship missing"

pass HARNESS-SPICEDB-STATIC "SpiceDB image pin, schema, seed, and module agent files are present"
