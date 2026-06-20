#!/usr/bin/env sh
#
# Authorization-server smoke test.
#
# Environment flags:
#   OIDC_ISSUER              Issuer URL. Default http://localhost:8080/realms/oidc-service-reference.
#   REALM_FILE               Realm JSON to statically verify. Default realm/oidc-service-reference-realm.json.
#   EXPECTED_REALM           Expected realm name. Default oidc-service-reference.
#   EXPECTED_API_AUDIENCE    Expected Resource Server audience. Default commerce-api.
#   EXPECTED_PAYMENT_AUDIENCE Expected Payment Service audience. Default payment-service.
#   EXPECTED_ROLES_CLAIM     Expected roles claim emitted by the roles mapper. Default realm_access.roles.
#   SERVICE_CLIENT_SECRET    Secret for order-service. Default dev placeholder.
#   API_GATEWAY_CLIENT_SECRET Secret for commerce-api-gateway. Default dev placeholder.
#   SMOKE_SKIP_DISCOVERY=1   Skip live discovery/JWKS/token checks.
#   SMOKE_SKIP_TOKEN=1       Skip live token issuance checks only.
#   SMOKE_API_GATEWAY_CHECK=1 Enable optional Client Credentials live check for
#                             commerce-api-gateway. Asserts the issued
#                             access token carries aud=commerce-auth-internal.
#                             Off by default so a missing secret does not break
#                             the smoke in environments that haven't provisioned it.
#
set -eu

issuer="${OIDC_ISSUER:-http://localhost:8080/realms/oidc-service-reference}"
script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_dir="$(CDPATH= cd -- "$script_dir/.." && pwd)"
realm_file="${REALM_FILE:-$project_dir/realm/oidc-service-reference-realm.json}"
expected_realm="${EXPECTED_REALM:-oidc-service-reference}"
expected_api_audience="${EXPECTED_API_AUDIENCE:-commerce-api}"
expected_payment_audience="${EXPECTED_PAYMENT_AUDIENCE:-payment-service}"
expected_roles_claim="${EXPECTED_ROLES_CLAIM:-realm_access.roles}"
service_secret="${SERVICE_CLIENT_SECRET:-LOCAL_DEV_SERVICE_CLIENT_SECRET__CHANGE_BEFORE_DEPLOY}"
api_gateway_secret="${API_GATEWAY_CLIENT_SECRET:-LOCAL_DEV_GATEWAY_CLIENT_SECRET__CHANGE_BEFORE_DEPLOY}"

fail() {
  echo "authorization-server smoke failed: $1" >&2
  exit 1
}

[ -f "$realm_file" ] || fail "missing $realm_file"

node - "$realm_file" "$expected_realm" "$expected_api_audience" "$expected_payment_audience" "$expected_roles_claim" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
const expectedRealm = process.argv[3];
const expectedApiAudience = process.argv[4];
const expectedPaymentAudience = process.argv[5];
const expectedRolesClaim = process.argv[6];
const realm = JSON.parse(fs.readFileSync(path, "utf8"));
const clients = new Map(realm.clients.map((c) => [c.clientId, c]));
const scopes = new Map((realm.clientScopes || []).map((s) => [s.name, s]));
const users = new Map((realm.users || []).map((u) => [u.username, u]));
const realmRoles = new Set(((realm.roles || {}).realm || []).map((r) => r.name));
const auth = clients.get("commerce-auth");
const apiGateway = clients.get("commerce-api-gateway");
const service = clients.get("order-service");
const audScope = scopes.get("api.audience");
const paymentAudScope = scopes.get("payment.audience");
const authInternalScope = scopes.get("auth.internal");
const rolesScope = scopes.get("roles");

function assert(cond, msg) {
  if (!cond) { console.error(msg); process.exit(1); }
}

assert(realm.realm === expectedRealm, `realm name mismatch: ${realm.realm}`);
assert(realm.accessTokenLifespan === 120, "access token lifespan must be 120 seconds");
assert(realm.revokeRefreshToken === true, "refresh token rotation must be enabled");
assert(realm.refreshTokenMaxReuse === 0, "refresh token reuse must be zero");

for (const role of ["user", "admin", "auditor"]) {
  assert(realmRoles.has(role), `missing realm role ${role}`);
}

const alice = users.get("alice");
const admin = users.get("admin");
assert(alice && alice.enabled === true, "missing enabled alice user");
assert(admin && admin.enabled === true, "missing enabled admin user");
assert((alice.realmRoles || []).includes("user"), "alice must have user role");
assert((admin.realmRoles || []).includes("user"), "admin must have user role");
assert((admin.realmRoles || []).includes("admin"), "admin must have admin role");
assert(((alice.attributes || {}).commerce_sub || []).includes("alice"),
       "alice must have stable commerce_sub for local SpiceDB relationships");
