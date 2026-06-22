#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

pass() { printf 'PASS %s - %s\n' "$1" "$2"; }
pending() { printf 'PENDING %s - %s\n' "$1" "$2"; }
fail_check() { printf 'FAIL %s - %s\n' "$1" "$2" >&2; exit 1; }

check_file() {
  id="$1"; path="$2"; purpose="$3"
  [ -e "$path" ] || fail_check "$id" "missing $path; $purpose"
  pass "$id" "$purpose"
}

check_absent_in() {
  id="$1"; pattern="$2"; purpose="$3"; shift 3
  if grep -Hn "$pattern" "$@" >/tmp/oidc-service-reference-verify.out 2>/dev/null; then
    cat /tmp/oidc-service-reference-verify.out >&2
    fail_check "$id" "$purpose"
  fi
  pass "$id" "$purpose"
}

info "oidc-service-reference local harness verifier"
info "evidence is bounded: file names, service health, stable check IDs, and explicit PENDING states; no tokens or secrets are printed"

check_file HARNESS-SCAFFOLD-AUTH auth-service/pom.xml "Auth Service scaffold exists"
check_file HARNESS-SCAFFOLD-GATEWAY api-gateway/apisix.yaml.template "APISIX gateway scaffold exists"
check_file HARNESS-SCAFFOLD-IDP authorization-server/realm/oidc-service-reference-realm.json "Keycloak realm scaffold exists"
check_file HARNESS-SCAFFOLD-FRONTEND frontend/package.json "frontend shell scaffold exists"
check_file HARNESS-COMMERCE-SECURITY-COMMON-PRESENT commerce-security-common/pom.xml \
  "commerce-security-common scaffold exists"
check_file HARNESS-CART-SERVICE-PRESENT cart-service/pom.xml "cart-service scaffold exists"
check_file HARNESS-SPICEDB-SCHEMA authorization-service/schema.zed "SpiceDB schema exists"
check_file HARNESS-SPICEDB-SEED authorization-service/seed.relationships "SpiceDB seed relationships exist"

grep -q 'quay.io/keycloak/keycloak:26.6.3' compose.yaml \
  || fail_check HARNESS-PIN-KEYCLOAK "Keycloak image must be pinned to 26.6.3"
grep -q 'apache/apisix:3.16.0-debian' compose.yaml \
  || fail_check HARNESS-PIN-APISIX "APISIX image must be pinned to 3.16.0-debian"
grep -q 'valkey/valkey:9.1.0' compose.yaml \
  || fail_check HARNESS-PIN-VALKEY "Valkey image must be pinned to 9.1.0"
grep -q 'postgres:18.4' compose.yaml \
  || fail_check HARNESS-PIN-POSTGRES "Postgres image must be pinned to 18.4"
grep -q '"packageManager": "pnpm@11.7.0"' frontend/package.json \
  || fail_check HARNESS-PIN-PNPM "frontend package manager must be pinned to pnpm 11.7.0"
grep -q '<artifactId>authzed</artifactId>' commerce-security-common/pom.xml \
  || fail_check HARNESS-PIN-AUTHZED-JAVA "Authzed Java SDK dependency must be present"
grep -q '<version>1.6.0</version>' commerce-security-common/pom.xml \
  || fail_check HARNESS-PIN-AUTHZED-JAVA "Authzed Java SDK must be pinned to 1.6.0"
grep -q '<version>1.72.0</version>' commerce-security-common/pom.xml \
  || fail_check HARNESS-PIN-GRPC-JAVA "gRPC Java must be pinned to 1.72.0"
grep -q '<artifactId>guava</artifactId>' commerce-security-common/pom.xml \
  || fail_check HARNESS-PIN-GUAVA "Guava dependency management override must be present"
grep -q '<version>33.5.0-jre</version>' commerce-security-common/pom.xml \
  || fail_check HARNESS-PIN-GUAVA "Guava must be pinned to 33.5.0-jre"
grep -q '<artifactId>error_prone_annotations</artifactId>' commerce-security-common/pom.xml \
  || fail_check HARNESS-PIN-ERROR-PRONE-ANNOTATIONS "Error Prone annotations override must be present"
