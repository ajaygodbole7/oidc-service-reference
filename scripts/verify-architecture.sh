#!/usr/bin/env sh
#
# Architecture boundary gate — source-import layering invariants for the backend services.
#
# Intentionally source-based (grep over imports), not bytecode-based (ArchUnit/ASM): this
# repo builds on a JDK whose class-file version outpaces ASM releases, and the strict Maven
# enforcer (dependencyConvergence) fights ArchUnit's transitive deps. A source-import gate
# is Java-version-proof, dependency-free, and catches the import-level layering violations
# that matter here.
#
# Invariants (the four-gate ladder's structural contract):
#  - domain is pure: no framework or other-layer imports (depends inward only)
#  - web (controllers) never import persistence: they go through the application service
#  - the gate authorizers live exactly at the service boundary: ScopeAuthorizer and
#    ResourceAuthorizer are used only in service (invocation) and config (wiring), never in
#    web or domain; and the user-facing services actually invoke both gates
#
# payment-service is server-to-server (ServiceJwtValidator, no SpiceDB user gate), so it is
# excluded from the positive gate-presence check.
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }

# Fail if any source under $dir has an import matching $pattern.
assert_no_import() {
  dir="$1"; pattern="$2"; id="$3"; purpose="$4"
  [ -d "$dir" ] || return 0
  if grep -REn "$pattern" "$dir" >/tmp/oidc-service-reference-arch.out 2>/dev/null; then
    cat /tmp/oidc-service-reference-arch.out >&2
    fail_check "$id" "$purpose"
  fi
}

# Fail unless some source under $dir has an import matching $pattern.
assert_import_present() {
  dir="$1"; pattern="$2"; id="$3"; purpose="$4"
  [ -d "$dir" ] || fail_check "$id" "missing $dir; $purpose"
  grep -REq "$pattern" "$dir" 2>/dev/null || fail_check "$id" "$purpose"
}

info "architecture boundary gate (source-import layering invariants)"
info "evidence is bounded: stable check IDs and pass/fail only; no source bodies are printed unless a rule fails"

for svc in cart catalog order payment; do
  base="$svc-service/src/main/java/com/example/commerce/$svc"

  assert_no_import "$base/domain" \
    "^import (org\.springframework|jakarta\.|com\.example\.commerce\.$svc\.(persistence|web|service|config|integration|testfixture))" \
    "ARCH-DOMAIN-PURE-$svc" "domain must depend inward only (no framework or other-layer imports): $svc"

  assert_no_import "$base/web" \
    "^import com\.example\.commerce\.$svc\.persistence" \
    "ARCH-WEB-NO-PERSISTENCE-$svc" "web must not import persistence; route through the application service: $svc"

  assert_no_import "$base/web" \
    "^import com\.example\.commerce\.security\.(ScopeAuthorizer|ResourceAuthorizer)" \
    "ARCH-GATES-AT-SERVICE-$svc" "gate authorizers must not be invoked from web: $svc"

  assert_no_import "$base/domain" \
    "^import com\.example\.commerce\.security\.(ScopeAuthorizer|ResourceAuthorizer)" \
    "ARCH-GATES-AT-SERVICE-$svc" "gate authorizers must not be invoked from domain: $svc"

  pass "ARCH-LAYERING-$svc" "$svc: domain pure, web free of persistence, gates only at the service boundary"
done

for svc in cart catalog order; do
  base="$svc-service/src/main/java/com/example/commerce/$svc"
  assert_import_present "$base/service" \
    "^import com\.example\.commerce\.security\.ScopeAuthorizer" \
    "ARCH-GATE-SCOPE-PRESENT-$svc" "service layer must invoke the scope gate: $svc"
  assert_import_present "$base/service" \
    "^import com\.example\.commerce\.security\.ResourceAuthorizer" \
    "ARCH-GATE-RESOURCE-PRESENT-$svc" "service layer must invoke the SpiceDB resource gate: $svc"
  pass "ARCH-GATES-PRESENT-$svc" "$svc service invokes both the scope and resource gates"
done

success "architecture boundary gate passed"