assert(((admin.attributes || {}).commerce_sub || []).includes("admin"),
       "admin must have stable commerce_sub for local SpiceDB relationships");

assert(auth, "missing Auth Service client (commerce-auth)");
assert(auth.publicClient === false, "Auth Service client must be confidential");
assert(auth.standardFlowEnabled === true, "Auth Service standard flow must be enabled");
assert(auth.implicitFlowEnabled === false, "Auth Service implicit flow must be disabled");
assert(auth.directAccessGrantsEnabled === false, "Auth Service direct grants must be disabled");
assert(auth.serviceAccountsEnabled === false, "Auth Service service accounts must be disabled");
assert(auth.attributes["pkce.code.challenge.method"] === "S256", "Auth Service PKCE must require S256");
assert(auth.attributes["backchannel.logout.url"] === "http://auth-service:8081/backchannel-logout",
       "Auth Service client must register internal back-channel logout URL");
assert(auth.attributes["backchannel.logout.session.required"] === "true",
       "Auth Service back-channel logout must require session sid");
assert(auth.redirectUris.includes("http://127.0.0.1:5173/auth/callback/idp"),
       "Auth Service redirect URI must point to SPA origin (Vite proxies to Auth Service); registration name is the generic 'idp'");
assert(auth.redirectUris.includes("http://127.0.0.1:9080/auth/callback/idp"),
       "Auth Service redirect URI must include APISIX origin for gateway-level login/refresh harnesses");
assert(Array.isArray(auth.webOrigins) && auth.webOrigins.length === 0,
       "Auth Service webOrigins must be empty (browser never calls Keycloak from JS)");
for (const scope of ["openid", "profile", "email", "roles", "api.read"]) {
  assert(auth.defaultClientScopes.includes(scope), `Auth Service default scopes must include ${scope}`);
}
for (const scope of ["cart:read", "cart:write"]) {
  assert(scopes.has(scope), `missing ${scope} client scope`);
  assert(scopes.get(scope).attributes["include.in.token.scope"] === "true",
         `${scope} must be emitted in the access-token scope claim`);
}
for (const scope of ["api.audience", "cart:read", "cart:write", "api.write", "admin.read"]) {
  assert((auth.optionalClientScopes || []).includes(scope), `Auth Service optional scopes must include ${scope}`);
}

assert(apiGateway, "missing API Gateway client (commerce-api-gateway)");
assert(apiGateway.publicClient === false, "API Gateway client must be confidential");
assert(apiGateway.serviceAccountsEnabled === true, "API Gateway service accounts (Client Credentials) must be enabled");
assert(apiGateway.standardFlowEnabled === false, "API Gateway standard flow must be disabled");
assert(apiGateway.implicitFlowEnabled === false, "API Gateway implicit flow must be disabled");
assert(apiGateway.directAccessGrantsEnabled === false, "API Gateway direct access grants must be disabled");
assert((apiGateway.defaultClientScopes || []).includes("auth.internal"),
       "API Gateway default scopes must include auth.internal");

assert(authInternalScope, "missing auth.internal client scope");
const authInternalMapper = (authInternalScope.protocolMappers || []).find(
  (m) => m.protocolMapper === "oidc-audience-mapper"
);
assert(authInternalMapper, "auth.internal scope missing oidc-audience-mapper");
assert(authInternalMapper.config["included.custom.audience"] === "commerce-auth-internal",
       "auth.internal audience mapper must add commerce-auth-internal");
assert(authInternalMapper.config["access.token.claim"] === "true",
       "auth.internal audience mapper must add to access token");
assert(authInternalMapper.config["id.token.claim"] === "false",
       "auth.internal audience mapper must NOT add to id token");

assert(service, "missing service client");
assert(service.publicClient === false, "service client must be confidential");
assert(service.serviceAccountsEnabled === true, "service accounts must be enabled");
assert(service.implicitFlowEnabled === false, "service implicit flow must be disabled");
assert(service.standardFlowEnabled === false, "service standard flow must be disabled");
assert(service.directAccessGrantsEnabled === false, "service direct grants must be disabled");
assert(service.defaultClientScopes.includes("payment.audience"),
       "order-service default scopes must include payment.audience");
assert(service.defaultClientScopes.includes("payments:authorize"),
       "order-service default scopes must include payments:authorize");
