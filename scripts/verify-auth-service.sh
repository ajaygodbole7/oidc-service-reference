#!/usr/bin/env sh
set -eu

# Verify script for the Auth Service module.
#
# Asserts layout, then runs the module's Maven test phase. The BFF is split
# into the Auth Service and the APISIX-based API Gateway.

cd "$(dirname "$0")/../auth-service"

fail() {
  echo "auth-service verification failed: $1" >&2
  exit 1
}

[ -f pom.xml ] || fail "missing pom.xml"
[ -d src/main/java ] || fail "missing src/main/java"
[ -d src/test/java ] || fail "missing src/test/java"

./mvnw -B -q test