grep -q '<version>2.42.0</version>' commerce-security-common/pom.xml \
  || fail_check HARNESS-PIN-ERROR-PRONE-ANNOTATIONS "Error Prone annotations must be pinned to 2.42.0"
pass HARNESS-PINNED-VERSIONS "initial manifests use exact scaffold pins"

check_absent_in HARNESS-NO-RESOURCE-SERVER 'backend-resource-server\|resource-server:8082' \
  "copied backend-resource-server demo routes must not be active in this repo" \
  compose.yaml api-gateway/apisix.yaml.template scripts/up.sh
check_absent_in HARNESS-NO-FLOATING-DOCKER ':latest' \
  "Docker manifests must not use floating latest tags" \
  compose.yaml auth-service/Dockerfile
check_absent_in HARNESS-NO-NPM-RANGES '"[~^*]' \
  "frontend package versions must be exact, not ranges" \
  frontend/package.json

sh scripts/verify-architecture.sh
sh scripts/verify-commerce-security-common.sh
sh scripts/verify-commerce-web-starter.sh
sh scripts/verify-spicedb-static.sh
SMOKE_SKIP_DISCOVERY=1 sh authorization-server/tests/smoke.sh
sh scripts/verify-cart-service.sh
sh scripts/verify-catalog-service.sh
sh scripts/verify-order-service.sh
sh scripts/verify-payment-service.sh
sh tests/security/verify-cart-security-draft.sh
sh tests/security/verify-catalog-security-draft.sh
sh tests/security/verify-order-payment-security-draft.sh

if command -v docker >/dev/null 2>&1; then
  if docker compose ps >/tmp/oidc-service-reference-compose-ps.out 2>/dev/null; then
    info "service health summary from docker compose ps"
    sed -n '1,18p' /tmp/oidc-service-reference-compose-ps.out
    for service in keycloak valkey spicedb postgres auth-service cart-service catalog-service payment-service order-service apisix; do
      grep -E "oidc-service-reference-${service}-1[[:space:]].*healthy" \
        /tmp/oidc-service-reference-compose-ps.out >/dev/null 2>&1 \
        || { pending HARNESS-SERVICE-HEALTH "$service unavailable or not healthy"; service_health_pending=1; }
    done
    if [ "${service_health_pending:-0}" = "0" ]; then
      pass HARNESS-SERVICE-HEALTH "Keycloak, Valkey, SpiceDB, Postgres, Auth Service, domain services, and APISIX are healthy"
      missing_db=""
      for db in catalog_db cart_db order_db payment_db; do
        if ! docker compose exec -T postgres psql \
            -U "${POSTGRES_USER:-commerce}" -d commerce -tAc "SELECT 1 FROM pg_database WHERE datname = '$db'" \
            | grep -q 1; then
          missing_db="$missing_db $db"
        fi
      done
      if [ -n "$missing_db" ]; then
        fail_check HARNESS-POSTGRES-INIT "missing database(s):$missing_db; recreate the local postgres-data volume or run a migration-safe init repair"
      fi
      unmigrated_db=""
      for db in catalog_db cart_db order_db payment_db; do
        if ! docker compose exec -T postgres psql \
            -U "${POSTGRES_USER:-commerce}" -d "$db" -tAc "SELECT 1 FROM flyway_schema_history WHERE success LIMIT 1" 2>/dev/null \
            | grep -q 1; then
          unmigrated_db="$unmigrated_db $db"
        fi
      done
      if [ -n "$unmigrated_db" ]; then
        fail_check HARNESS-POSTGRES-INIT "database(s) exist but have no applied Flyway migration:$unmigrated_db"
      fi
      pass HARNESS-POSTGRES-INIT "catalog_db, cart_db, order_db, and payment_db exist and are Flyway-migrated"
    fi
  else
    pending HARNESS-SERVICE-HEALTH "compose stack is not running; run scripts/up.sh when ready"
  fi
else
  pending HARNESS-SERVICE-HEALTH "docker not available on PATH"
fi