assert(!service.defaultClientScopes.includes("api.audience"),
       "order-service client-credentials token must not default to commerce-api audience");
assert(!service.defaultClientScopes.includes("service.jobs"),
       "order-service client-credentials token must not use the old service.jobs scope");

assert(scopes.has("payments:authorize"), "missing payments:authorize client scope");
assert(scopes.get("payments:authorize").attributes["include.in.token.scope"] === "true",
       "payments:authorize must be emitted in the access-token scope claim");

assert(paymentAudScope, "missing payment.audience client scope");
const paymentAudMapper = (paymentAudScope.protocolMappers || []).find(
  (m) => m.protocolMapper === "oidc-audience-mapper"
);
assert(paymentAudMapper, "payment.audience scope missing oidc-audience-mapper");
assert(paymentAudMapper.config["included.custom.audience"] === expectedPaymentAudience,
       `payment audience mapper must add ${expectedPaymentAudience}`);
assert(paymentAudMapper.config["access.token.claim"] === "true",
       "payment audience mapper must add to access token");
assert(paymentAudMapper.config["id.token.claim"] === "false",
       "payment audience mapper must NOT add to id token");

assert(audScope, "missing api.audience client scope");
const audMapper = (audScope.protocolMappers || []).find(
  (m) => m.protocolMapper === "oidc-audience-mapper"
);
assert(audMapper, "api.audience scope missing oidc-audience-mapper");
assert(audMapper.config["included.custom.audience"] === expectedApiAudience,
       `audience mapper must add ${expectedApiAudience}`);
assert(audMapper.config["access.token.claim"] === "true",
       "audience mapper must add to access token");

assert(rolesScope, "missing roles client scope");
const subjectMapper = ((scopes.get("profile") || {}).protocolMappers || []).find(
  (m) => m.config && m.config["claim.name"] === "sub"
);
assert(subjectMapper, "profile scope must map stable commerce subject to sub");
assert(subjectMapper.protocolMapper === "oidc-usermodel-attribute-mapper",
       "sub mapper must read the stable commerce_sub user attribute");
assert(subjectMapper.config["user.attribute"] === "commerce_sub",
       "sub mapper must read commerce_sub");
assert(subjectMapper.config["access.token.claim"] === "true",
       "sub mapper must add sub to the access token");
assert(subjectMapper.config["id.token.claim"] === "true",
       "sub mapper must add sub to the ID token");

const realmRoleMapper = (rolesScope.protocolMappers || []).find(
  (m) => m.protocolMapper === "oidc-usermodel-realm-role-mapper"
);
assert(realmRoleMapper, "roles scope missing oidc-usermodel-realm-role-mapper");
assert(realmRoleMapper.config["claim.name"] === expectedRolesClaim,
       `realm role mapper must emit ${expectedRolesClaim}`);
assert(realmRoleMapper.config["access.token.claim"] === "true",
       "realm role mapper must add to access token");
// The Auth Service reads roles from the ID token (JwtOidcIdTokenValidator +
// app.roles-claim-path) to populate /auth/me. If the mapper omits roles from
// the ID token, /auth/me reports empty roles for every Keycloak user even
// though the access token (and thus RS authorization) has them.
assert(realmRoleMapper.config["id.token.claim"] === "true",
       "realm role mapper must add roles to the ID token (the BFF reads roles from the id_token for /auth/me)");

console.log("realm static checks passed");
NODE

if [ "${SMOKE_SKIP_DISCOVERY:-}" = "1" ]; then
  exit 0
fi

discovery_json="$(mktemp)"
jwks_json="$(mktemp)"
token_json="$(mktemp)"
gateway_token_json="$(mktemp)"
trap 'rm -f "$discovery_json" "$jwks_json" "$token_json" "$gateway_token_json"' EXIT

# Wait for Keycloak. Realm import on cold start typically completes in
# 20-60s; without a wait, `verify-all.sh` races startup.
ready=""
for i in $(seq 1 60); do
  if curl -fsS "$issuer/.well-known/openid-configuration" >"$discovery_json" 2>/dev/null; then
    ready="1"
    break
  fi
  sleep 2
done
[ -n "$ready" ] || fail "discovery did not become ready at $issuer/.well-known/openid-configuration within 120s"

node - "$issuer" "$discovery_json" <<'NODE'
const fs = require("fs");
const issuer = process.argv[2];
const d = JSON.parse(fs.readFileSync(process.argv[3], "utf8"));
if (d.issuer !== issuer) { console.error(`issuer mismatch: ${d.issuer}`); process.exit(1); }
if (!d.jwks_uri) { console.error("missing jwks_uri"); process.exit(1); }
if (!d.token_endpoint) { console.error("missing token_endpoint"); process.exit(1); }
if (!d.end_session_endpoint) { console.error("missing end_session_endpoint"); process.exit(1); }
console.log("discovery checks passed");
NODE

