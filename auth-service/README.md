# Auth Service

Spring Boot service that owns the OAuth/OIDC client role for the browser
flow. Built on Nimbus `oauth2-oidc-sdk` directly for the browser OIDC
client flow, instead of using the Spring Security OAuth2 Client starter.

This directory owns:

- the Authorization Code Flow with PKCE
- the session cookie
- the custom Redis-compatible `tx:{state}` and `sess:{sid}` repositories
- ID-token validation
- refresh-token rotation with reuse detection
- RP-initiated logout
- OIDC Back-Channel Logout
- the `/internal/resolve` RPC

## Endpoints

- `/auth/login`, `/auth/callback/idp`, `/auth/logout`, `/auth/logout/continue`,
  `/auth/me` — browser-facing, cookie-authenticated.
- `/internal/resolve` — back-channel RPC, served as an OAuth Resource Server.
  Called by the API Gateway as the `commerce-api-gateway` Client Credentials
  client, audience-bound to `commerce-auth-internal`.
- `/backchannel-logout` — OIDC Back-Channel Logout 1.0, IdP-to-service (root
  path, no `/auth` prefix). Form-encoded `logout_token`; validates the token
  and revokes the mapped session(s).

## Trust boundary

The Auth Service is the sole writer of `tx:{state}` and `sess:{sid}`. The
API Gateway reads `sess:{sid}` but never writes it. The two share only the
`sess:{sid}` JSON schema and the `/internal/resolve` contract.