pending SEC-NO-BROWSER-TOKENS "requires live auth flow through APISIX and frontend"
pending SEC-NON-COMMERCE-AUD "run sh tests/security/verify-cart-security-live.sh SEC-NON-COMMERCE-AUD for the local auth-service audience fixture"
pending SEC-CATALOG-ANONYMOUS-READ-ONLY "run sh tests/security/verify-catalog-security-live.sh SEC-CATALOG-ANONYMOUS-READ-ONLY after catalog-service exists; anonymous GET list/detail must pass, anonymous POST/PATCH must deny, merchant writes must require catalog:write plus store:main#manage"
pending SEC-SCOPE-WITHOUT-RELATIONSHIP "run sh tests/security/verify-cart-security-live.sh SEC-SCOPE-WITHOUT-RELATIONSHIP for the live cross-user cart request"
pending SEC-RELATIONSHIP-WITHOUT-SCOPE "run sh tests/security/verify-cart-security-live.sh SEC-RELATIONSHIP-WITHOUT-SCOPE for the local missing-scope fixture"
pending SEC-OWNERSHIP-PROVISIONED-FOR-CALLER "run sh tests/security/verify-cart-security-live.sh SEC-OWNERSHIP-PROVISIONED-FOR-CALLER for dynamic cart ownership provisioning"
pending SEC-NO-RESOURCE-HIJACK "run sh tests/security/verify-cart-security-live.sh SEC-NO-RESOURCE-HIJACK for server-generated cart id proof"
pending SEC-PROVISIONING-FAILS-CLOSED "run sh tests/security/verify-cart-security-live.sh SEC-PROVISIONING-FAILS-CLOSED for relationship-write failure proof"
pending SEC-SPOOFED-IDENTITY-HEADERS "run sh tests/security/verify-cart-security-live.sh SEC-SPOOFED-IDENTITY-HEADERS for the APISIX stripped-header proof"
pending SEC-BROWSER-AUTHORIZATION-OVERWRITTEN "run sh tests/security/verify-cart-security-live.sh SEC-BROWSER-AUTHORIZATION-OVERWRITTEN for the gateway bearer overwrite proof"
pending SEC-SECURITY-TRACE-EVIDENCE "run sh tests/security/verify-cart-security-live.sh SEC-SECURITY-TRACE-EVIDENCE for bounded harness-only four-gate trace evidence"
pending SEC-PAYMENT-NO-BROWSER-ROUTE "run sh tests/security/verify-order-payment-security-live.sh SEC-PAYMENT-NO-BROWSER-ROUTE for the browser gateway proof"
pending SEC-PAYMENT-WRONG-CLIENT "run sh tests/security/verify-order-payment-security-live.sh SEC-PAYMENT-WRONG-CLIENT for the payment S2S caller proof"
pending SEC-PAYMENT-REJECTS-USER-TOKEN "run sh tests/security/verify-order-payment-security-live.sh SEC-PAYMENT-REJECTS-USER-TOKEN for the payment audience proof"
pending SEC-CHECKOUT-IDEMPOTENT-REPLAY "run sh tests/security/verify-order-payment-security-live.sh SEC-CHECKOUT-IDEMPOTENT-REPLAY for checkout replay"
pending SEC-CHECKOUT-IDEMPOTENCY-COLLISION "run sh tests/security/verify-order-payment-security-live.sh SEC-CHECKOUT-IDEMPOTENCY-COLLISION for checkout collision"
pending SEC-SPICEDB-UNAVAILABLE "run sh tests/security/verify-cart-security-live.sh SEC-SPICEDB-UNAVAILABLE for the local SpiceDB outage proof"
pending SEC-RELATIONSHIP-REMOVAL-IMMEDIATE "run sh tests/security/verify-cart-security-live.sh SEC-RELATIONSHIP-REMOVAL-IMMEDIATE for the local relationship removal fixture"
pending HARNESS-CART-SPICEDB-LIVE "run CART_SPICEDB_LIVE=1 sh scripts/verify-cart-service.sh or sh scripts/verify-cart-spicedb-live.sh for the real cart SpiceDB argument proof"
pending HARNESS-SPICEDB-LIVE "run scripts/verify-spicedb-live.sh for schema load, seed apply, and real adapter contract"

success "local harness verifier completed with explicit pending checks"