jwks_uri="$(node -e 'const fs = require("fs"); const d = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); process.stdout.write(d.jwks_uri);' "$discovery_json")"
curl -fsS "$jwks_uri" >"$jwks_json" || fail "JWKS endpoint not reachable at $jwks_uri"
node - "$jwks_json" <<'NODE'
const fs = require("fs");
const jwks = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
if (!Array.isArray(jwks.keys) || jwks.keys.length === 0) {
  console.error("JWKS contains no keys");
  process.exit(1);
}
console.log("JWKS checks passed");
NODE

if [ "${SMOKE_SKIP_TOKEN:-}" = "1" ]; then
  exit 0
fi

if ! curl -fsS \
    -d "grant_type=client_credentials" \
    -d "client_id=order-service" \
    --data-urlencode "client_secret=${service_secret}" \
    "$issuer/protocol/openid-connect/token" >"$token_json"; then
  fail "service client_credentials token issuance failed (check SERVICE_CLIENT_SECRET)"
fi

node - "$issuer" "$token_json" "$expected_payment_audience" <<'NODE'
const fs = require("fs");
const issuer = process.argv[2];
const t = JSON.parse(fs.readFileSync(process.argv[3], "utf8"));
const expectedPaymentAudience = process.argv[4];
if (!t.access_token) { console.error("missing access_token"); process.exit(1); }
const parts = t.access_token.split(".");
if (parts.length < 2) { console.error("malformed JWT"); process.exit(1); }
const payload = JSON.parse(Buffer.from(parts[1].replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8"));
if (payload.iss !== issuer) {
  console.error(`token iss mismatch: ${payload.iss}`);
  process.exit(1);
}
const aud = Array.isArray(payload.aud) ? payload.aud : (payload.aud ? [payload.aud] : []);
if (!aud.includes(expectedPaymentAudience)) {
  console.error(`token aud missing ${expectedPaymentAudience}: ${JSON.stringify(aud)}`);
  process.exit(1);
}
if (aud.includes("commerce-api")) {
  console.error(`order-service client-credentials token must not default to commerce-api audience: ${JSON.stringify(aud)}`);
  process.exit(1);
}
const scopes = (payload.scope || "").split(" ");
if (!scopes.includes("payments:authorize")) {
  console.error(`token scope missing payments:authorize: ${payload.scope}`);
  process.exit(1);
}
console.log("real-token claim checks passed (iss, aud=payment-service, scope=payments:authorize)");
NODE

# Optional: live-token check for commerce-api-gateway via Client Credentials.
# Gated by SMOKE_API_GATEWAY_CHECK=1 so environments without a provisioned secret
# do not break the smoke. Asserts the issued token carries
# aud=commerce-auth-internal (the audience the Auth Service /internal/*
# endpoints will accept).
if [ "${SMOKE_API_GATEWAY_CHECK:-}" = "1" ]; then
  if ! curl -fsS \
      -d "grant_type=client_credentials" \
      -d "client_id=commerce-api-gateway" \
      --data-urlencode "client_secret=${api_gateway_secret}" \
      "$issuer/protocol/openid-connect/token" >"$gateway_token_json"; then
    fail "api-gateway client_credentials token issuance failed (check API_GATEWAY_CLIENT_SECRET)"
  fi

  node - "$issuer" "$gateway_token_json" <<'NODE'
const fs = require("fs");
const issuer = process.argv[2];
const t = JSON.parse(fs.readFileSync(process.argv[3], "utf8"));
if (!t.access_token) { console.error("missing access_token (api-gateway)"); process.exit(1); }
const parts = t.access_token.split(".");
if (parts.length < 2) { console.error("malformed JWT (api-gateway)"); process.exit(1); }
const payload = JSON.parse(Buffer.from(parts[1].replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8"));
if (payload.iss !== issuer) {
  console.error(`api-gateway token iss mismatch: ${payload.iss}`);
  process.exit(1);
}
const aud = Array.isArray(payload.aud) ? payload.aud : (payload.aud ? [payload.aud] : []);
if (!aud.includes("commerce-auth-internal")) {
  console.error(`api-gateway token aud missing commerce-auth-internal: ${JSON.stringify(aud)}`);
  process.exit(1);
}
console.log("api-gateway real-token claim checks passed (iss, aud=commerce-auth-internal)");
NODE
fi
