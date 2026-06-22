# Security

oidc-service-reference is a reference implementation, not a production service. Do not deploy it
as-is. This file describes the local security posture; the model itself is documented under
`docs/`.

## Local secrets and loopback binding

The local stack uses placeholder secrets that end in `CHANGE_BEFORE_DEPLOY` (the gateway client
secret, the SpiceDB preshared key, the Postgres password, the CSRF signing key). They are safe
only because every port binds to loopback and the values are obvious placeholders.

Auth Service runs `SecretSentinelValidator` at startup: it refuses to boot when it sees a
`CHANGE_BEFORE_DEPLOY` sentinel while configured for a non-loopback address. A real deployment
replaces every secret, and this boot check fails closed if a placeholder is left in a non-local
configuration.

Cart Service and Order Service demonstrate a stronger rule for the SpiceDB preshared key: it has
no default at all. `CartProperties` and `OrderProperties` mark the key `@NotBlank` with no fallback
value (config binds it from `CART_SPICEDB_PRESHARED_KEY` and `SPICEDB_PRESHARED_KEY`), so an unset
key fails validation and the context refuses to start, unconditionally, rather than silently
shipping a dev key. There is no loopback or sentinel condition on this check: the key must be
supplied or the service does not boot.

## The security model

- [docs/authorization-model.md](docs/authorization-model.md): the four-gate ladder and the
  SpiceDB schema.
- [docs/token-model.md](docs/token-model.md): the browser token boundary.
- [docs/threat-model.md](docs/threat-model.md): threats and the controls that stop them.
- [docs/security-verification.md](docs/security-verification.md): the runnable security gates.
- [docs/production-hardening.md](docs/production-hardening.md): what a real deployment adds.

## Reporting

This is a reference for study, not a deployed system; there is no production instance to report
against. If you find an error in the security model or the verification, open an issue on the
repository.
